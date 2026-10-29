package com.aircas.ptr.foundry.ontology.mq.consumer;
import com.aircas.ptr.foundry.common.util.JsonUtil;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.TypeReference;
import com.aircas.ptr.foundry.ontology.model.dto.cdc.DebeziumEnvelopeDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.service.LinkGroupCdcJobService;
import com.aircas.ptr.foundry.ontology.service.MetaCdcJobService;
import com.aircas.ptr.foundry.ontology.service.PropertyCdcJobService;
import com.aircas.ptr.foundry.ontology.service.SpaceCdcJobService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Objects;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "foundry.integration.cdc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OntologyListener {
    @Autowired
    MetaCdcJobService metaCdcJobService;
    @Autowired
    SpaceCdcJobService spaceCdcJobService;
    @Autowired
    PropertyCdcJobService propertyCdcJobService;
    @Autowired
    LinkGroupCdcJobService linkGroupCdcJobService;


    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-meta}", groupId = "${cdc.consumer-group:cdc-monitor-group}")
    public void metaCdc(ConsumerRecord<String, String> record) {
        log.debug("meta CDC message, key={}, value={}", record.key(), record.value());
        DebeziumEnvelopeDTO<OntologyMeta> ontologyMetaDTODebeziumEnvelopeDTO = JsonUtil.parseObject(record.value(), new TypeReference<>() {
        }, JSONReader.Feature.SupportSmartMatch);
        if (ontologyMetaDTODebeziumEnvelopeDTO == null || ontologyMetaDTODebeziumEnvelopeDTO.getOp() == null) {
            log.warn("meta CDC 消息解析为空或缺少 op 字段, key={}", record.key());
            return;
        }
        String op = ontologyMetaDTODebeziumEnvelopeDTO.getOp();
        log.info("meta CDC, op={}, id={}", op,
                ontologyMetaDTODebeziumEnvelopeDTO.getAfter() != null ? ontologyMetaDTODebeziumEnvelopeDTO.getAfter().getId() : null);
        // op=r 为 Debezium 快照读（initial snapshot），语义等同新增文档
        if(Objects.equals(op, "c") || Objects.equals(op, "r")){
            metaCdcJobService.handleCreateCdc(ontologyMetaDTODebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(op, "u")){
            metaCdcJobService.handleUpdateCdc(ontologyMetaDTODebeziumEnvelopeDTO.getBefore(),ontologyMetaDTODebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(op, "d")){
            metaCdcJobService.handleDeleteCdc(ontologyMetaDTODebeziumEnvelopeDTO.getBefore());
        }
    }
    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-space}", groupId = "${cdc.consumer-group:cdc-monitor-group}")
    public void spaceCdc(ConsumerRecord<String, String> record) {
        log.debug("space CDC message, key={}, value={}", record.key(), record.value());
        DebeziumEnvelopeDTO<OntologySpace> ontologySpaceDebeziumEnvelopeDTO = JsonUtil.parseObject(record.value(), new TypeReference<>() {
        }, JSONReader.Feature.SupportSmartMatch);
        if (ontologySpaceDebeziumEnvelopeDTO == null || ontologySpaceDebeziumEnvelopeDTO.getOp() == null) {
            log.warn("space CDC 消息解析为空或缺少 op 字段, key={}", record.key());
            return;
        }
        String op = ontologySpaceDebeziumEnvelopeDTO.getOp();
        log.info("space CDC, op={}, id={}", op,
                ontologySpaceDebeziumEnvelopeDTO.getAfter() != null ? ontologySpaceDebeziumEnvelopeDTO.getAfter().getId() : null);
        // op=r 为 Debezium 快照读（initial snapshot），语义等同新增文档
        if(Objects.equals(op, "c") || Objects.equals(op, "r")){
            spaceCdcJobService.handleCreateCdc(ontologySpaceDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(op, "u")){
            spaceCdcJobService.handleUpdateCdc(ontologySpaceDebeziumEnvelopeDTO.getBefore(),ontologySpaceDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(op, "d")){
            spaceCdcJobService.handleDeleteCdc(ontologySpaceDebeziumEnvelopeDTO.getBefore());
        }
    }
    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-property}", groupId = "${cdc.consumer-group:cdc-monitor-group}")
    public void propertyCdc(ConsumerRecord<String, String> record) {
        log.debug("property CDC message, key={}, value={}", record.key(), record.value());
        DebeziumEnvelopeDTO<OntologyProperty> ontologyPropertyDebeziumEnvelopeDTO = JsonUtil.parseObject(record.value(), new TypeReference<>() {
        }, JSONReader.Feature.SupportSmartMatch);
        if (ontologyPropertyDebeziumEnvelopeDTO == null || ontologyPropertyDebeziumEnvelopeDTO.getOp() == null) {
            log.warn("property CDC 消息解析为空或缺少 op 字段, key={}", record.key());
            return;
        }
        String op = ontologyPropertyDebeziumEnvelopeDTO.getOp();
        log.info("property CDC, op={}, id={}", op,
                ontologyPropertyDebeziumEnvelopeDTO.getAfter() != null ? ontologyPropertyDebeziumEnvelopeDTO.getAfter().getId() : null);
        // op=r 为 Debezium 快照读（initial snapshot），语义等同新增文档
        if(Objects.equals(op, "c") || Objects.equals(op, "r")){
            propertyCdcJobService.handleCreateCdc(ontologyPropertyDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(op, "u")){
            propertyCdcJobService.handleUpdateCdc(ontologyPropertyDebeziumEnvelopeDTO.getBefore(),ontologyPropertyDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(op, "d")){
            propertyCdcJobService.handleDeleteCdc(ontologyPropertyDebeziumEnvelopeDTO.getBefore());
        }
    }
    @KafkaListener(topics = "${cdc.ontology-topic-prefix}.${cdc.ontology-link-group}", groupId = "${cdc.consumer-group:cdc-monitor-group}")
    public void linkGroupCdc(ConsumerRecord<String, String> record) {
        log.debug("linkGroup CDC message, key={}, value={}", record.key(), record.value());
        DebeziumEnvelopeDTO<OntologyLinkGroup> ontologyLinkGroupDebeziumEnvelopeDTO = JsonUtil.parseObject(record.value(), new TypeReference<>() {
        }, JSONReader.Feature.SupportSmartMatch);
        if (ontologyLinkGroupDebeziumEnvelopeDTO == null || ontologyLinkGroupDebeziumEnvelopeDTO.getOp() == null) {
            log.warn("linkGroup CDC 消息解析为空或缺少 op 字段, key={}", record.key());
            return;
        }
        String op = ontologyLinkGroupDebeziumEnvelopeDTO.getOp();
        log.info("linkGroup CDC, op={}, id={}", op,
                ontologyLinkGroupDebeziumEnvelopeDTO.getAfter() != null ? ontologyLinkGroupDebeziumEnvelopeDTO.getAfter().getId() : null);
        // op=r 为 Debezium 快照读（initial snapshot），语义等同新增文档
        if(Objects.equals(op, "c") || Objects.equals(op, "r")){
            linkGroupCdcJobService.handleCreateCdc(ontologyLinkGroupDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(op, "u")){
            linkGroupCdcJobService.handleUpdateCdc(ontologyLinkGroupDebeziumEnvelopeDTO.getBefore(),ontologyLinkGroupDebeziumEnvelopeDTO.getAfter());
        }else if(Objects.equals(op, "d")){
            linkGroupCdcJobService.handleDeleteCdc(ontologyLinkGroupDebeziumEnvelopeDTO.getBefore());
        }
    }
}
