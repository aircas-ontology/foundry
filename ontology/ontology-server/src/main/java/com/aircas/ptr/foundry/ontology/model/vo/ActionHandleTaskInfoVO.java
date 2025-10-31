package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "行为周期任务")
public class ActionHandleTaskInfoVO {


    @ApiModelProperty(value = "任务开始时间，在functionApi不为空时生效", example = "2024-01-01 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date taskStartTime;

    @ApiModelProperty(value = "任务结束时间，在functionApi不为空时生效", example = "2024-01-02 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date taskEndTime;

    @ApiModelProperty(value = "任务执行周期表达式，在functionApi不为空时生效", example = "30 * * 1/1 * ? *")
    private String taskCronExpression;

}
