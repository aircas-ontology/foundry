package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "行为规则请求")
public class ActionHandleRuleParam {

    @Schema(name = "rules", required = true, description = "行为规则执行，规则list", example = "[{\"columnName\":\"longitude\",\"columnValue\":\"20.0\",\"condition\":\"CH\"}]")
    @NotEmpty(message = "rules is empty")
    private List<ActionHandleRuleAddParam> rules;

    @Schema(name = "ruleConnectType", required = true, description = "行为规则拼接类型", example = "AND")
    @NotNull(message = "ruleConnectType is null")
    private ActionRuleConnectType ruleConnectType;
}
