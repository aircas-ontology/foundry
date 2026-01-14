package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import com.aircas.ptr.foundry.ontology.model.param.ActionHandleRuleAddParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(value = "行为规则信息VO")
public class ActionHandleRuleVO {

    @ApiModelProperty(name = "rules", value = "行为规则执行，规则list", example = "[{\"columnName\":\"longitude\",\"columnValue\":\"20.0\",\"condition\":\"CH\"}]")
    private List<ActionHandleRuleAddParam> rules;

    @ApiModelProperty(name = "ruleConnectType", value = "行为规则拼接类型", example = "AND")
    private ActionRuleConnectType ruleConnectType;


}
