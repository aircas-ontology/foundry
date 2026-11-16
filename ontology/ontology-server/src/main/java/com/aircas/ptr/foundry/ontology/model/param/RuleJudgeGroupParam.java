package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "规则调度单条规则：组合条件判断（可整体取反）+ 命中后执行的行为。"
        + "条件可跨任意对象实例；CDC 只在条件引用的实例发生变化时定向评估本条规则")
public class RuleJudgeGroupParam {

    @Schema(name = "condition", description = "是否对组合条件的结果整体取反：true 时条件不成立才执行（else 语义）", example = "false")
    private Boolean condition;

    @Schema(name = "judgeParams", description = "判断条件列表：括号 + AND/OR 组合", required = true)
    private List<RuleJudgeItemParam> judgeParams;

    @Schema(name = "actionParam", description = "条件成立（或取反后成立）时执行的行为参数", required = true)
    private RuleActionParam actionParam;
}