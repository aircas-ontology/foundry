package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.service.RateLimitService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 限流未启用时的空实现，所有请求直接放行。
 */
@Service
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpRateLimitService implements RateLimitService {

    @Override
    public boolean acquire(String functionApi, int tpsLimit, int maxConcurrency) {
        return true;
    }

    @Override
    public void release(String functionApi) {
    }
}
