package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "行为规则信息VO")
public class ActionHandleRuleVO {

    @Schema(name = "ruleFlow", description = "规则编排 DSL（ScheduleRuleFlowDocument），由 action_handle_rule.rules 反序列化而来")
    private JsonNode ruleFlow;

    @Schema(name = "ruleConnectType", description = "行为规则拼接类型", example = "AND")
    private ActionRuleConnectType ruleConnectType;

}
