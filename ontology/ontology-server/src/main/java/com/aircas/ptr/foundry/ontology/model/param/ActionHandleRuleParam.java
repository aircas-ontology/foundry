package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.ActionRuleConnectType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "行为规则请求")
public class ActionHandleRuleParam {

    @ApiModelProperty(name = "rules", required = true, value = "行为规则执行，规则list", example = "[{\"columnName\":\"longitude\",\"columnValue\":\"20.0\",\"condition\":\"CH\"}]")
    @NotEmpty(message = "rules is empty")
    private List<ActionHandleRuleAddParam> rules;

    @ApiModelProperty(name = "ruleConnectType", required = true, value = "行为规则拼接类型", example = "AND")
    @NotNull(message = "ruleConnectType is null")
    private ActionRuleConnectType ruleConnectType;
}
