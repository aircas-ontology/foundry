package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "行为规则请求")
public class ActionHandleRuleParam {

    @Schema(name = "ruleFlow", required = true, description = "规则编排 DSL（ScheduleRuleFlowDocument），直接存入 action_handle_rule.rules")
    private JsonNode ruleFlow;

    @Schema(name = "ruleConnectType", description = "行为规则拼接类型（兼容保留，组合逻辑以 DSL 内 composite.logic 为准）", example = "AND")
    private ActionRuleConnectType ruleConnectType;
}
