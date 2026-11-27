package com.aircas.ptr.foundry.ontology.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * CDC 消费侧配置占位。定时任务已改由无条件的 {@link SchedulingConfig} 统一打开，
 * 本模块的 {@code @Scheduled} 任务（如 action_handle_log 超时清扫）不再随 cdc 开关一起失效；
 * {@code CdcLakeTopicWatcher} 自身带同条件注解，cdc 关闭时该组件仍会被一并关掉。
 * <p>
 * 没有选择覆盖 {@code kafkaListenerContainerFactory}：Spring Kafka 的 topicPattern 运行期刷新依赖
 * {@code ContainerProperties.setTopicPatternRefreshMs}，该方法在 spring-kafka 3.3.7 上是否存在未经确认，
 * 而容器重新启动一次本来就会重新解析 pattern，效果相同，所以改为探测到新 topic 后只重启湖监听容器，
 * Boot 默认装配的工厂（retry / 错误处理 / 反序列化 / group.id / auto-offset-reset）一概不动。
 */
@Configuration
@ConditionalOnProperty(prefix = "foundry.integration.cdc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CdcKafkaConsumerConfig {
}
