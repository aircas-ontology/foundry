package com.aircas.ptr.foundry.ontology.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CDC 消费侧配置：打开 Spring 的定时任务，供 {@code CdcLakeTopicWatcher} 周期性检查数据湖是否新增了 topic。
 * <p>
 * 没有选择覆盖 {@code kafkaListenerContainerFactory}：Spring Kafka 的 topicPattern 运行期刷新依赖
 * {@code ContainerProperties.setTopicPatternRefreshMs}，该方法在 spring-kafka 3.3.7 上是否存在未经确认，
 * 而容器重新启动一次本来就会重新解析 pattern，效果相同，所以改为探测到新 topic 后只重启湖监听容器，
 * Boot 默认装配的工厂（retry / 错误处理 / 反序列化 / group.id / auto-offset-reset）一概不动。
 */
@Configuration
@ConditionalOnProperty(prefix = "foundry.integration.cdc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class CdcKafkaConsumerConfig {
}
