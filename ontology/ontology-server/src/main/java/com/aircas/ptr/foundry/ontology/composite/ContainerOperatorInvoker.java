package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.config.ContainerOperatorProperties;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceParam;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerOperatorConfig;
import com.aircas.ptr.foundry.ontology.model.enums.ContainerPhaseEnum;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionContainerInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 容器算子执行器：常驻复用模型（设计 B）下，按 {@code containerKey} 定位已运行的容器实例，
 * 依据算子保存的接口定义（docker_config 中的单个接口）组装 HTTP 请求，打到
 * {@code http://归属节点 node_ip（为空回退配置 exec-host）:hostPort + path}，复用 {@link ExternalApiExecutor} 发起调用。
 * <p>容器的 {@code -p} 宿主端口只在承载它的那台机器上可达，因此多实例部署下基地址必须取实例归属节点。</p>
 *
 * <p>与外部API算子的区别：地址不来自 {@code function.code} 的 endpoint，而是运行实例的宿主端口 + 接口路径；
 * 且调用前强制要求容器处于 RUNNING——未运行则报错引导用户去容器运行管理页手动启动，执行器不代为拉起。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerOperatorInvoker {

    /** 输出端口约定名：整个响应报文 / HTTP 状态码 */
    public static final String PORT_RESULT = "result";
    public static final String PORT_STATUS = "status";

    /** 路径中的占位符 {name} */
    private static final Pattern PATH_PLACEHOLDER = Pattern.compile("\\{\\s*(\\w+)\\s*}");

    private final ExternalApiExecutor executor;

    private final FunctionContainerInstanceMapper containerInstanceMapper;

    private final ContainerOperatorProperties properties;

    /** 解析 docker_config / 实例 JSON，容忍未知字段 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 执行容器算子：定位运行实例 → 校验 RUNNING → 按接口定义拼请求 → 发 HTTP → 返回端口值。
     *
     * @param function 容器算子定义（含 containerKey 与 docker_config 的单接口）
     * @param inputs   入参表（入参名 -&gt; 值）
     * @return 输出端口名 -&gt; 值（{@code result}=响应体，{@code status}=HTTP 状态码）
     */
    public Map<String, Object> invoke(Function function, Map<String, Object> inputs) {
        FunctionContainerInstance instance = requireRunningInstance(function);

        ContainerInterface itf = resolveInterface(function);
        String method = StringUtils.isBlank(itf.getMethod()) ? "GET" : itf.getMethod().trim().toUpperCase();

        Map<String, String> pathValues = new LinkedHashMap<>();
        Map<String, String> queryValues = new LinkedHashMap<>();
        Map<String, String> headerValues = new LinkedHashMap<>();
        Map<String, Object> bodyValues = new LinkedHashMap<>();
        distribute(itf, inputs, pathValues, queryValues, headerValues, bodyValues);

        String url = buildUrl(instance, itf.getPath(), pathValues, queryValues);
        String body = buildBody(method, bodyValues);

        int timeout = properties.getExecTimeoutSeconds();
        ExternalApiExecutor.HttpResult response = executor.call(url, method, headerValues, body, timeout);
        if (!response.isSuccessful()) {
            throw new BusinessException("容器算子调用返回异常状态：" + method + " " + url + " -> HTTP "
                    + response.status(), HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> ports = new LinkedHashMap<>();
        ports.put(PORT_RESULT, parseBody(response.body()));
        ports.put(PORT_STATUS, response.status());
        return ports;
    }

    /** 定位运行实例并校验处于 RUNNING：容器未起时不代为拉起，直接报错引导手动启动 */
    private FunctionContainerInstance requireRunningInstance(Function function) {
        String containerKey = function.getContainerKey();
        if (StringUtils.isBlank(containerKey)) {
            throw new BusinessException("容器算子缺少运行实例标识，无法调用：" + function.getApi(), HttpStatus.BAD_REQUEST);
        }
        FunctionContainerInstance instance = containerInstanceMapper.selectOne(
                new LambdaQueryWrapper<FunctionContainerInstance>()
                        .eq(FunctionContainerInstance::getContainerKey, containerKey));
        if (instance == null) {
            throw new BusinessException("容器算子运行实例不存在，请重新创建：" + function.getApi(), HttpStatus.BAD_REQUEST);
        }
        if (instance.getPhase() != ContainerPhaseEnum.RUNNING) {
            String label = instance.getPhase() == null ? "未知" : instance.getPhase().getLabel();
            throw new BusinessException("容器算子尚未运行（当前状态：" + label + "），请先在容器运行管理页启动后再调用："
                    + function.getApi(), HttpStatus.BAD_REQUEST);
        }
        if (instance.getHostPort() == null) {
            throw new BusinessException("容器算子缺少宿主端口映射，无法调用：" + function.getApi(), HttpStatus.BAD_REQUEST);
        }
        return instance;
    }

    /** 取本算子的接口定义：docker_config 只存本行那一个接口（设计 C 一算子一接口） */
    private ContainerInterface resolveInterface(Function function) {
        ContainerOperatorConfig config = readJson(function.getDockerConfig(), ContainerOperatorConfig.class);
        if (config == null || config.getInterfaces() == null || config.getInterfaces().isEmpty()) {
            throw new BusinessException("容器算子缺少接口定义，无法调用：" + function.getApi(), HttpStatus.BAD_REQUEST);
        }
        return config.getInterfaces().get(0);
    }

    /** 按参数位置（path/query/header/body）分发入参；未声明位置的默认进 body */
    private void distribute(ContainerInterface itf, Map<String, Object> inputs,
                            Map<String, String> pathValues, Map<String, String> queryValues,
                            Map<String, String> headerValues, Map<String, Object> bodyValues) {
        if (itf.getInputParameters() == null) {
            return;
        }
        for (ContainerInterfaceParam p : itf.getInputParameters()) {
            if (StringUtils.isBlank(p.getName())) {
                continue;
            }
            Object value = inputs == null ? null : inputs.get(p.getName());
            if (value == null) {
                continue;
            }
            String location = StringUtils.isBlank(p.getIn()) ? "body" : p.getIn().trim().toLowerCase();
            switch (location) {
                case "path" -> pathValues.put(p.getName(), text(value));
                case "query" -> queryValues.put(p.getName(), text(value));
                case "header" -> headerValues.put(p.getName(), text(value));
                default -> bodyValues.put(p.getName(), value);
            }
        }
    }

    /**
     * 拼完整调用地址：归属节点:hostPort + 填充占位符后的 path + query。
     * <p>容器的 {@code -p} 宿主端口只在承载它的那台机器（= 归属节点）上可达，故基地址取实例的
     * {@code node_ip}；为空（单节点部署或改造前的历史数据）回退全局 {@code exec-host}。</p>
     */
    private String buildUrl(FunctionContainerInstance instance, String pathTemplate, Map<String, String> pathValues,
                            Map<String, String> queryValues) {
        String path = fillPathPlaceholders(pathTemplate, pathValues);
        String host = StringUtils.defaultIfBlank(instance.getNodeIp(), properties.getExecHost());
        String url = "http://" + host + ":" + instance.getHostPort() + path;
        ExternalApiExecutor.requireValidUrl(url);
        return ExternalApiExecutor.appendQuery(url, queryValues);
    }

    private String fillPathPlaceholders(String pathTemplate, Map<String, String> pathValues) {
        String template = StringUtils.isBlank(pathTemplate) ? "/" : pathTemplate.trim();
        if (!template.startsWith("/")) {
            template = "/" + template;
        }
        Matcher matcher = PATH_PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = pathValues.get(name);
            if (StringUtils.isBlank(value)) {
                throw new BusinessException("容器算子调用缺少路径参数：" + name, HttpStatus.BAD_REQUEST);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(urlEncode(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** GET/DELETE/HEAD 不带请求体；其余有 body 参数则序列化，无则交给执行器补空对象 */
    private String buildBody(String method, Map<String, Object> bodyValues) {
        if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
            return null;
        }
        if (bodyValues.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(bodyValues);
        } catch (Exception e) {
            throw new BusinessException("容器算子请求体序列化失败：" + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 响应体尽量按 JSON 解析，非 JSON 时原样返回文本 */
    private Object parseBody(String body) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception e) {
            log.debug("容器算子响应非 JSON，按文本返回");
            return body;
        }
    }

    private <T> T readJson(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("解析容器算子配置失败({})：{}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static String text(Object value) {
        return String.valueOf(value);
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }
}
