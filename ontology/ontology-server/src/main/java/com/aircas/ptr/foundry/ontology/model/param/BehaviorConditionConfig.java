package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 表达式条件节点的配置。
 * <p>表达式用 SpEL，裸名即黑板键名；求值走 SimpleEvaluationContext 只读绑定，
 * 不能用存量 EntityServiceImpl 里的 StandardEvaluationContext，那种上下文允许 T(java.lang.Runtime)。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树表达式条件节点配置")
public class BehaviorConditionConfig {

    @Schema(name = "expression", description = "SpEL 条件表达式，求值为 true 则该节点成功", example = "ammoCount > 0")
    private String expression;
}
