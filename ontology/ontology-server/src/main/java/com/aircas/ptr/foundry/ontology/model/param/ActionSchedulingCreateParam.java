package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "行为调度创建请求")
public class ActionSchedulingCreateParam extends ActionHandleConfigInfoParam {


    @ApiModelProperty(name = "task", value = "创建定时任务行为调度")
    private ActionHandleTaskParam task;

    @ApiModelProperty(name = "rule", value = "创建规则行为调度")
    private ActionHandleRuleParam rule;
}
