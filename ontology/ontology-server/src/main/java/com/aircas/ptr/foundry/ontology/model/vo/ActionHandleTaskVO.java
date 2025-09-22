package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "行为任务")
public class ActionHandleTaskVO {

    @ApiModelProperty(name = "entityNames", value = "实体名称列表")
    private List<String> entityNames;

    @ApiModelProperty(value = "任务开始时间，在functionApi不为空时生效", example = "2024-01-01 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date taskStartTime;

    @ApiModelProperty(value = "任务结束时间，在functionApi不为空时生效", example = "2024-01-02 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date taskEndTime;

    @ApiModelProperty(value = "任务执行周期表达式，在functionApi不为空时生效", example = "30 * * 1/1 * ? *")
    private String taskCronExpression;

    @ApiModelProperty(name = "ontologyName", value = "本体名称", example = "名称")
    private String ontologyName;
}
