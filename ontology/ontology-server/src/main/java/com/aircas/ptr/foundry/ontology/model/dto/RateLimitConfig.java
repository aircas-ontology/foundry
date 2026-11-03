package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 算子调用与限流配置（JSON 快照，存入 function.rate_limit_config）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfig {

    /** 超时时间（毫秒） */
    private Integer timeoutMs;

    /** 重试次数（0 表示不重试） */
    private Integer retryCount;

    /** 重试间隔（毫秒） */
    private Integer retryIntervalMs;

    /** TPS 限流（每秒最大调用数） */
    private Integer tpsLimit;

    /** 最大并发数 */
    private Integer maxConcurrency;

    /** 是否启用（0 禁用 / 1 启用） */
    private Integer enabled;
}
