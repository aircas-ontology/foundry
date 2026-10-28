package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.client.SemgrepScanClient;
import com.aircas.ptr.foundry.ontology.config.SemgrepScanProperties;
import com.aircas.ptr.foundry.ontology.exception.SemgrepScanException;
import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionScanResult;
import com.aircas.ptr.foundry.ontology.model.dto.scan.ScanError;
import com.aircas.ptr.foundry.ontology.model.dto.scan.ScanFinding;
import com.aircas.ptr.foundry.ontology.model.dto.scan.ScanSummary;
import com.aircas.ptr.foundry.ontology.model.dto.scan.SemgrepCallResult;
import com.aircas.ptr.foundry.ontology.model.dto.scan.SemgrepErrorResponse;
import com.aircas.ptr.foundry.ontology.model.dto.scan.SemgrepFindingResponse;
import com.aircas.ptr.foundry.ontology.model.dto.scan.SemgrepScanResponse;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanErrorCode;
import com.aircas.ptr.foundry.ontology.model.enums.ScanPurpose;
import com.aircas.ptr.foundry.ontology.model.enums.ScanSeverity;
import com.aircas.ptr.foundry.ontology.model.enums.ScanSkipReason;
import com.aircas.ptr.foundry.ontology.model.enums.ScanStatus;
import com.aircas.ptr.foundry.ontology.utils.ScriptTypeMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 扫描跳过策略、语言映射和 fail-closed 业务判定。 */
@Service
public class ScriptSecurityScanService {

    private final SemgrepScanProperties properties;
    private final SemgrepScanClient client;

    public ScriptSecurityScanService(SemgrepScanProperties properties, SemgrepScanClient client) {
        this.properties = properties;
        this.client = client;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public FunctionScanResult scan(FunctionTypeEnum functionType, FunctionLanguageEnum language,
                                   String code, ScanPurpose purpose) {
        if (functionType != FunctionTypeEnum.CUSTOMIZE) {
            return FunctionScanResult.skipped(ScanSkipReason.NON_SCRIPT_OPERATOR);
        }
        if (!properties.isEnabled()) {
            return FunctionScanResult.skipped(ScanSkipReason.FEATURE_DISABLED);
        }

        SemgrepCallResult call = client.scan(ScriptTypeMapper.toSemgrep(language), code, purpose);
        SemgrepScanResponse response = call.response();

        // 顺序固定：HTTP/协议已由 client 处理；随后依次处理 error、截断、severity。
        ScanError mappedError = mapResponseError(response.error());
        if (mappedError != null) {
            return rejected(call, mapFindings(response.findings()), mappedError);
        }
        if (response.findingsTruncated()) {
            throw new SemgrepScanException(ScanErrorCode.FINDINGS_TRUNCATED);
        }

        List<ScanFinding> findings = mapFindings(response.findings());
        ScanSummary summary = ScanSummary.from(findings);
        ScanStatus status = summary.high() > 0 ? ScanStatus.REJECTED : ScanStatus.PASSED;
        return complete(call, findings, summary, status, null);
    }

    private ScanError mapResponseError(SemgrepErrorResponse error) {
        if (error == null) {
            return null;
        }
        String code = error.code();
        if (ScanErrorCode.PARSE_FAILED.name().equals(code)) {
            return ScanError.of(ScanErrorCode.PARSE_FAILED);
        }
        if (ScanErrorCode.PARTIAL_PARSE.name().equals(code)) {
            return ScanError.of(ScanErrorCode.PARTIAL_PARSE);
        }
        ScanErrorCode failedCode = switch (code) {
            case "INVALID_REQUEST" -> ScanErrorCode.INVALID_REQUEST;
            case "UNSUPPORTED_LANGUAGE" -> ScanErrorCode.UNSUPPORTED_LANGUAGE;
            case "CODE_HASH_MISMATCH" -> ScanErrorCode.CODE_HASH_MISMATCH;
            case "TARGET_SKIPPED" -> ScanErrorCode.TARGET_SKIPPED;
            case "REPORT_INVALID" -> ScanErrorCode.REPORT_INVALID;
            case "REPORT_MISSING" -> ScanErrorCode.REPORT_MISSING;
            case "REPORT_TOO_LARGE" -> ScanErrorCode.REPORT_TOO_LARGE;
            case "RULESET_INVALID" -> ScanErrorCode.RULESET_INVALID;
            case "ENGINE_CRASHED" -> ScanErrorCode.ENGINE_CRASHED;
            case "SERVICE_FAILURE" -> ScanErrorCode.SERVICE_FAILURE;
            case "SERVICE_BUSY" -> ScanErrorCode.SERVICE_BUSY;
            case "SERVICE_NOT_READY" -> ScanErrorCode.SERVICE_NOT_READY;
            case "RESOURCE_UNAVAILABLE" -> ScanErrorCode.RESOURCE_UNAVAILABLE;
            case "SCAN_TIMEOUT" -> ScanErrorCode.SEMGREP_TIMEOUT;
            default -> ScanErrorCode.INVALID_SCAN_RESPONSE;
        };
        throw new SemgrepScanException(failedCode);
    }

    private List<ScanFinding> mapFindings(List<SemgrepFindingResponse> responseFindings) {
        List<ScanFinding> findings = new ArrayList<>(responseFindings.size());
        for (SemgrepFindingResponse finding : responseFindings) {
            if (finding == null || isBlank(finding.ruleId()) || isBlank(finding.category())
                    || isBlank(finding.message()) || invalidPosition(finding.startLine())
                    || invalidPosition(finding.startColumn()) || invalidPosition(finding.endLine())
                    || invalidPosition(finding.endColumn())) {
                throw new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
            }
            ScanSeverity severity;
            try {
                severity = ScanSeverity.valueOf(finding.severity());
            } catch (RuntimeException exception) {
                throw new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
            }
            findings.add(new ScanFinding(finding.ruleId(), finding.category(), severity,
                    finding.message(), finding.reason(), finding.startLine(), finding.startColumn(),
                    finding.endLine(), finding.endColumn()));
        }
        return List.copyOf(findings);
    }

    private FunctionScanResult rejected(SemgrepCallResult call, List<ScanFinding> findings, ScanError error) {
        return complete(call, findings, ScanSummary.from(findings), ScanStatus.REJECTED, error);
    }

    private FunctionScanResult complete(SemgrepCallResult call, List<ScanFinding> findings,
                                        ScanSummary summary, ScanStatus status, ScanError error) {
        return new FunctionScanResult(FunctionScanResult.SCHEMA_VERSION, status, null,
                call.codeHash(), call.scannedAt(), call.response().scanId(),
                call.response().durationMs(), false, summary, findings, error);
    }

    private static boolean invalidPosition(Integer value) {
        return value != null && value < 1;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
