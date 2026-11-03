package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.aircas.ptr.foundry.ontology.model.enums.ScanSkipReasonEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanStatusEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanErrorCodeEnum;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/** 函数扫描结果的固定 11 个顶层字段。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record FunctionScanResult(
        int schemaVersion,
        ScanStatusEnum status,
        ScanSkipReasonEnum skipReason,
        String codeHash,
        OffsetDateTime scannedAt,
        String scanId,
        Long durationMs,
        Boolean findingsTruncated,
        ScanSummary summary,
        List<ScanFinding> findings,
        ScanError error) {

    public static final int SCHEMA_VERSION = 1;

    public FunctionScanResult {
        findings = List.copyOf(findings);
    }

    public static FunctionScanResult skipped(ScanSkipReasonEnum reason) {
        return new FunctionScanResult(SCHEMA_VERSION, ScanStatusEnum.SKIPPED, reason, null, null,
                null, null, null, ScanSummary.empty(), List.of(), null);
    }

    public FunctionScanSummary toSummary() {
        return new FunctionScanSummary(summary);
    }

    public FunctionScanResult rejectedWithError(ScanErrorCodeEnum code) {
        return new FunctionScanResult(schemaVersion, ScanStatusEnum.REJECTED, null, codeHash, scannedAt,
                scanId, durationMs, findingsTruncated, summary, findings, ScanError.of(code));
    }
}
