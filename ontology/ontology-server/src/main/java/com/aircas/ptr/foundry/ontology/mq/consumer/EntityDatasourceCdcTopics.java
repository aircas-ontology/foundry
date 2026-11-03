package com.aircas.ptr.foundry.ontology.mq.consumer;

/**
 * 数据湖 CDC 增量监听的 topic 约定，供 {@code CdcLakeTopicWatcher} 复用，避免正则与容器 id 两处不一致。
 */
public interface EntityDatasourceCdcTopics {

    /** 湖监听容器 id（{@code @KafkaListener(id = ...)}），运行期重启该容器以重新解析 topicPattern */
    String LISTENER_ID = "entityDatasourceCdcListener";

    /** 湖 CDC topic 正则：{topic.prefix}.{schema}.{table}，前缀见 cdc.entity-datasource-prefix */
    String TOPIC_PATTERN = "entity_datasource_server\\..*";
}
