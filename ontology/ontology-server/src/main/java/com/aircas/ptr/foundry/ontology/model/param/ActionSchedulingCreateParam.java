package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "行为调度创建请求")
public class ActionSchedulingCreateParam extends ActionHandleConfigInfoParam {


    @Schema(name = "task", description = "创建定时任务行为调度")
    private ActionHandleTaskParam task;

    @Schema(name = "rule", description = "创建规则行为调度")
    private ActionHandleRuleParam rule;
}
