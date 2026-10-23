package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    public String requiredBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("function-security.semgrep.base-url must be configured when enabled");
        }
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalStateException("Semgrep timeouts must be positive");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
