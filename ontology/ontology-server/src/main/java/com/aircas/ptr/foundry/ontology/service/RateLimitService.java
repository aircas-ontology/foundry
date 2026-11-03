package com.aircas.ptr.foundry.ontology.service;

/**
 * 分布式限流服务（跨实例）。
 * <p>
 * 基于 Redis + Lua 脚本实现原子性的 TPS 滑动窗口 + 并发槽位控制。
 * 调用方在执行函数前调用 {@link #acquire}，执行完毕后在 finally 中调用 {@link #release}。
 */
public interface RateLimitService {

    /**
     * 尝试获取一次执行许可（同时检查 TPS 和并发）。
     *
     * @param functionApi    函数 API 标识（限流粒度）
     * @param tpsLimit       每秒最大调用数
     * @param maxConcurrency 最大并发执行数
     * @return true=获取成功可执行；false=被限流拒绝
     */
    boolean acquire(String functionApi, int tpsLimit, int maxConcurrency);

    /**
     * 释放一次执行许可（递减并发计数）。
     * 必须在函数执行完毕后调用（通常在 finally 块中）。
     */
    void release(String functionApi);
}
