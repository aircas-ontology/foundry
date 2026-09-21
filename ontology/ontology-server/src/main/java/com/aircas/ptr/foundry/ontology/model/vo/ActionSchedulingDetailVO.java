package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "行为调度详情VO")
public class ActionSchedulingDetailVO extends ActionSchedulingInfoVO {

    @Schema(name = "ruleVO", description = "行为规则调度详情")
    private ActionHandleRuleVO ruleVO;

    @Schema(name = "ruleVO", description = "行为周期调度详情")
    private ActionHandleTaskInfoVO taskVO;
}
