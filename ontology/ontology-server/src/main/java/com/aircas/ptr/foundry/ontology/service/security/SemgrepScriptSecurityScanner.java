package com.aircas.ptr.foundry.ontology.service.security;

import com.aircas.ptr.foundry.ontology.config.ScriptSecurityProperties;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptScanResultDTO;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptScanStatusEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptTypeEnum;
import com.aircas.ptr.foundry.ontology.service.ScriptSecurityScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemgrepScriptSecurityScanner implements ScriptSecurityScanner {

    private final ScriptSecurityProperties properties;
    private final TempSourceManager tempSourceManager;
    private final SemgrepProcessRunner processRunner;
    private final SemgrepResultParser resultParser;
    private final MeterRegistry meterRegistry;

    @Override
    public ScriptScanResultDTO scan(ScriptTypeEnum scriptType, String code) {
        long startedAt = System.nanoTime();
        String scanId = UUID.randomUUID().toString();
        if (!properties.isEnabled()) {
            ScriptScanResultDTO result = ScriptScanResultDTO.builder()
                    .status(properties.isFailClosed() ? ScriptScanStatusEnum.FAILED : ScriptScanStatusEnum.PASSED)
                    .engine("DISABLED")
                    .ruleSetVersion(properties.getRuleSetVersion())
                    .durationMillis(0L)
                    .errorSummary(properties.isFailClosed() ? "脚本安全检测未启用" : null)
                    .build();
            audit(scanId, scriptType, code, result);
            return result;
        }

        try {
            Path selectedRules = Path.of(properties.getRulesDir(), scriptType.getRulesDirectory());
            if (!Files.isDirectory(selectedRules) || !Files.isReadable(selectedRules)) {
                return auditAndReturn(scanId, scriptType, code, failed("脚本规则不可用", startedAt));
            }
            try (TempSourceManager.TempSource source = tempSourceManager.create(scriptType, code)) {
                SemgrepProcessResult processResult = processRunner.run(source.getDirectory(), scriptType, scanId);
                return auditAndReturn(scanId, scriptType, code, resultParser.parse(processResult));
            }
        } catch (IllegalArgumentException exception) {
            return auditAndReturn(scanId, scriptType, code, failed("脚本大小超过限制", startedAt));
        } catch (Exception exception) {
            return auditAndReturn(scanId, scriptType, code, failed("脚本安全检测失败", startedAt));
        }
    }

    private ScriptScanResultDTO failed(String summary, long startedAt) {
        return ScriptScanResultDTO.builder()
                .status(ScriptScanStatusEnum.FAILED)
                .engine("SEMGREP")
                .engineVersion(properties.getSemgrepImage())
                .ruleSetVersion(properties.getRuleSetVersion())
                .durationMillis((System.nanoTime() - startedAt) / 1_000_000)
                .errorSummary(summary)
                .build();
    }

    private ScriptScanResultDTO auditAndReturn(String scanId, ScriptTypeEnum type, String code,
                                               ScriptScanResultDTO result) {
        audit(scanId, type, code, result);
        return result;
    }

    private void audit(String scanId, ScriptTypeEnum type, String code, ScriptScanResultDTO result) {
        String ruleIds = result.getFindings().stream()
                .map(finding -> finding.getRuleId())
                .collect(Collectors.joining(","));
        log.info("scriptScan scanId={} scriptType={} codeSha256={} codeBytes={} ruleSetVersion={} status={} findings={} ruleIds={} durationMillis={}",
                scanId,
                type,
                sha256(code),
                code == null ? 0 : code.getBytes(StandardCharsets.UTF_8).length,
                result.getRuleSetVersion(),
                result.getStatus(),
                result.getFindings().size(),
                ruleIds,
                result.getDurationMillis());
        Counter.builder("script.security.scans")
                .description("Script security scan count")
                .tag("scriptType", type.name())
                .tag("status", result.getStatus().name())
                .register(meterRegistry)
                .increment();
        Timer.builder("script.security.duration")
                .description("Script security scan duration")
                .tag("scriptType", type.name())
                .tag("status", result.getStatus().name())
                .register(meterRegistry)
                .record(result.getDurationMillis() == null ? 0 : result.getDurationMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private String sha256(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((code == null ? "" : code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            return "unavailable";
        }
    }
}
