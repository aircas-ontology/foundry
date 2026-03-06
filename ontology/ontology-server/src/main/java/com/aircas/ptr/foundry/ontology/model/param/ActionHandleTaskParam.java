package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "行为任务请求")
public class ActionHandleTaskParam {


    @ApiModelProperty(value = "任务执行周期表达式，在functionApi不为空时生效", example = "30 * * 1/1 * ? *")
    @NotNull(message = "taskCronExpression is null")
    private String taskCronExpression;

}
