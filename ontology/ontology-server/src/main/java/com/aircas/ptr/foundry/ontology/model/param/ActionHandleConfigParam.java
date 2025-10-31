package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.common.constant.ActionRuleConnectType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * @className: ActionHandleConfigParam
 * @author: yangj
 * @date: 2024/9/1 19:27
 * @version: 1.0
 * @description: 行为执行配置参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "行为执行配置参数")
public class ActionHandleConfigParam {

    @ApiModelProperty(value = "行为api", required = true, example = "shipLocation")
    private String actionApi;

    @ApiModelProperty(value = "行为执行类型", required = true, example = "RULE")
    private ActionSchedulingTypeEnum handleType;

    @ApiModelProperty(value = "任务的实体主键列表，以逗号分隔", example = "[\"7\",\"10\"]")
    private List<String> objectPrimaryKeys;

    @ApiModelProperty(value = "任务开始时间，在functionApi不为空时生效", example = "2024-01-01 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date taskStartTime;

    @ApiModelProperty(value = "任务结束时间，在functionApi不为空时生效", example = "2024-01-02 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date taskEndTime;

    @ApiModelProperty(value = "任务执行周期，在functionApi不为空时生效", example = "30 * * 1/1 * ? *")
    private String taskCorn;

    @ApiModelProperty(value = "行为规则执行，规则list", example = "[{\"columnName\":\"longitude\",\"columnValue\":\"20.0\",\"condition\":\"CH\"}]")
    private List<ActionHandleRuleAddParam> rules;

    @ApiModelProperty(value = "行为规则拼接类型", example = "AND")
    private ActionRuleConnectType ruleConnectType;
}
