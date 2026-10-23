package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 扫描服务成功 HTTP 响应。字段存在性由客户端在反序列化前校验。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SemgrepScanResponse(
        String requestId,
        String scanId,
        Long durationMs,
        Boolean findingsTruncated,
        List<SemgrepFindingResponse> findings,
        SemgrepErrorResponse error) {
}
