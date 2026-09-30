package com.aircas.ptr.foundry.ontology.script.security.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 脚本安全扫描结果。属于服务内部模型，不直接对外暴露。
 */
@Data
@Builder
public class ScriptScanResult {

    private boolean passed;

    private SecuritySeverity highestSeverity;

    private List<SecurityViolation> violations;

    /** 一行摘要，用于 BusinessException 消息，不含脚本正文 */
    private String summary;

    private long scanDurationMs;

    public static ScriptScanResult passed() {
        return passed(0L);
    }

    public static ScriptScanResult passed(long durationMs) {
        return ScriptScanResult.builder()
                .passed(true)
                .highestSeverity(null)
                .violations(Collections.emptyList())
                .summary("未命中安全规则")
                .scanDurationMs(durationMs)
                .build();
    }

    /**
     * fail-closed：扫描器自身异常或脚本超出限制时，直接拒绝。
     */
    public static ScriptScanResult reject(String summary) {
        return ScriptScanResult.builder()
                .passed(false)
                .highestSeverity(SecuritySeverity.DANGER)
                .violations(Collections.singletonList(
                        SecurityViolation.of("scanner.reject", SecuritySeverity.DANGER,
                                SecurityCategory.SCANNER, -1, -1, summary, null)))
                .summary(summary)
                .build();
    }

    /**
     * 按严重程度汇总。
     *
     * @param blockOnViolation DANGER 是否拦截（运维应急开关，默认 true）
     * @param blockOnWarn      WARN 是否升级为拦截（默认 true）
     */
    public static ScriptScanResult of(List<SecurityViolation> violations,
                                      boolean blockOnViolation,
                                      boolean blockOnWarn,
                                      long durationMs) {
        List<SecurityViolation> safe = violations == null ? new ArrayList<>() : new ArrayList<>(violations);
        boolean hasDanger = safe.stream().anyMatch(v -> v.getSeverity() == SecuritySeverity.DANGER);
        boolean hasWarn = safe.stream().anyMatch(v -> v.getSeverity() == SecuritySeverity.WARN);
        boolean blocked = (hasDanger && blockOnViolation) || (hasWarn && blockOnWarn);
        SecuritySeverity highest = hasDanger ? SecuritySeverity.DANGER : (hasWarn ? SecuritySeverity.WARN : null);

        String summary = buildSummary(safe);
        return ScriptScanResult.builder()
                .passed(!blocked)
                .highestSeverity(highest)
                .violations(safe)
                .summary(summary)
                .scanDurationMs(durationMs)
                .build();
    }

    private static String buildSummary(List<SecurityViolation> violations) {
        if (violations.isEmpty()) {
            return "未命中安全规则";
        }
        long danger = violations.stream().filter(v -> v.getSeverity() == SecuritySeverity.DANGER).count();
        long warn = violations.stream().filter(v -> v.getSeverity() == SecuritySeverity.WARN).count();
        StringBuilder sb = new StringBuilder("命中 ").append(violations.size()).append(" 项");
        if (danger > 0) {
            sb.append("（DANGER×").append(danger).append("）");
        }
        if (warn > 0) {
            sb.append("（WARN×").append(warn).append("）");
        }
        sb.append("：").append(violations.get(0).getRuleId());
        if (violations.get(0).getLineNumber() > 0) {
            sb.append(" @L").append(violations.get(0).getLineNumber());
        }
        return sb.toString();
    }
}
