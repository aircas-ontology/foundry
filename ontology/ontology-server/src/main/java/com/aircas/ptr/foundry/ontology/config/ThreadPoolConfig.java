package com.aircas.ptr.foundry.ontology.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ThreadPoolConfig {

    private final TaskExecutorProperties properties;

    private final ScriptSecurityProperties scriptSecurityProperties;

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(Math.max(properties.getMaxPoolSize(), properties.getCorePoolSize()));
        // queueCapacity > 0 → 有界 LinkedBlockingQueue
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        return executor;
    }

    /**
     * 行为算子代码安全检测专用线程池。
     * <p>
     * 独立小池 + 有界队列 + AbortPolicy：扫描是攻击者可控输入的解析过程，
     * 极端情况下可能卡住线程，独立池可把影响范围限制在扫描自身，不牵连通用 taskExecutor。
     * 队列满或池满时提交会抛 RejectedExecutionException，由扫描器按 fail-closed 转为拒绝。
     */
    @Bean(name = "scriptSecurityExecutor")
    public ThreadPoolTaskExecutor scriptSecurityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int core = Math.max(1, scriptSecurityProperties.getScanExecutorCorePoolSize());
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(Math.max(core, scriptSecurityProperties.getScanExecutorMaxPoolSize()));
        executor.setQueueCapacity(Math.max(1, scriptSecurityProperties.getScanExecutorQueueCapacity()));
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("script-security-");
        // 守护线程：解析若卡死且中断无效，也不至于阻止 JVM 退出
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
