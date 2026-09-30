package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 函数算子统一测试请求。
 * <p>根据函数类型使用不同字段：</p>
 * <ul>
 *   <li>BASIC_QUERY：使用 ontologyIdentifier + variableBindings + 分页参数</li>
 *   <li>CUSTOMIZE/EXTERNAL：使用 parameters</li>
 * </ul>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数算子统一测试请求")
public class FunctionTestParam {

    @Schema(name = "functionApi", description = "函数 api", required = true)
    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    // ========== BASIC_QUERY 类型专用 ==========

    @Schema(name = "ontologyIdentifier", description = "测试本体 id（BASIC_QUERY 类型必填）")
    private String ontologyIdentifier;

    @Schema(name = "variableBindings",
            description = "变量绑定：key=创建时的变量名，value=实际属性 apiName（BASIC_QUERY 类型必填）",
            example = "{\"amount\": \"price\", \"status\": \"order_status\"}")
    private Map<String, String> variableBindings;

    @Schema(name = "pageNum", description = "分页号，默认 1（BASIC_QUERY query 模式有效）")
    private Integer pageNum = 1;

    @Schema(name = "pageSize", description = "每页条数，默认 10（BASIC_QUERY query 模式有效）")
    private Integer pageSize = 10;

    // ========== CUSTOMIZE/EXTERNAL 类型专用 ==========

    @Schema(name = "parameters", description = "参数列表（CUSTOMIZE/EXTERNAL 类型必填）")
    private List<FunctionParameter> parameters;
}
