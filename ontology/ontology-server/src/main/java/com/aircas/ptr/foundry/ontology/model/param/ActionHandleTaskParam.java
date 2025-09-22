package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.ActionHandleTypeEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "行为任务请求")
public class ActionHandleTaskParam extends ActionHandleConfigInfoParam{

    @ApiModelProperty(value = "任务开始时间，在functionApi不为空时生效", example = "2024-01-01 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @NotNull(message = "taskStartTime is null")
    private Date taskStartTime;

    @ApiModelProperty(value = "任务结束时间，在functionApi不为空时生效", example = "2024-01-02 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @NotNull(message = "taskEndTime is null")
    private Date taskEndTime;

    @ApiModelProperty(value = "任务执行周期表达式，在functionApi不为空时生效", example = "30 * * 1/1 * ? *")
    @NotNull(message = "taskCronExpression is null")
    private String taskCronExpression;

}
