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
     * 行为树并行节点专用：队列为 0，线程数到上限后在调用线程执行。
     * <p>并行分支等的是自己的子任务，队列堆积只会加长整棵树的响应时间；
     * 达到上限时退回调用线程执行，既不拒绝任务，也不会出现分支相互等待线程的死锁。</p>
     */
    @Bean(name = "behaviorTreeBranchExecutor")
    public ThreadPoolTaskExecutor behaviorTreeBranchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(0);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(0);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("ontology-bt-branch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }
}
