package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.filter.LoggingFilter;
import com.aircas.ptr.foundry.ontology.composite.ExternalApiExecutor;
import com.aircas.ptr.foundry.ontology.config.ContainerOperatorProperties;
import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 容器算子跨节点转发器：把「必须落在归属节点上执行」的请求原样转给那台机器上的 ontology-server 进程。
 * <p>转发目标 = 归属节点 {@code node_ip:node_port} + <b>当前请求的原始 URI 与 query</b>，
 * 请求体用手上已解析好的 DTO 重新序列化，因此对端跑的是同一个端点、同一套校验，契约不会分叉，
 * 也无需为转发单独开内部接口。</p>
 * <p>鉴权：原样透传调用方的 {@code access-token}，由对端自身的 AuthInterceptor 校验
 * ——前提是各实例配置了相同的 {@code jwt.secret}，否则一律 401，报错文案会直接点出这一点。</p>
 * <p>环路保护：转发请求带 {@link #FORWARDED_HEADER}，对端据此判定「这是兄弟节点转来的」后一律本地执行，
 * 即使 {@code node.ip} 被误配成同一个值也不会来回踢皮球。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerNodeForwarder {

    /** 转发标记头：值为本节点地址，便于对端日志串起「谁转来的」 */
    public static final String FORWARDED_HEADER = "X-Foundry-Container-Forwarded";

    private final ExternalApiExecutor executor;
    private final ContainerOperatorProperties properties;
    private final ContainerNodeIdentity identity;

    /** 解析对端返回体：容忍 data 内业务对象的未知字段（两端版本可能不完全一致） */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 当前请求是否兄弟节点转来的（转发链路中一律本地执行，不再二次转发） */
    public boolean isForwardedRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return false;
        }
        return StringUtils.isNotBlank(((ServletRequestAttributes) attributes).getRequest()
                .getHeader(FORWARDED_HEADER));
    }

    /** 转发并取回返回体中的 data（对端为有返回值的接口） */
    public <T> T forwardFor(String nodeIp, Integer nodePort, Object body, Class<T> dataType, String action) {
        JsonNode data = post(nodeIp, nodePort, requestUri(), body, action);
        if (data == null || data.isNull()) {
            return null;
        }
        return objectMapper.convertValue(data, dataType);
    }

    /** 转发并取回返回体中的 data（对端返回数组，如探查接口列表） */
    public <T> List<T> forwardList(String nodeIp, Integer nodePort, Object body, Class<T> elementType, String action) {
        JsonNode data = post(nodeIp, nodePort, requestUri(), body, action);
        if (data == null || data.isNull()) {
            return new ArrayList<>();
        }
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return objectMapper.convertValue(data, listType);
    }

    /** 转发且不取返回值（对端为 void 接口，如创建算子） */
    public void forward(String nodeIp, Integer nodePort, Object body, String action) {
        post(nodeIp, nodePort, requestUri(), body, action);
    }

    /**
     * 转发到归属节点的指定 URI（非当前请求 URI），用于副作用转发场景：
     * 如删除算子时的文件清理，原始请求 URI 是 DELETE /function/delete，但清理端点是独立的 POST 路径。
     */
    public void forwardTo(String nodeIp, Integer nodePort, String uri, Object body, String action) {
        post(nodeIp, nodePort, uri, body, action);
    }

    /**
     * 实际发起转发：拼目标地址与头、调用、判定成功与否，失败统一转成带归属节点地址的 BusinessException。
     *
     * @param uri 目标端点 URI（由调用方传入：同端点转发传当前请求 URI，跨端点转发传指定路径）
     * @return 返回体中的 data 节点，可能为 null
     */
    private JsonNode post(String nodeIp, Integer nodePort, String uri, Object body, String action) {
        if (StringUtils.isBlank(nodeIp) || nodePort == null || nodePort <= 0) {
            throw new BusinessException("容器算子" + action + "的归属节点地址不完整（" + nodeIp + ":" + nodePort
                    + "），请到该节点上操作", HttpStatus.BAD_REQUEST);
        }
        HttpServletRequest request = currentRequest(action);
        String target = nodeIp.trim() + ":" + nodePort;
        String url = "http://" + target + uri;
        // 同端点转发时 URI 不带 query，需从原请求透传；跨端点转发时 URI 已自带完整 query，不再拼接原请求的
        if (!uri.contains("?")) {
            String query = request.getQueryString();
            if (StringUtils.isNotBlank(query)) {
                url = url + "?" + query;
            }
        }
        ExternalApiExecutor.requireValidUrl(url);

        int timeoutSeconds = properties.getForwardTimeoutSeconds();
        log.info("容器算子{}请求转发至归属节点 {}（本节点 {}）", action, url, identity.describeSelf());
        ExternalApiExecutor.HttpResult response;
        try {
            response = executor.call(url, "POST", headers(request),
                    body == null ? null : writeJson(body), timeoutSeconds);
        } catch (BusinessException e) {
            throw new BusinessException("容器算子" + action + "无法送达归属节点 " + target + "（本端最多等待 "
                    + timeoutSeconds + "s）：" + e.getMessage()
                    + "。该节点可能仍在执行，请稍后用状态刷新确认结果", HttpStatus.BAD_REQUEST);
        }

        // 先判状态码再解报文：对端鉴权失败返回的可能是网关/容器生成的非 JSON 页，
        // 若先解析就会把「jwt.secret 不一致」这个真正原因掩盖成「响应无法解析」
        if (response.status() == HttpStatus.UNAUTHORIZED.value() || response.status() == HttpStatus.FORBIDDEN.value()) {
            throw new BusinessException("容器算子" + action + "在归属节点 " + target
                    + " 鉴权失败：多实例部署时各节点的 jwt.secret 必须完全一致", HttpStatus.BAD_REQUEST);
        }
        JsonNode root = parse(response.body(), target, action);
        String message = root == null ? "" : root.path("message").asText("");
        Integer code = root != null && root.path("code").isNumber() ? root.path("code").asInt() : null;
        Integer successCode = ResultCode.SUCCESS.getCode();
        if (!response.isSuccessful() || (code != null && !code.equals(successCode))) {
            throw new BusinessException("归属节点 " + target + " 执行容器算子" + action + "失败："
                    + StringUtils.defaultIfBlank(message, "HTTP " + response.status()), HttpStatus.BAD_REQUEST);
        }
        return root == null ? null : root.get("data");
    }

    /** 透传调用方 token + 转发标记 + 链路 id，其余头不带 */
    private Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        String accessToken = request.getHeader(Constants.ACCESS_TOKEN_HEADER);
        if (StringUtils.isNotBlank(accessToken)) {
            headers.put(Constants.ACCESS_TOKEN_HEADER, accessToken);
        }
        // 标记头值必须是 ASCII：describeSelf() 在未配 node.ip 时是中文说明串，OkHttp 会因非法字符抛异常
        String self = identity.nodeIp();
        headers.put(FORWARDED_HEADER, StringUtils.isBlank(self) ? "1" : self.trim() + ":" + identity.nodePort());
        String logId = MDC.get(LoggingFilter.LOG_ID_KEY);
        if (StringUtils.isNotBlank(logId)) {
            headers.put(LoggingFilter.LOG_ID_HEADER, logId);
        }
        return headers;
    }

    /** 解析对端 JSON；非 JSON（如网关超时页）按失败处理 */
    private JsonNode parse(String body, String target, String action) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BusinessException("归属节点 " + target + " 的容器算子" + action
                    + "响应无法解析（返回的不是本服务的标准报文，常见于网关超时或该地址未指向 ontology-server）："
                    + StringUtils.abbreviate(body, 200), HttpStatus.BAD_REQUEST);
        }
    }

    private String writeJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException("容器算子转发请求体序列化失败：" + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private HttpServletRequest currentRequest(String action) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            throw new BusinessException("容器算子" + action + "的跨节点转发只能在 HTTP 请求线程内发起"
                    + "（定时任务等无请求上下文的入口不能触发转发）", HttpStatus.BAD_REQUEST);
        }
        return ((ServletRequestAttributes) attributes).getRequest();
    }

    /** 取当前请求的 URI（同端点转发用） */
    private String requestUri() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            throw new BusinessException("容器算子跨节点转发只能在 HTTP 请求线程内发起", HttpStatus.BAD_REQUEST);
        }
        return ((ServletRequestAttributes) attributes).getRequest().getRequestURI();
    }
}
