package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.param.ExternalApiConfig;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部API函数算子执行器：按接口定义组装 HTTP 请求、发起调用、再按响应映射抽取输出端口的值。
 *
 * <p>接口定义（{@link ExternalApiConfig}）序列化存放在 {@code function.code}，
 * 注册外部API算子与编排节点调用共用同一份定义，因此外部API算子可以直接接入编排工作流。</p>
 */
@Slf4j
public class ExternalApiOperator {

    /** 输出端口约定名：整个响应报文 */
    public static final String PORT_RESULT = "result";
    public static final String PORT_STATUS = "status";

    /** endpoint 中的路径占位符 {name} */
    private static final Pattern PATH_PLACEHOLDER = Pattern.compile("\\{\\s*(\\w+)\\s*}");

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final ExternalApiExecutor executor;

    private final ObjectMapper objectMapper;

    public ExternalApiOperator(ExternalApiExecutor executor, ObjectMapper objectMapper) {
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    /**
     * 编排节点视角执行外部API算子。
     *
     * @param function 算子定义（{@code code} 为接口定义 JSON）
     * @param nodePlan 编排节点定义（仅用于失败文案）
     * @param inputs   按算子入参定义顺序组装好的入参表
     * @return 输出端口名 -&gt; 值
     */
    public Map<String, Object> runAsNode(Function function, CompositeNodePlanBO nodePlan, Map<String, Object> inputs) {
        String label = nodePlan == null ? function.getApi() : nodePlan.getLabel();
        ExternalApiConfig config = parseConfig(function.getCode(), function.getApi());
        if (StringUtils.isBlank(config.getEndpoint())) {
            throw new BusinessException("外部API节点[" + label + "]未配置接口地址", HttpStatus.BAD_REQUEST);
        }
        Map<String, String> pathValues = new LinkedHashMap<>();
        Map<String, String> queryValues = new LinkedHashMap<>();
        Map<String, String> headerValues = new LinkedHashMap<>();
        Map<String, Object> bodyValues = new LinkedHashMap<>();
        applyRequestMappings(config, inputs, pathValues, queryValues, headerValues, bodyValues);

        String url = buildUrl(config.getEndpoint(), pathValues, queryValues);
        Map<String, String> headers = buildHeaders(config, headerValues);
        String body = buildBody(config, bodyValues);

        int timeout = config.getTimeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : config.getTimeoutSeconds();
        ExternalApiExecutor.HttpResult response = executor.call(url, config.methodOrDefault(), headers, body, timeout);
        if (!response.isSuccessful()) {
            throw new BusinessException("外部API节点[" + label + "]调用返回异常状态：" + config.methodOrDefault() + " "
                    + url + " -> HTTP " + response.status(), HttpStatus.BAD_REQUEST);
        }
        return bindResponse(config, response, label);
    }

    /**
     * 解析接口定义 JSON。
     */
    public ExternalApiConfig parseConfig(String code, String functionApi) {
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("外部API算子配置为空：" + functionApi, HttpStatus.BAD_REQUEST);
        }
        try {
            return objectMapper.readValue(code, ExternalApiConfig.class);
        } catch (Exception e) {
            throw new BusinessException("外部API算子配置解析失败：" + functionApi, HttpStatus.BAD_REQUEST);
        }
    }

    /** 按请求映射把入参分发到 path/query/header/body 四个位置 */
    private void applyRequestMappings(ExternalApiConfig config, Map<String, Object> inputs,
                                      Map<String, String> pathValues, Map<String, String> queryValues,
                                      Map<String, String> headerValues, Map<String, Object> bodyValues) {
        List<ExternalApiConfig.RequestMapping> mappings = config.effectiveRequestMappings();
        if (mappings.isEmpty()) {
            // 未配置请求映射：入参整体作为 JSON 请求体
            inputs.forEach((name, value) -> {
                if (value != null) {
                    bodyValues.put(name, value);
                }
            });
            return;
        }
        for (ExternalApiConfig.RequestMapping mapping : mappings) {
            String parameterId = mapping.getParameterId();
            if (StringUtils.isBlank(parameterId)) {
                continue;
            }
            String literal = null;
            Object value;
            if (parameterId.startsWith(ExternalApiConfig.LITERAL_PREFIX)) {
                literal = parameterId.substring(ExternalApiConfig.LITERAL_PREFIX.length());
                value = literal;
            } else {
                value = inputs.get(parameterId);
            }
            if (value == null) {
                continue;
            }
            String target = StringUtils.isBlank(mapping.getTargetName()) ? fallbackName(parameterId, literal)
                    : mapping.getTargetName().trim();
            String location = StringUtils.isBlank(mapping.getLocation()) ? ExternalApiConfig.LOCATION_BODY
                    : mapping.getLocation().trim().toLowerCase();
            switch (location) {
                case ExternalApiConfig.LOCATION_PATH -> pathValues.put(target, text(value));
                case ExternalApiConfig.LOCATION_QUERY -> queryValues.put(target, text(value));
                case ExternalApiConfig.LOCATION_HEADER -> headerValues.put(target, text(value));
                case ExternalApiConfig.LOCATION_BODY -> bodyValues.put(target, jsonValue(value));
                default -> throw new BusinessException("外部API请求映射位置非法：" + mapping.getLocation(),
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    /** 替换路径占位符并拼接 query 参数 */
    private String buildUrl(String endpoint, Map<String, String> pathValues, Map<String, String> queryValues) {
        Matcher matcher = PATH_PLACEHOLDER.matcher(endpoint);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = pathValues.get(name);
            if (StringUtils.isBlank(value)) {
                throw new BusinessException("外部API地址缺少路径参数：" + name, HttpStatus.BAD_REQUEST);
            }
            // 路径片段做一次 URL 编码，避免空格与保留字符破坏地址
            String encoded = urlEncode(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(encoded));
        }
        matcher.appendTail(sb);
        String url = sb.toString();
        ExternalApiExecutor.requireValidUrl(url);
        return ExternalApiExecutor.appendQuery(url, queryValues);
    }

    private Map<String, String> buildHeaders(ExternalApiConfig config, Map<String, String> mapped) {
        Map<String, String> headers = new LinkedHashMap<>();
        config.headersOrEmpty().forEach((name, value) -> {
            if (StringUtils.isNotBlank(name)) {
                headers.put(name.trim(), value == null ? "" : value);
            }
        });
        headers.putAll(mapped);
        if (ExternalApiConfig.AUTH_API_KEY.equalsIgnoreCase(StringUtils.trimToEmpty(config.getAuthType()))) {
            if (StringUtils.isBlank(config.getApiKey())) {
                throw new BusinessException("外部API配置了 api-key 鉴权但未填写密钥", HttpStatus.BAD_REQUEST);
            }
            String header = StringUtils.isBlank(config.getApiKeyHeader()) ? "X-API-Key" : config.getApiKeyHeader().trim();
            headers.put(header, config.getApiKey());
        }
        return headers;
    }

    private String buildBody(ExternalApiConfig config, Map<String, Object> bodyValues) {
        String method = config.methodOrDefault();
        if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
            return null;
        }
        if (bodyValues.isEmpty()) {
            // 无请求映射且无入参时给一个空对象，避免部分服务端拒绝空体
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(bodyValues);
        } catch (Exception e) {
            throw new BusinessException("外部API请求体序列化失败：" + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 按响应映射抽取输出端口值；未配置响应映射时给出整个报文 */
    private Map<String, Object> bindResponse(ExternalApiConfig config, ExternalApiExecutor.HttpResult response,
                                             String label) {
        Map<String, Object> ports = new LinkedHashMap<>();
        List<ExternalApiConfig.ResponseMapping> mappings = config.effectiveResponseMappings();
        if (mappings.isEmpty()) {
            ports.put(PORT_RESULT, parseBody(response.body(), label));
            ports.put(PORT_STATUS, response.status());
            return ports;
        }
        JsonNode root = readTree(response.body(), label);
        for (ExternalApiConfig.ResponseMapping mapping : mappings) {
            String parameterId = mapping.getParameterId();
            if (StringUtils.isBlank(parameterId)) {
                continue;
            }
            String path = StringUtils.isBlank(mapping.getSourcePath()) ? parameterId : mapping.getSourcePath().trim();
            ports.put(parameterId, extract(root, path));
        }
        return ports;
    }

    /**
     * 按点分路径取值，支持 {@code a.b[0].c}、{@code /a/b} 形式；路径不存在时返回 null。
     */
    static Object extract(JsonNode root, String path) {
        if (root == null || StringUtils.isBlank(path)) {
            return null;
        }
        JsonNode current = root;
        for (String segment : path.trim().replaceFirst("^/", "").split("/")) {
            if (StringUtils.isBlank(segment)) {
                continue;
            }
            for (String part : segment.split("\\.")) {
                current = step(current, part);
                if (current == null || current.isNull()) {
                    return null;
                }
            }
        }
        return toJavaValue(current);
    }

    private static JsonNode step(JsonNode current, String token) {
        if (current == null) {
            return null;
        }
        int bracket = token.indexOf('[');
        if (bracket < 0) {
            return current.isObject() ? current.get(token) : null;
        }
        String name = token.substring(0, bracket);
        JsonNode node = current;
        if (StringUtils.isNotEmpty(name)) {
            node = node.isObject() ? node.get(name) : null;
        }
        Matcher matcher = Pattern.compile("\\[(\\d+)]").matcher(token.substring(bracket));
        while (matcher.find() && node != null) {
            node = node.has(matcher.group(1)) ? node : node.get(Integer.parseInt(matcher.group(1)));
        }
        return node;
    }

    private static Object toJavaValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                map.put(field.getKey(), toJavaValue(field.getValue()));
            }
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new java.util.ArrayList<>();
            for (JsonNode item : node) {
                list.add(toJavaValue(item));
            }
            return list;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        return node.asText();
    }

    private JsonNode readTree(String body, String label) {
        if (StringUtils.isBlank(body)) {
            throw new BusinessException("外部API节点[" + label + "]响应体为空，无法按映射取值", HttpStatus.BAD_REQUEST);
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BusinessException("外部API节点[" + label + "]响应不是合法 JSON，无法按映射取值", HttpStatus.BAD_REQUEST);
        }
    }

    private Object parseBody(String body, String label) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        try {
            return toJavaValue(objectMapper.readTree(body));
        } catch (Exception e) {
            // 非 JSON 响应按纯文本返回，交给下游节点自行处理
            log.debug("外部API[{}]响应非 JSON，按文本返回", label);
            return body;
        }
    }

    /** 把字符串形式的入参尽量还原成 JSON 值（数组/对象/数字/布尔） */
    private Object jsonValue(Object value) {
        if (!(value instanceof CharSequence text)) {
            return value;
        }
        String trimmed = text.toString().trim();
        if (trimmed.length() > 1 && (trimmed.startsWith("[") || trimmed.startsWith("{"))) {
            try {
                return toJavaValue(objectMapper.readTree(trimmed));
            } catch (Exception ignored) {
                return text.toString();
            }
        }
        return text.toString();
    }

    private static String text(Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                ? String.valueOf(value)
                : String.valueOf(value);
    }

    private static String fallbackName(String parameterId, String literal) {
        if (literal != null) {
            throw new BusinessException("外部API字面量映射必须指定 targetName", HttpStatus.BAD_REQUEST);
        }
        int colon = parameterId.indexOf(':');
        return colon >= 0 ? parameterId.substring(colon + 1) : parameterId;
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }
}
