package com.aircas.ptr.foundry.ontology.model.enums;

/** 本体服务对外暴露的稳定扫描错误码及默认 HTTP 映射。 */
public enum ScanErrorCode {
    INVALID_REQUEST(400, "安全扫描请求不合法", false),
    UNSUPPORTED_LANGUAGE(400, "不支持该脚本语言的安全扫描", false),
    CODE_HASH_MISMATCH(400, "脚本内容校验失败", false),
    SECURITY_SCAN_REJECTED(422, "安全扫描未通过，已阻止保存", false),
    PARSE_FAILED(422, "脚本解析失败，请检查语法后重试", false),
    PARTIAL_PARSE(422, "脚本存在无法完整解析的代码，请检查语法后重试", false),
    SCRIPT_VALIDATION_FAILED(422, "脚本语法或编译校验失败", false),
    SEMGREP_ENDPOINT_NOT_FOUND(502, "安全扫描服务接口不存在", false),
    TARGET_SKIPPED(502, "安全扫描未处理目标脚本", false),
    REPORT_INVALID(502, "安全扫描报告异常，已阻止保存", false),
    REPORT_MISSING(502, "安全扫描报告异常，已阻止保存", false),
    REPORT_TOO_LARGE(502, "安全扫描报告异常，已阻止保存", false),
    RULESET_INVALID(502, "安全扫描规则集异常", false),
    ENGINE_CRASHED(502, "安全扫描引擎异常，请稍后重试", true),
    SERVICE_FAILURE(502, "安全扫描服务返回异常", true),
    INVALID_SCAN_RESPONSE(502, "安全扫描服务响应无效", false),
    FINDINGS_TRUNCATED(502, "安全扫描结果不完整，已阻止保存", false),
    SERVICE_BUSY(503, "安全扫描服务繁忙，请稍后重试", true),
    SERVICE_NOT_READY(503, "安全扫描服务尚未就绪", true),
    RESOURCE_UNAVAILABLE(503, "安全扫描资源暂时不可用", true),
    SEMGREP_UNREACHABLE(503, "无法连接安全扫描服务", true),
    SEMGREP_TIMEOUT(504, "安全扫描超时，请稍后重试", true);

    private final int httpStatus;
    private final String message;
    private final boolean retryable;

    ScanErrorCode(int httpStatus, String message, boolean retryable) {
        this.httpStatus = httpStatus;
        this.message = message;
        this.retryable = retryable;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }

    public boolean retryable() {
        return retryable;
    }

    public String topLevelMessage() {
        return message + "（" + name() + "）";
    }
}
