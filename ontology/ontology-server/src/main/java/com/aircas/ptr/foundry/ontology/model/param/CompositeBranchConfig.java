package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分支节点配置。
 * <p>expression 为 Groovy 布尔表达式，可使用 #item、#value 等占位符引用上下文数据。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排分支节点配置")
public class CompositeBranchConfig {

    @Schema(name = "expression", description = "分支判断表达式（Groovy 布尔表达式）", example = "#value != null")
    private String expression;
}