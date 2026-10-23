package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 扫描服务原始错误；message 永不向外透传。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SemgrepErrorResponse(String code, String message) {
}
