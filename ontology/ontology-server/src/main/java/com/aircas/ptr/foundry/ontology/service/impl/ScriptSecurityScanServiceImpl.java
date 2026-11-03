package com.aircas.ptr.foundry.ontology.service.impl;

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
import com.aircas.ptr.foundry.ontology.model.enums.ScanErrorCodeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanPurposeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanSeverityEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanSkipReasonEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanStatusEnum;
import com.aircas.ptr.foundry.ontology.service.ScriptSecurityScanService;
import com.aircas.ptr.foundry.ontology.utils.ScriptTypeUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 扫描跳过策略、语言映射和 fail-closed 业务判定实现。 */
@Service
public class ScriptSecurityScanServiceImpl implements ScriptSecurityScanService {

    private final SemgrepScanProperties properties;
    private final SemgrepScanClient client;

    public ScriptSecurityScanServiceImpl(SemgrepScanProperties properties, SemgrepScanClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public FunctionScanResult scan(FunctionTypeEnum functionType, FunctionLanguageEnum language,
                                   String code, ScanPurposeEnum purpose) {
        if (functionType != FunctionTypeEnum.CUSTOMIZE) {
            return FunctionScanResult.skipped(ScanSkipReasonEnum.NON_SCRIPT_OPERATOR);
        }
        if (!properties.isEnabled()) {
            return FunctionScanResult.skipped(ScanSkipReasonEnum.FEATURE_DISABLED);
        }

        SemgrepCallResult call = client.scan(ScriptTypeUtil.toSemgrep(language), code, purpose);
        SemgrepScanResponse response = call.response();

        // 顺序固定：HTTP/协议已由 client 处理；随后依次处理 error、截断、severity。
        ScanError mappedError = mapResponseError(response.error());
        if (mappedError != null) {
            return rejected(call, mapFindings(response.findings()), mappedError);
        }
        if (response.findingsTruncated()) {
            throw new SemgrepScanException(ScanErrorCodeEnum.FINDINGS_TRUNCATED);
        }

        List<ScanFinding> findings = mapFindings(response.findings());
        ScanSummary summary = ScanSummary.from(findings);
        ScanStatusEnum status = summary.high() > 0 ? ScanStatusEnum.REJECTED : ScanStatusEnum.PASSED;
        return complete(call, findings, summary, status, null);
    }

    private ScanError mapResponseError(SemgrepErrorResponse error) {
        if (error == null) {
            return null;
        }
        String code = error.code();
        if (ScanErrorCodeEnum.PARSE_FAILED.name().equals(code)) {
            return ScanError.of(ScanErrorCodeEnum.PARSE_FAILED);
        }
        if (ScanErrorCodeEnum.PARTIAL_PARSE.name().equals(code)) {
            return ScanError.of(ScanErrorCodeEnum.PARTIAL_PARSE);
        }
        ScanErrorCodeEnum failedCode = switch (code) {
            case "INVALID_REQUEST" -> ScanErrorCodeEnum.INVALID_REQUEST;
            case "UNSUPPORTED_LANGUAGE" -> ScanErrorCodeEnum.UNSUPPORTED_LANGUAGE;
            case "CODE_HASH_MISMATCH" -> ScanErrorCodeEnum.CODE_HASH_MISMATCH;
            case "TARGET_SKIPPED" -> ScanErrorCodeEnum.TARGET_SKIPPED;
            case "REPORT_INVALID" -> ScanErrorCodeEnum.REPORT_INVALID;
            case "REPORT_MISSING" -> ScanErrorCodeEnum.REPORT_MISSING;
            case "REPORT_TOO_LARGE" -> ScanErrorCodeEnum.REPORT_TOO_LARGE;
            case "RULESET_INVALID" -> ScanErrorCodeEnum.RULESET_INVALID;
            case "ENGINE_CRASHED" -> ScanErrorCodeEnum.ENGINE_CRASHED;
            case "SERVICE_FAILURE" -> ScanErrorCodeEnum.SERVICE_FAILURE;
            case "SERVICE_BUSY" -> ScanErrorCodeEnum.SERVICE_BUSY;
            case "SERVICE_NOT_READY" -> ScanErrorCodeEnum.SERVICE_NOT_READY;
            case "RESOURCE_UNAVAILABLE" -> ScanErrorCodeEnum.RESOURCE_UNAVAILABLE;
            case "SCAN_TIMEOUT" -> ScanErrorCodeEnum.SEMGREP_TIMEOUT;
            default -> ScanErrorCodeEnum.INVALID_SCAN_RESPONSE;
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
                throw new SemgrepScanException(ScanErrorCodeEnum.INVALID_SCAN_RESPONSE);
            }
            ScanSeverityEnum severity;
            try {
                severity = ScanSeverityEnum.valueOf(finding.severity());
            } catch (RuntimeException exception) {
                throw new SemgrepScanException(ScanErrorCodeEnum.INVALID_SCAN_RESPONSE);
            }
            findings.add(new ScanFinding(finding.ruleId(), finding.category(), severity,
                    finding.message(), finding.reason(), finding.startLine(), finding.startColumn(),
                    finding.endLine(), finding.endColumn()));
        }
        return List.copyOf(findings);
    }

    private FunctionScanResult rejected(SemgrepCallResult call, List<ScanFinding> findings, ScanError error) {
        return complete(call, findings, ScanSummary.from(findings), ScanStatusEnum.REJECTED, error);
    }

    private FunctionScanResult complete(SemgrepCallResult call, List<ScanFinding> findings,
                                        ScanSummary summary, ScanStatusEnum status, ScanError error) {
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
