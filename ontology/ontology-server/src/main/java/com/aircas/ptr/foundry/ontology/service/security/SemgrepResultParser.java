package com.aircas.ptr.foundry.ontology.service.security;

import com.aircas.ptr.foundry.ontology.config.ScriptSecurityProperties;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptScanFindingDTO;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptScanResultDTO;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptRiskCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptRiskSeverityEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptScanStatusEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SemgrepResultParser {

    private final ObjectMapper objectMapper;
    private final ScriptSecurityProperties properties;

    public ScriptScanResultDTO parse(SemgrepProcessResult processResult) {
        ScriptScanResultDTO failed = validateProcessResult(processResult);
        if (failed != null) {
            return failed;
        }
        try {
            SemgrepOutput output = objectMapper.readValue(processResult.getStdout(), SemgrepOutput.class);
            if (output.errors != null && !output.errors.isEmpty()) {
                return failed(processResult, "扫描引擎报告错误");
            }

            List<ScriptScanFindingDTO> findings = new ArrayList<>();
            for (SemgrepFinding finding : output.results == null ? Collections.<SemgrepFinding>emptyList() : output.results) {
                if (!isValid(finding)) {
                    return failed(processResult, "扫描结果缺少必要字段");
                }
                findings.add(ScriptScanFindingDTO.builder()
                        .ruleId(finding.checkId)
                        .category(ScriptRiskCategoryEnum.fromValue(
                                finding.extra.metadata == null ? null : finding.extra.metadata.category))
                        .severity(ScriptRiskSeverityEnum.fromValue(finding.extra.severity))
                        .message(finding.extra.message)
                        .line(finding.start.line)
                        .column(finding.start.column)
                        .build());
                if (findings.get(findings.size() - 1).getSeverity() == ScriptRiskSeverityEnum.UNKNOWN) {
                    return failed(processResult, "扫描结果包含未知严重级别");
                }
            }
            findings.sort(Comparator
                    .comparingInt((ScriptScanFindingDTO value) -> severityRank(value.getSeverity()))
                    .thenComparing(value -> value.getLine() == null ? Integer.MAX_VALUE : value.getLine())
                    .thenComparing(ScriptScanFindingDTO::getRuleId));

            boolean rejected = findings.stream()
                    .anyMatch(finding -> properties.getBlockSeverities().contains(finding.getSeverity()));
            boolean truncated = findings.size() > properties.getMaxFindings();
            List<ScriptScanFindingDTO> returnedFindings = truncated
                    ? List.copyOf(findings.subList(0, properties.getMaxFindings()))
                    : List.copyOf(findings);
            return ScriptScanResultDTO.builder()
                    .status(rejected ? ScriptScanStatusEnum.REJECTED : ScriptScanStatusEnum.PASSED)
                    .engine("SEMGREP")
                    .engineVersion(properties.getSemgrepImage())
                    .ruleSetVersion(properties.getRuleSetVersion())
                    .durationMillis(processResult.getDurationMillis())
                    .findings(returnedFindings)
                    .truncated(truncated)
                    .build();
        } catch (Exception exception) {
            return failed(processResult, "扫描结果无法解析");
        }
    }

    private ScriptScanResultDTO validateProcessResult(SemgrepProcessResult result) {
        if (result.isTimedOut()) {
            return failed(result, "扫描超时");
        }
        if (result.isOverloaded()) {
            return failed(result, "扫描服务繁忙");
        }
        if (StringUtils.isNotBlank(result.getFailureSummary())) {
            return failed(result, result.getFailureSummary());
        }
        if (result.isOutputTruncated()) {
            return failed(result, "扫描输出超过限制");
        }
        if (result.getExitCode() == null || result.getExitCode() != 0) {
            return failed(result, "扫描引擎异常退出");
        }
        if (StringUtils.isBlank(result.getStdout())) {
            return failed(result, "扫描结果为空");
        }
        return null;
    }

    private boolean isValid(SemgrepFinding finding) {
        return finding != null
                && StringUtils.isNotBlank(finding.checkId)
                && finding.start != null
                && finding.start.line != null
                && finding.start.column != null
                && finding.extra != null
                && StringUtils.isNoneBlank(finding.extra.message, finding.extra.severity);
    }

    private int severityRank(ScriptRiskSeverityEnum severity) {
        return switch (severity) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFO -> 2;
            case UNKNOWN -> 3;
        };
    }

    private ScriptScanResultDTO failed(SemgrepProcessResult processResult, String summary) {
        return ScriptScanResultDTO.builder()
                .status(ScriptScanStatusEnum.FAILED)
                .engine("SEMGREP")
                .engineVersion(properties.getSemgrepImage())
                .ruleSetVersion(properties.getRuleSetVersion())
                .durationMillis(processResult.getDurationMillis())
                .errorSummary(summary)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SemgrepOutput {
        public List<SemgrepFinding> results;
        public List<Object> errors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SemgrepFinding {
        @JsonProperty("check_id")
        public String checkId;
        public Position start;
        public Extra extra;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Position {
        public Integer line;
        @JsonProperty("col")
        public Integer column;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Extra {
        public String message;
        public String severity;
        public Metadata metadata;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Metadata {
        public String category;
    }
}
