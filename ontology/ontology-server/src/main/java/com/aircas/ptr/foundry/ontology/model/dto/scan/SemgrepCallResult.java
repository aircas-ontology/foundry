package com.aircas.ptr.foundry.ontology.model.dto.scan;

import java.time.OffsetDateTime;

/** 客户端生成的可信请求元数据及已通过协议校验的远端响应。 */
public record SemgrepCallResult(
        String requestId,
        String codeHash,
        OffsetDateTime scannedAt,
        SemgrepScanResponse response) {
}
