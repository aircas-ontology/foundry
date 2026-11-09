package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.aircas.ptr.foundry.ontology.model.enums.SemgrepScriptType;

/** 发往扫描服务的最小请求，禁止增加业务标识或用户信息。 */
public record SemgrepScanRequest(
        String requestId,
        SemgrepScriptType scriptType,
        String code,
        String codeHash) {
}
