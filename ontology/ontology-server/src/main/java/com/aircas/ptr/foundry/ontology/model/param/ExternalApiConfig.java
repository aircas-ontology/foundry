package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部API函数算子配置。
 *
 * <p>注册外部API算子（{@code type = EXTERNAL}）时序列化存入 {@code function.code}，
 * 既用于 {@code /function/execute} 的单算子调用，也用于作为函数编排画布节点被调用，
 * 因此脚本算子、基础算子、外部API算子三类算子都能接入编排工作流。</p>
 *
 * <p>字段与原型 {@code FunctionOperatorApiDefinition} 对齐：请求映射决定入参写到报文的哪个位置，
 * 响应映射决定出参从报文的哪个位置取值。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部API函数算子配置")
public class ExternalApiConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 入参在 HTTP 报文中的落点 */
    public static final String LOCATION_PATH = "path";
    public static final String LOCATION_QUERY = "query";
    public static final String LOCATION_HEADER = "header";
    public static final String LOCATION_BODY = "body";

    /** 鉴权方式 */
    public static final String AUTH_NONE = "none";
    public static final String AUTH_API_KEY = "api-key";

    /** 固定字面量值映射的入参约定前缀：{@code literal:xxx} 表示直接使用 xxx 作为字面量 */
    public static final String LITERAL_PREFIX = "literal:";

    @Schema(name = "endpoint", description = "接口地址，支持 {param} 路径占位符", example = "http://host/api/orders/{id}")
    private String endpoint;

    @Schema(name = "method", description = "请求方法：GET/POST/PUT/DELETE，默认 POST")
    private String method;

    @Schema(name = "authType", description = "鉴权方式：none/api-key，默认 none")
    private String authType;

    @Schema(name = "apiKeyHeader", description = "api-key 模式的请求头名，默认 X-API-Key")
    private String apiKeyHeader;

    @Schema(name = "apiKey", description = "api-key 模式的密钥")
    private String apiKey;

    @Schema(name = "contentType", description = "请求体 Content-Type，默认 application/json")
    private String contentType;

    @Schema(name = "headers", description = "固定请求头")
    private Map<String, String> headers;

    @Schema(name = "requestMappings", description = "请求映射：入参 -> HTTP 报文位置")
    private List<RequestMapping> requestMappings;

    @Schema(name = "responseMappings", description = "响应映射：响应字段 -> 出参")
    private List<ResponseMapping> responseMappings;

    @Schema(name = "timeoutSeconds", description = "超时秒数，非正数时使用默认 30 秒")
    private Integer timeoutSeconds;

    @Schema(name = "inputs", description = "入参定义；为空时从请求映射的 parameterId 推导")
    private List<PortDefinition> inputs;

    @Schema(name = "outputs", description = "出参定义；为空时从响应映射的 parameterId 推导")
    private List<PortDefinition> outputs;

    public List<PortDefinition> inputsOrEmpty() {
        return inputs == null ? new ArrayList<>() : inputs;
    }

    public List<PortDefinition> outputsOrEmpty() {
        return outputs == null ? new ArrayList<>() : outputs;
    }

    /** 请求方法缺省值 */
    public String methodOrDefault() {
        return method == null || method.trim().isEmpty() ? "POST" : method.trim().toUpperCase();
    }

    /** 固定请求头，永不为 null */
    public Map<String, String> headersOrEmpty() {
        return headers == null ? new LinkedHashMap<>() : headers;
    }

    public List<RequestMapping> requestMappingsOrEmpty() {
        return requestMappings == null ? new ArrayList<>() : requestMappings;
    }

    public List<ResponseMapping> responseMappingsOrEmpty() {
        return responseMappings == null ? new ArrayList<>() : responseMappings;
    }

    /**
     * 单条请求映射。
     *
     * <p>{@code parameterId} 支持两种写法：算子入参名（编排节点端口名），
     * 或者 {@code literal:字面量} 表示不取入参而直接使用固定值。</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "外部API请求映射")
    public static class RequestMapping implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(name = "parameterId", description = "入参名，或 literal:字面量", example = "orderId")
        private String parameterId;

        @Schema(name = "location", description = "落点：path/query/header/body", example = "path")
        private String location;

        @Schema(name = "targetName", description = "报文中的名字，缺省用入参名", example = "id")
        private String targetName;
    }

    /** 单条响应映射：从响应报文的 sourcePath 取值写入出参 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "外部API响应映射")
    public static class ResponseMapping implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(name = "parameterId", description = "出参名", example = "amount")
        private String parameterId;

        @Schema(name = "sourcePath", description = "响应取值路径，支持 a.b.c / a.b[0].c，缺省用出参名", example = "data.order.amount")
        private String sourcePath;
    }

    /**
     * 端口（参数）定义。
     *
     * <p>注册外部API算子时用于生成 {@code function_param} 行，编排画布节点的端口
     * 完全由 {@code function_param} 推导，因此这里缺省时由请求/响应映射兜底。</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "外部API端口定义")
    public static class PortDefinition implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(name = "name", description = "参数名", example = "orderId")
        private String name;

        @Schema(name = "type", description = "参数类型：int/string/double/bool/object/list，缺省 string", example = "string")
        private String type;

        @Schema(name = "description", description = "参数描述")
        private String description;

        @Schema(name = "required", description = "是否必填")
        private Boolean required;
    }
}
