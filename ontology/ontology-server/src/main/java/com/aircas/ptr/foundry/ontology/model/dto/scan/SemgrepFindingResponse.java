package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 扫描服务 finding 的公开字段。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SemgrepFindingResponse(
        String ruleId,
        String category,
        String severity,
        String message,
        String reason,
        Integer startLine,
        Integer startColumn,
        Integer endLine,
        Integer endColumn) {
}
