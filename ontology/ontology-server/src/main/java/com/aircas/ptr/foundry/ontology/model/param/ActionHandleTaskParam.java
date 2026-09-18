package com.aircas.ptr.foundry.ontology.model.param;

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

}
