package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 基础查询算子测试执行请求。
 * <p>前端选择本体后，将创建时定义的变量名映射到实际属性 apiName。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "基础查询算子测试请求")
public class BasicQueryTestParam {

    @Schema(name = "functionApi", description = "函数 api", required = true)
    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    @Schema(name = "ontologyIdentifier", description = "测试本体 id", required = true)
    @NotBlank(message = "ontologyIdentifier is empty")
    private String ontologyIdentifier;

    @Schema(name = "variableBindings",
            description = "变量绑定：key=创建时的变量名，value=实际属性 apiName",
            example = "{\"amount\": \"price\", \"status\": \"order_status\"}",
            required = true)
    @NotNull(message = "variableBindings is null")
    @Size(min = 1, message = "至少绑定一个变量")
    private Map<String, String> variableBindings;

    @Schema(name = "pageNum", description = "分页号，默认 1（仅 query 模式有效，聚合操作忽略）")
    private Integer pageNum = 1;

    @Schema(name = "pageSize", description = "每页条数，默认 10（仅 query 模式有效，聚合操作忽略）")
    private Integer pageSize = 10;
}
