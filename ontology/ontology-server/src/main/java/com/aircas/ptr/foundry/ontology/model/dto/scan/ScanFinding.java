package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.aircas.ptr.foundry.ontology.model.enums.ScanSeverityEnum;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 可持久化、可对外返回的 finding 固定九字段结构。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanFinding(
        String ruleId,
        String category,
        ScanSeverityEnum severity,
        String message,
        String reason,
        Integer startLine,
        Integer startColumn,
        Integer endLine,
        Integer endColumn) {
}
