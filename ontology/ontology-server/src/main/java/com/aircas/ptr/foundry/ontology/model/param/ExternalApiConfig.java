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
 * <p>契约简化为「声明即映射，一律以 {@code name} 为准」：入参 {@code inputs} 的 {@code name}
 * 即其在 HTTP 报文中的键名（配合 {@code location} 决定落点），出参 {@code outputs} 的 {@code name}
 * 即从响应报文的取值路径。不再提供独立的请求/响应映射，也不再有改名/别名层。</p>
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

    @Schema(name = "timeoutSeconds", description = "超时秒数，非正数时使用默认 30 秒")
    private Integer timeoutSeconds;

    @Schema(name = "inputs", description = "入参定义：name 即报文键名，location 决定落点（path/query/header/body，缺省 body）")
    private List<PortDefinition> inputs;

    @Schema(name = "outputs", description = "出参定义：name 即从响应报文的取值路径（点分/数组，如 data.order.amount）")
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

    /**
     * 端口（参数）定义。
     *
     * <p>注册外部API算子时用于生成 {@code function_param} 行，编排画布节点的端口
     * 完全由 {@code function_param} 推导。入参用 {@code location} 指明落点，
     * 出参的 {@code name} 兼作响应取值路径。</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "外部API端口定义")
    public static class PortDefinition implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(name = "name", description = "参数名（入参=报文键名，出参=响应取值路径）", example = "orderId")
        private String name;

        @Schema(name = "type", description = "参数类型：int/string/double/bool/object/list，缺省 string", example = "string")
        private String type;

        @Schema(name = "description", description = "参数描述")
        private String description;

        @Schema(name = "required", description = "是否必填")
        private Boolean required;

        @Schema(name = "location", description = "入参落点：path/query/header/body，缺省 body（出参忽略此字段）", example = "query")
        private String location;
    }
}
