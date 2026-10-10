package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.enums.ScriptRiskCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptRiskSeverityEnum;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScriptScanFindingDTO {
    String ruleId;
    ScriptRiskCategoryEnum category;
    ScriptRiskSeverityEnum severity;
    String message;
    Integer line;
    Integer column;
}
