package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 算子限流配置属性。
 * <p>
 * 对应 application.yml 中 rate-limit.* 节点。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** 是否启用分布式限流（false 时注入 NoOp 实现，所有请求放行） */
    private boolean enabled = false;

    /** Redis key 统一前缀 */
    private String keyPrefix = "foundry:rl:";

    /** 并发槽位 hash 的 TTL（秒），防止 JVM 崩溃后槽位永不释放 */
    private long concurrencyTtl = 3600;
}
