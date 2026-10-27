package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.config.RateLimitProperties;
import com.aircas.ptr.foundry.ontology.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis + Lua 的分布式限流实现。
 * <p>
 * 核心 Lua 脚本在 Redis 中原子执行：滑动窗口计 TPS + 并发槽位检查，一次调用完成判定。
 * <ul>
 *   <li>TPS：sorted set，score = 毫秒时间戳，窗口 = 1 秒</li>
 *   <li>并发：hash，field = requestId，执行完 DECR 释放</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true")
public class RedisRateLimitService implements RateLimitService {

    private static final String LUA_SCRIPT =
            "local mode          = ARGV[1] " +
            "local tpsLimit      = tonumber(ARGV[2]) " +
            "local maxConc       = tonumber(ARGV[3]) " +
            "local nowMs         = tonumber(ARGV[4]) " +
            "local requestId     = ARGV[5] " +
            "local ttlSec        = tonumber(ARGV[6]) " +
            "local tpsKey        = KEYS[1] " +
            "local concKey       = KEYS[2] " +
            "local windowStart   = nowMs - 1000 " +
            "if mode == 'acquire' then " +
            "  redis.call('zremrangebyscore', tpsKey, '-inf', windowStart) " +
            "  local tpsCount    = redis.call('zcard', tpsKey) " +
            "  if tpsLimit > 0 and tpsCount >= tpsLimit then " +
            "    return -1 " +
            "  end " +
            "  local concCount   = redis.call('hlen', concKey) " +
            "  if maxConc > 0 and concCount >= maxConc then " +
            "    return -2 " +
            "  end " +
            "  redis.call('zadd', tpsKey, nowMs, requestId) " +
            "  redis.call('expire', tpsKey, 2) " +
            "  redis.call('hincrby', concKey, requestId, 1) " +
            "  redis.call('expire', concKey, ttlSec) " +
            "  return 1 " +
            "else " +
            "  local exists = redis.call('hexists', concKey, requestId) " +
            "  if exists == 1 then " +
            "    redis.call('hincrby', concKey, requestId, -1) " +
            "    local val = tonumber(redis.call('hget', concKey, requestId)) " +
            "    if val ~= nil and val <= 0 then " +
            "      redis.call('hdel', concKey, requestId) " +
            "    end " +
            "  end " +
            "  return redis.call('hlen', concKey) " +
            "end";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final ThreadLocal<String> lastRequestId = new ThreadLocal<>();
    private DefaultRedisScript<Long> redisScript;

    public RedisRateLimitService(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptText(LUA_SCRIPT);
        this.redisScript.setResultType(Long.class);
    }

    @Override
    public boolean acquire(String functionApi, int tpsLimit, int maxConcurrency) {
        if (tpsLimit <= 0 && maxConcurrency <= 0) {
            return true;
        }
        String requestId = UUID.randomUUID().toString();
        lastRequestId.set(requestId);
        try {
            Long result = executeScript(functionApi, "acquire", tpsLimit, maxConcurrency, requestId);
            if (result == null) {
                return true;
            }
            if (result == -1L) {
                log.warn("算子 {} TPS 超限（limit={}），拒绝", functionApi, tpsLimit);
                return false;
            }
            if (result == -2L) {
                log.warn("算子 {} 并发超限（limit={}），拒绝", functionApi, maxConcurrency);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("限流检查异常，放行请求: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void release(String functionApi) {
        String requestId = lastRequestId.get();
        if (requestId == null) {
            return;
        }
        lastRequestId.remove();
        try {
            executeScript(functionApi, "release", 0, 0, requestId);
        } catch (Exception e) {
            log.warn("限流释放异常: {}", e.getMessage());
        }
    }

    private Long executeScript(String functionApi, String mode,
                               int tpsLimit, int maxConcurrency, String requestId) {
        String prefix = properties.getKeyPrefix();
        List<String> keys = List.of(prefix + "tps:" + functionApi,
                                    prefix + "conc:" + functionApi);
        return redisTemplate.execute(
                redisScript,
                keys,
                mode,
                String.valueOf(tpsLimit),
                String.valueOf(maxConcurrency),
                String.valueOf(System.currentTimeMillis()),
                requestId,
                String.valueOf(properties.getConcurrencyTtl()));
    }
}
