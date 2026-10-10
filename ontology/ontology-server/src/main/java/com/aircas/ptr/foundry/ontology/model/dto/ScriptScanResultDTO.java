package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.enums.ScriptScanStatusEnum;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value
@Builder
public class ScriptScanResultDTO {
    ScriptScanStatusEnum status;
    String engine;
    String engineVersion;
    String ruleSetVersion;
    Long durationMillis;
    @Builder.Default
    List<ScriptScanFindingDTO> findings = Collections.emptyList();
    String errorSummary;
    boolean truncated;
}
