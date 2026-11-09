package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 函数脚本 Semgrep 同步扫描配置。配置只在应用启动时绑定，不支持运行期刷新。
 */
@Data
@Component
@ConfigurationProperties(prefix = "function-security.semgrep")
public class SemgrepScanProperties {

    private boolean enabled = false;

    private String baseUrl;

    private int connectTimeoutMs = 2000;

    private int readTimeoutMs = 25000;

    private String scanPath = "/api/v1/scan";

    private String mediaType = "application/json; charset=utf-8";

    private int maxCodeBytes = 256 * 1024;

    private int maxResponseBytes = 4 * 1024 * 1024;

    private int maxFindings = 200;

    private String requestIdPattern = "[A-Za-z0-9_.:-]{1,128}";

    private String hashPattern = "[0-9a-f]{64}";

    private List<String> requiredResponseFields = List.of(
            "scanId", "durationMs", "cached", "findingsTruncated", "findings");

    public String requiredBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("function-security.semgrep.base-url must be configured when enabled");
        }
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalStateException("Semgrep timeouts must be positive");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void validate() {
        if (maxCodeBytes <= 0 || maxResponseBytes <= 0 || maxFindings <= 0) {
            throw new IllegalStateException("function-security.semgrep 的 max-code-bytes/max-response-bytes/max-findings 必须为正数");
        }
        if (scanPath == null || !scanPath.startsWith("/")) {
            throw new IllegalStateException("function-security.semgrep.scan-path 必须以 / 开头");
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalStateException("function-security.semgrep.media-type 不能为空");
        }
        if (requiredResponseFields == null || requiredResponseFields.isEmpty()) {
            throw new IllegalStateException("function-security.semgrep.required-response-fields 不能为空");
        }
    }
}
