package com.aircas.ptr.foundry.ontology.script.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条安全违规。属于服务内部模型，不直接对外暴露（对外需转换为 VO）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityViolation {

    /** 规则标识，如 call.runtime_exec */
    private String ruleId;

    private SecuritySeverity severity;

    private SecurityCategory category;

    private int lineNumber;

    private int columnNumber;

    /** 面向开发者的说明 */
    private String message;

    /**
     * 命中的源码片段。已截断，禁止写入完整脚本正文。
     */
    private String snippet;

    /**
     * 构造时统一截断 snippet，避免日志/响应携带过大内容（CODING_CONVENTIONS §13）。
     */
    public static SecurityViolation of(String ruleId, SecuritySeverity severity, SecurityCategory category,
                                       int lineNumber, int columnNumber, String message, String snippet) {
        return SecurityViolation.builder()
                .ruleId(ruleId)
                .severity(severity)
                .category(category)
                .lineNumber(lineNumber)
                .columnNumber(columnNumber)
                .message(message)
                .snippet(truncate(snippet))
                .build();
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        String singleLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() > 120 ? singleLine.substring(0, 120) + "..." : singleLine;
    }
}
