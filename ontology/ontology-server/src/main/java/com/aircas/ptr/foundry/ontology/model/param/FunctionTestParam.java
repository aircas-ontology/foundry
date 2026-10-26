package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

import static com.aircas.ptr.foundry.ontology.constant.FunctionConstant.VERSION_PATTERN;

/**
 * 函数算子统一测试请求（同时支持「已存库测试」和「草稿测试」两种模式）。
 * <p>模式判定：</p>
 * <ul>
 *   <li>{@code functionApi} 非空 → 已存库测试，按 api + version 查库取定义</li>
 *   <li>{@code functionApi} 为空 + {@code type} 非空 → 草稿测试，直接用请求体中的 code / apiConfig 执行，不落库</li>
 * </ul>
 * <p>根据函数类型使用不同字段：</p>
 * <ul>
 *   <li>BASIC_QUERY：使用 ontologyIdentifier + variableBindings + 分页参数</li>
 *   <li>CUSTOMIZE/EXTERNAL：使用 parameters（CUSTOMIZE 草稿需额外传 code + language；EXTERNAL 草稿需额外传 apiConfig）</li>
 * </ul>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数算子统一测试请求（已存库 / 草稿通用）")
public class FunctionTestParam {

    // ========== 已存库测试必填，草稿测试忽略 ==========

    @Schema(name = "functionApi", description = "函数 api（已存库测试必填；草稿测试时不传）")
    private String functionApi;

    @Schema(name = "version", description = "版本号（已存库测试必填，格式 x.y.z；草稿测试时不传）")
    @Pattern(regexp = VERSION_PATTERN, message = "版本号格式必须为 x.y.z（如 1.0.0）")
    private String version;

    // ========== 草稿测试专用 ==========

    @Schema(name = "type", description = "函数类型（草稿测试必填：CUSTOMIZE / EXTERNAL；已存库测试忽略）")
    private FunctionTypeEnum type;

    @Schema(name = "code", description = "脚本源码（CUSTOMIZE 草稿测试必填）")
    private String code;

    @Schema(name = "language", description = "脚本语言（CUSTOMIZE 草稿测试必填，不传默认 GROOVY）")
    private FunctionLanguageEnum language;

    @Schema(name = "apiConfig", description = "外部 API 配置（EXTERNAL 草稿测试必填）")
    private ExternalApiConfig apiConfig;

    // ========== BASIC_QUERY 类型专用 ==========

    @Schema(name = "ontologyIdentifier", description = "测试本体 id（BASIC_QUERY 类型必填）")
    private String ontologyIdentifier;

    @Schema(name = "variableBindings",
            description = "变量绑定：key=创建时的变量名，value=实际属性 apiName（BASIC_QUERY 类型必填）",
            example = "{\"amount\": \"price\", \"status\": \"order_status\"}")
    private Map<String, String> variableBindings;

    @Schema(name = "pageNum", description = "分页号，默认 1（BASIC_QUERY query 模式有效）")
    @jakarta.validation.constraints.Min(value = 1, message = "pageNum 必须大于 0")
    private Integer pageNum = 1;

    @Schema(name = "pageSize", description = "每页条数，默认 10（BASIC_QUERY query 模式有效）")
    @jakarta.validation.constraints.Min(value = 1, message = "pageSize 必须大于 0")
    @jakarta.validation.constraints.Max(value = 1000, message = "pageSize 不能超过 1000")
    private Integer pageSize = 10;

    // ========== CUSTOMIZE/EXTERNAL 类型专用 ==========

    @Schema(name = "parameters", description = "参数列表（CUSTOMIZE/EXTERNAL 类型必填）")
    private List<FunctionParameter> parameters;

    /**
     * 是否为草稿测试（functionApi 为空 + type 非空）。
     */
    public boolean isDraftTest() {
        return (functionApi == null || functionApi.isBlank()) && type != null;
    }
}
