package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import com.aircas.ptr.foundry.ontology.model.param.ActionHandleRuleAddParam;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "行为规则信息VO")
public class ActionHandleRuleVO {

    @Schema(name = "rules", description = "行为规则执行，规则list", example = "[{\"columnName\":\"longitude\",\"columnValue\":\"20.0\",\"condition\":\"CH\"}]")
    private List<ActionHandleRuleAddParam> rules;

    @Schema(name = "ruleConnectType", description = "行为规则拼接类型", example = "AND")
    private ActionRuleConnectType ruleConnectType;


}
