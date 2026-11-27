package com.aircas.ptr.foundry.ontology.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 统一打开 Spring 定时任务。
 * <p>
 * 不放在 {@code CdcKafkaConsumerConfig} 里：那边的 {@code @ConditionalOnProperty} 一关，
 * 与本模块所有 {@code @Scheduled} 任务（如 action_handle_log 的 PENDING 超时清扫）一起失效，
 * CDC 开关不应决定这些任务是否运行。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
