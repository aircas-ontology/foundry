package com.aircas.ptr.foundry.ontology.mq.consumer;
import com.aircas.ptr.foundry.common.util.JsonUtil;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.TypeReference;
import com.aircas.ptr.foundry.ontology.model.dto.cdc.DebeziumEnvelopeDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.service.MetaCdcJobService;
import com.aircas.ptr.foundry.ontology.service.PropertyCdcJobService;
import com.aircas.ptr.foundry.ontology.service.SpaceCdcJobService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
@Component
public class OntologyListener {
    @Autowired
    MetaCdcJobService metaCdcJobService;
    @Autowired
    SpaceCdcJobService spaceCdcJobService;
    @Autowired
    PropertyCdcJobService propertyCdcJobService;


    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-meta}", groupId = "${cdc.consumer-group:cdc-monitor-group}")
    public void metaCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        DebeziumEnvelopeDTO<OntologyMeta> ontologyMetaDTODebeziumEnvelopeDTO = JsonUtil.parseObject(record.value(), new TypeReference<>() {
        }, JSONReader.Feature.SupportSmartMatch);
        // op=r 为 Debezium 快照读（initial snapshot），语义等同新增文档
        if(Objects.equals(ontologyMetaDTODebeziumEnvelopeDTO.getOp(), "c") || Objects.equals(ontologyMetaDTODebeziumEnvelopeDTO.getOp(), "r")){
            metaCdcJobService.handleCreateCdc(ontologyMetaDTODebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(ontologyMetaDTODebeziumEnvelopeDTO.getOp(), "u")){
            metaCdcJobService.handleUpdateCdc(ontologyMetaDTODebeziumEnvelopeDTO.getBefore(),ontologyMetaDTODebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(ontologyMetaDTODebeziumEnvelopeDTO.getOp(), "d")){
            metaCdcJobService.handleDeleteCdc(ontologyMetaDTODebeziumEnvelopeDTO.getBefore());
        }
    }
    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-space}", groupId = "${cdc.consumer-group:cdc-monitor-group}")
    public void spaceCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        DebeziumEnvelopeDTO<OntologySpace> ontologySpaceDebeziumEnvelopeDTO = JsonUtil.parseObject(record.value(), new TypeReference<>() {
        }, JSONReader.Feature.SupportSmartMatch);
        // op=r 为 Debezium 快照读（initial snapshot），语义等同新增文档
        if(Objects.equals(ontologySpaceDebeziumEnvelopeDTO.getOp(), "c") || Objects.equals(ontologySpaceDebeziumEnvelopeDTO.getOp(), "r")){
            spaceCdcJobService.handleCreateCdc(ontologySpaceDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(ontologySpaceDebeziumEnvelopeDTO.getOp(), "u")){
            spaceCdcJobService.handleUpdateCdc(ontologySpaceDebeziumEnvelopeDTO.getBefore(),ontologySpaceDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(ontologySpaceDebeziumEnvelopeDTO.getOp(), "d")){
            spaceCdcJobService.handleDeleteCdc(ontologySpaceDebeziumEnvelopeDTO.getBefore());
        }
    }
    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-property}", groupId = "${cdc.consumer-group:cdc-monitor-group}")
    public void propertyCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        DebeziumEnvelopeDTO<OntologyProperty> ontologyPropertyDebeziumEnvelopeDTO = JsonUtil.parseObject(record.value(), new TypeReference<>() {
        }, JSONReader.Feature.SupportSmartMatch);
        // op=r 为 Debezium 快照读（initial snapshot），语义等同新增文档
        if(Objects.equals(ontologyPropertyDebeziumEnvelopeDTO.getOp(), "c") || Objects.equals(ontologyPropertyDebeziumEnvelopeDTO.getOp(), "r")){
            propertyCdcJobService.handleCreateCdc(ontologyPropertyDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(ontologyPropertyDebeziumEnvelopeDTO.getOp(), "u")){
            propertyCdcJobService.handleUpdateCdc(ontologyPropertyDebeziumEnvelopeDTO.getBefore(),ontologyPropertyDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(ontologyPropertyDebeziumEnvelopeDTO.getOp(), "d")){
            propertyCdcJobService.handleDeleteCdc(ontologyPropertyDebeziumEnvelopeDTO.getBefore());
        }
    }
    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-link-group}", groupId = "${cdc.consumer-group:cdc-monitor-group}")
    public void linkGroupCdc(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        log.info("Partition: {}", record.partition());
        log.info("Offset: {}", record.offset());
        log.info("topic:{}",record.topic());
    }
}
