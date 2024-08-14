package com.aircas.ptr.foundry.ontology.repository.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "行为新增模型")
public class ActionAddParam {

    @ApiModelProperty(value = "行为api，用来调用", required = true, example = "cal")
    private String api;

    @ApiModelProperty(value = "函数api", example = "satellite")
    private String functionApi;

    @ApiModelProperty(value = "本体id", required = true, example = "fdsa-dfsaf-gdsagfd")
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(value = "行为描述", example = "这是一个行为")
    private String description;

    @ApiModelProperty(value = "行为显示名称", required = true, example = "调用函数")
    private String displayName;

    @ApiModelProperty(value = "任务开始时间，在functionApi不为空时生效", example = "2024-01-01 00:00:00")
    private Date taskStartTime;

    @ApiModelProperty(value = "任务结束时间，在functionApi不为空时生效", example = "2024-01-02 00:00:00")
    private Date taskEndTime;

    @ApiModelProperty(value = "任务开始时间，在functionApi不为空时生效", example = "30 * * 1/1 * ? *")
    private String taskCorn;
}
