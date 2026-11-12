package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingPeriodTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "行为任务请求")
public class ActionHandleTaskParam {


    @Schema(description = "任务执行周期表达式，在functionApi不为空时生效", example = "30 * * 1/1 * ? *")
    @NotNull(message = "taskCronExpression is null")
    private String taskCronExpression;

    @Schema(description = "周期调度类型：DAILY(每天)/WEEKLY(每周)/MONTHLY(每月)/CRON", example = "DAILY")
    private ActionSchedulingPeriodTypeEnum periodType;

    @Schema(description = "实体执行参数 JSON，执行时反序列化成 EntityActionExecuteParam；不传则按对象下全部实例遍历执行",
            example = "{\"ontologySpaceId\":1,\"actionApi\":\"shipLocation\",\"ontologyUniqueIdentifier\":\"abcdef\",\"entityPrimaryKey\":\"3\"}")
    private String entityJson;

}
