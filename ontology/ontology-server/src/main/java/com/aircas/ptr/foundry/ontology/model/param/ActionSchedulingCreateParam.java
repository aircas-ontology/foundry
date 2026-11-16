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


    @Schema(name = "spaceId", description = "行为所属空间 id，必填；调度只能绑定本空间的行为", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer spaceId;

    @Schema(name = "task", description = "创建定时任务行为调度")
    private ActionHandleTaskParam task;

    @Schema(name = "rule", description = "创建规则行为调度")
    private ActionHandleRuleParam rule;
}
