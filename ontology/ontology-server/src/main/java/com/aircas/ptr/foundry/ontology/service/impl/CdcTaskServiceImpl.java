package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.model.dto.ConnectorDTO;
import com.aircas.ptr.foundry.ontology.service.CdcTaskService;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Debezium CDC 任务管理服务
 * 使用 Result<T> 统一封装返回结果
 */
@Slf4j
@Service
public class CdcTaskServiceImpl implements CdcTaskService {

    @Value("${cdc.connect.rest.url:http://192.168.9.11:18083}")
    private String connectRestUrl;

    private static String tableOntologyMeta = "ontology.ontology_meta";

    private static String tableOntologySpace = "ontology.ontology_space";


    private static final String TASK_1_NAME = "entity-datasource-cdc";
    private static final String TASK_2_NAME = "ontology-space-cdc";
    private static final String TASK_3_NAME = "ontology-meta-cdc";

    /**
     * 初始化所有 CDC 任务
     * 有则更新，无则创建
     *
     */
    public void initializeCdcTasks() {
        log.info("========== 开始初始化 CDC 任务 ==========");

        // 1. 先获取当前已存在的连接器列表
        List<String> existingConnectors = getConnectorList();
        log.info("当前已存在的连接器: {}", existingConnectors);
        Set<String> connectorSet = new HashSet<>(existingConnectors);
        Map<String, Object> entityDatasourceCdcConfig = createEntityDatasourceCdcConfig(TASK_1_NAME);
        if (!connectorSet.contains(TASK_1_NAME)) {
            ConnectorDTO res = createConnector(TASK_1_NAME, entityDatasourceCdcConfig);
            log.info("创建连接器 {} 成功,{}", TASK_1_NAME, JSONObject.toJSONString(res));
        } else {
            updateConnector(TASK_1_NAME, entityDatasourceCdcConfig);
        }
        Map<String, Object> ontologyMetaTaskConfig = createOntologyTaskConfig(tableOntologyMeta);
        if (!connectorSet.contains(TASK_2_NAME)) {
            ConnectorDTO res = createConnector(TASK_2_NAME, ontologyMetaTaskConfig);
            log.info("创建连接器 {} 成功,{}", TASK_2_NAME, JSONObject.toJSONString(res));
        } else {
            updateConnector(TASK_2_NAME, ontologyMetaTaskConfig);
        }
        Map<String, Object> ontologySpaceTaskConfig = createOntologyTaskConfig(tableOntologySpace);
        if (!connectorSet.contains(TASK_3_NAME)) {
            ConnectorDTO res = createConnector(TASK_3_NAME, ontologySpaceTaskConfig);
            log.info("创建连接器 {} 成功,{}", TASK_3_NAME, JSONObject.toJSONString(res));
        } else {
            updateConnector(TASK_3_NAME, ontologyMetaTaskConfig);
        }

        log.info("========== CDC 任务初始化完成 ==========");
    }


    /**
     * 创建 entity-datasource-all-tables-cdc 连接器
     *
     * @return Result<String> - 创建结果
     */
    private Map<String, Object> createEntityDatasourceCdcConfig(String connectorName) {
        log.info("开始创建 CDC 连接器: {}", connectorName);

        // 构建连接器配置
        Map<String, Object> config = new HashMap<>();
        config.put("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        config.put("database.hostname", "192.168.9.11");
        config.put("database.port", "45432");
        config.put("database.user", "iecas");
        config.put("database.password", "iecas123");
        config.put("database.dbname", "entity_datasource");
        config.put("database.server.name", "entity_datasource_server");
        config.put("topic.prefix", "entity_datasource_server");
        config.put("plugin.name", "pgoutput");
        config.put("publication.name", "entity_datasource_publication");
        config.put("publication.autocreate.mode", "filtered");
        config.put("include.schema.changes", "true");
        config.put("snapshot.mode", "initial");
        config.put("slot.name", "entity_datasource_slot");
        config.put("poll.interval.ms", "1000");
        config.put("key.converter", "org.apache.kafka.connect.json.JsonConverter");
        config.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
        config.put("key.converter.schemas.enable", "false");
        config.put("value.converter.schemas.enable", "false");
        return config;
    }

    /**
     * 创建 ontology-space-cdc 连接器
     *
     * @return Result<String> - 创建结果
     */
    private Map<String, Object> createOntologyTaskConfig(String tableName) {

        // 构建连接器配置
        Map<String, Object> config = new HashMap<>();
        config.put("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        config.put("database.hostname", "192.168.9.11");
        config.put("database.port", "45432");
        config.put("database.user", "iecas");
        config.put("database.password", "iecas123");
        config.put("database.dbname", "postgres");
        config.put("topic.prefix", "ontology_server");
        config.put("table.include.list", tableName);
        config.put("schema.include.list", "ontology");
        config.put("plugin.name", "pgoutput");
        config.put("publication.name", "ontology_space_publication");
        config.put("publication.autocreate.mode", "filtered");
        config.put("include.schema.changes", "true");
        config.put("snapshot.mode", "initial");
        config.put("slot.name", "ontology_space_slot");
        config.put("key.converter", "org.apache.kafka.connect.json.JsonConverter");
        config.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
        config.put("key.converter.schemas.enable", "false");
        config.put("value.converter.schemas.enable", "false");
        config.put("tombstones.on.delete", "false");
        config.put("max.batch.size", "2048");
        config.put("max.queue.size", "8192");
        config.put("poll.interval.ms", "1000");
        return config;
    }

    /**
     * 通用的创建连接器方法
     *
     * @param connectorName 连接器名称
     * @param config        连接器配置
     * @return Result<String> - 操作结果
     */
    private ConnectorDTO createConnector(String connectorName, Map<String, Object> config) {
        String url = connectRestUrl + "/connectors";

        // 构建请求体 (Kafka Connect REST API 格式)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", connectorName);
        requestBody.put("config", config);

        // 发送 POST 请求
        TypeReference<ConnectorDTO> typeRef = new TypeReference<ConnectorDTO>() {
        };
        ConnectorDTO response = HttpUtil.postJson(url, null, requestBody, typeRef);
        log.info("成功创建 CDC 连接器: {}, 响应数据: {}", connectorName, response);
        return response;
    }

    /**
     * 通用的更新连接器方法（如果连接器不存在会返回 404，从而触发创建逻辑）
     *
     * @param connectorName 连接器名称
     * @param config        连接器配置
     */
    private void updateConnector(String connectorName, Map<String, Object> config) {
        String url = connectRestUrl + "/connectors/" + connectorName + "/config";

        // 发送 PUT 请求
        ConnectorDTO connectorDTO = new ConnectorDTO();
        connectorDTO.setConfig(config);
        connectorDTO.setName(connectorName);
        TypeReference<ConnectorDTO> typeRef = new TypeReference<ConnectorDTO>() {
        };
        ConnectorDTO response = HttpUtil.putJson(url, null, JSONObject.toJSONString(connectorDTO), typeRef);
        log.info("成功更新 CDC 连接器: {}, 响应数据: {}", connectorName, response);
    }

    /**
     * 获取所有已存在的连接器列表
     *
     * @return List<String> - 连接器名称列表
     */
    public List<String> getConnectorList() {
        String url = connectRestUrl + "/connectors";
        try {
            TypeReference<List<String>> typeRef = new TypeReference<List<String>>() {
            };
            List<String> connectors = HttpUtil.get(url, null, typeRef);
            log.info("获取连接器列表成功: {}", connectors);
            return connectors;
        } catch (Exception e) {
            log.error("获取连接器列表失败", e);
            return new ArrayList<>();
        }
    }

}
