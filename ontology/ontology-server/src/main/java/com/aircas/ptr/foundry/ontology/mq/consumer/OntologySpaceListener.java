package com.aircas.ptr.foundry.ontology.mq.consumer;
import com.aircas.ptr.foundry.ontology.model.dto.CDCEventDTO;
import com.alibaba.fastjson.JSONObject;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OntologySpaceListener {

    @KafkaListener(topics = "${cdc.ontology-meta}", groupId = "cdc-monitor-group")
    public void metaCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        log.info("Partition: {}", record.partition());
        log.info("Offset: {}", record.offset());
    }
    @KafkaListener(topics = "${cdc.ontology-space}", groupId = "cdc-monitor-group")
    public void spaceCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        log.info("Partition: {}", record.partition());
        log.info("Offset: {}", record.offset());
    }
}
