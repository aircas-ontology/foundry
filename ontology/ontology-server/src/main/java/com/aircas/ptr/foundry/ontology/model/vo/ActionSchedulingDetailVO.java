package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(description = "行为调度详情VO")
public class ActionSchedulingDetailVO extends ActionSchedulingInfoVO {

    @ApiModelProperty(name = "ruleVO", value = "行为规则调度详情")
    private ActionHandleRuleVO ruleVO;

    @ApiModelProperty(name = "ruleVO", value = "行为周期调度详情")
    private ActionHandleTaskInfoVO taskVO;
}
