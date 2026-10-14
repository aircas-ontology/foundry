package com.aircas.ptr.foundry.ontology.mq.consumer;

import com.aircas.ptr.foundry.common.util.JsonUtil;
import com.aircas.ptr.foundry.ontology.model.dto.cdc.DebeziumEnvelopeDTO;
import com.aircas.ptr.foundry.ontology.service.EntityInstanceCdcJobService;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 数据湖库（entity_datasource）DML CDC 消费者：
 * 将 Debezium 捕获的表行变更同步到 ES ontology_instance 索引。
 * <p>
 * 消息结构：before/after 为动态列 Map（列名 -> 值），schema/表名取自 source.schema / source.table；
 * 主表/从表判定与具体同步逻辑由 {@link EntityInstanceCdcJobService} 完成。
 */
@Slf4j
@Component
public class EntityDatasourceDMLListener {

    @Autowired
    EntityInstanceCdcJobService entityInstanceCdcJobService;

    @KafkaListener(topicPattern = "entity_datasource_server\\..*", groupId = "${cdc.consumer-group:cdc-monitor-group}")
    public void consumeAllTables(ConsumerRecord<String, String> record) {
        log.info("Key: {}", record.key());
        log.info("Value: {}", record.value());
        log.info("Partition: {}", record.partition());
        log.info("Offset: {}", record.offset());

        DebeziumEnvelopeDTO<Map<String, Object>> envelope;
        try {
            envelope = JsonUtil.parseObject(record.value(), new TypeReference<>() {
            });
        } catch (Exception e) {
            log.error("数据湖CDC消息解析失败, topic={}", record.topic(), e);
            return;
        }
        // 心跳（__debezium-heartbeat）、schema history 等非 DML 消息无 op/source，直接忽略
        if (envelope == null || envelope.getOp() == null || envelope.getSource() == null) {
            return;
        }
        String schema = envelope.getSource().getSchema();
        String table = envelope.getSource().getTable();
        if (StringUtils.isEmpty(schema) || StringUtils.isEmpty(table)) {
            return;
        }
        String op = envelope.getOp();
        try {
            if (Objects.equals(op, "c") || Objects.equals(op, "r")) {
                // r = 连接器快照阶段读到的存量行，按创建处理
                entityInstanceCdcJobService.handleCreateCdc(schema, table, envelope.getAfter());
            } else if (Objects.equals(op, "u")) {
                // 注意：PG 默认 REPLICA IDENTITY 下 u 消息 before 为 null，服务层已兜底（按 after 主键 upsert）
                entityInstanceCdcJobService.handleUpdateCdc(schema, table, envelope.getBefore(), envelope.getAfter());
            } else if (Objects.equals(op, "d")) {
                entityInstanceCdcJobService.handleDeleteCdc(schema, table, envelope.getBefore());
            }
        } catch (Exception e) {
            log.error("数据湖CDC同步失败, op={}, table=[{}.{}], value={}", op, schema, table, record.value(), e);
        }
    }
}
