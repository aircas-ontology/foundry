package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "行为规则请求：规则数组，每条规则 = 组合条件（judgeParams，括号+AND/OR，可整体取反）+ 行为执行参数（actionParam）")
public class ActionHandleRuleParam {

    @NotEmpty(message = "judgeRules is empty")
    @Schema(name = "judgeRules", description = "规则数组：条件可跨任意对象实例，CDC 只在条件引用的实例变化时定向评估；"
            + "规则启用时先对绑定实例评估一遍存量数据", required = true)
    private List<RuleJudgeGroupParam> judgeRules;
}