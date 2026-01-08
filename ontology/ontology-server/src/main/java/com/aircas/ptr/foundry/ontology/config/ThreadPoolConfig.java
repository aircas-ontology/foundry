package com.aircas.ptr.foundry.ontology.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 设置核心线程数
        executor.setCorePoolSize(10);

        // 设置最大线程数
        executor.setMaxPoolSize(50);

        // 设置队列容量
        executor.setQueueCapacity(100);

        // 设置线程活跃时间（秒）
        executor.setKeepAliveSeconds(60);

        // 设置线程前缀
        executor.setThreadNamePrefix("ontology-thread-");

        // 设置拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 初始化
        executor.initialize();

        return executor;
    }
}