package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.ActionHandleTypeEnum;
import com.aircas.ptr.foundry.common.constant.ActionRuleConnectType;
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
@ApiModel(description = "行为执行配置参数")
public class ActionHandleConfigInfoParam {

    @ApiModelProperty(name="actionId", value = "行为id", required = true, example = "shipLocation")
    @NotBlank(message = "actionId is empty")
    private String actionId;

    @ApiModelProperty(name = "handleType", value = "行为执行类型", required = true, example = "RULE")
    @NotNull(message = "handleType is empty")
    private ActionHandleTypeEnum handleType;

    @ApiModelProperty(name = "entityPrimaryKeys", value = "行为执行的实体主键列表，以逗号分隔", required = true, example = "[\"7\",\"10\"]")
    @NotEmpty(message = "entityPrimaryKeys is empty")
    private List<String> entityPrimaryKeys;
}
