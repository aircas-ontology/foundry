package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.param.RuleJudgeGroupParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "行为规则信息VO")
public class ActionHandleRuleVO {

    @Schema(name = "judgeRules", description = "规则数组：组合条件（括号+AND/OR，可整体取反）+ 行为执行参数")
    private List<RuleJudgeGroupParam> judgeRules;

}