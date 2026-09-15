package com.aircas.ptr.foundry.ontology.mq.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class EntityDatasourceDMLListener {

    @KafkaListener(topicPattern = "entity_datasource_server\\..*", groupId = "cdc-monitor-group")
    public void consumeAllTables(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        log.info("Partition: {}", record.partition());
        log.info("Offset: {}", record.offset());
    }
}
