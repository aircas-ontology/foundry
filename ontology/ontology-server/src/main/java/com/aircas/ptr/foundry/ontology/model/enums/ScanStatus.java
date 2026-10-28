package com.aircas.ptr.foundry.ontology.model.enums;

/**
 * 脚本安全扫描状态。
 * <p>{@link #FAILED} 仅用于当次错误响应和日志，不允许持久化到 function 表。</p>
 */
public enum ScanStatus {
    PASSED,
    REJECTED,
    FAILED,
    SKIPPED
}
