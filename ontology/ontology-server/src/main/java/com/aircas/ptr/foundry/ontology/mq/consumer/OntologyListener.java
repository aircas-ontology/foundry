package com.aircas.ptr.foundry.ontology.mq.consumer;
import com.aircas.ptr.foundry.ontology.service.CdcJobService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OntologyListener {
    @Autowired
    CdcJobService cdcJobService;

    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-meta}", groupId = "cdc-monitor-group")
    public void metaCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        log.info("Partition: {}", record.partition());
        log.info("Offset: {}", record.offset());
        log.info("topic:{}",record.topic());
    }
    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-space}", groupId = "cdc-monitor-group")
    public void spaceCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        log.info("Partition: {}", record.partition());
        log.info("Offset: {}", record.offset());
        log.info("topic:{}",record.topic());
    }
    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-property}", groupId = "cdc-monitor-group")
    public void propertyCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        log.info("Partition: {}", record.partition());
        log.info("Offset: {}", record.offset());
        log.info("topic:{}",record.topic());
    }
    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-link-group}", groupId = "cdc-monitor-group")
    public void linkGroupCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        log.info("Partition: {}", record.partition());
        log.info("Offset: {}", record.offset());
        log.info("topic:{}",record.topic());
    }
}
