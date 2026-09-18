package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.EntityDatasourceColumnDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityDatasourceDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityDatasourceSchemaChangeEventDTO;
import com.aircas.ptr.foundry.ontology.model.enums.DatasourceEventTypeEnum;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.mq.producer.RabbitMQProducer;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TableMetadataServiceImpl implements TableMetadataService {

    @Resource
    private TableMetadataMapper tableMetadataMapper;


    @Resource
    private OntologySpaceMapper spaceMapper;


    @Resource
    private RabbitMQProducer rmqProducer;


    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    private ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public List<TableColumnDescVO> getColumns(Integer spaceId, String datasourceId) {

        var ontologySpace = spaceMapper.selectById(spaceId);
        PreconditionUtils.checkNotNull(ontologySpace, "无效的本体空间id", HttpStatus.BAD_REQUEST);

        return tableMetadataMapper.queryColumnMetadata(ontologySpace.getApiName(), datasourceId).stream().map(v ->
                TableColumnDescVO.builder()
                        .columnName(v.getColumnName())
                        .description(v.getDescription())
                        .isPrimaryKey(v.getIsPrimaryKey())
                        .type(v.getType())
                        .build()
        ).collect(Collectors.toList());
    }


    @Override
    public Page<DatasourceTableVO> getTables(Integer spaceId,
                                             String keyword,
                                             Integer pageNum,
                                             Integer pageSize) {

        var ontologySpace = spaceMapper.selectById(spaceId);
        PreconditionUtils.checkNotNull(ontologySpace, "无效的本体空间id", HttpStatus.BAD_REQUEST);

        var schemaName = ontologySpace.getApiName();
        var total = tableMetadataMapper.countTables(schemaName, keyword);
        var page = new Page<DatasourceTableVO>(pageNum, pageSize, total);
        if (total == 0) {
            return page;
        }
        var offset = (pageNum - 1) * pageSize;
        var records = tableMetadataMapper.listTablesPage(schemaName, keyword, pageSize, offset).stream()
                .map(v -> DatasourceTableVO.builder()
                        .schemaName(schemaName)
                        .description(v.getDescription())
                        .tableName(v.getTableName())
                        .build())
                .collect(Collectors.toList());
        page.setRecords(records);
        return page;
    }

    @Override
    @SneakyThrows
    public void dropDataSource(String schemaName, String dataSourceId) {
        tableMetadataMapper.dropTable(schemaName, dataSourceId);
        var event = EntityDatasourceSchemaChangeEventDTO.builder()
                .datasource(Lists.newArrayList(EntityDatasourceDTO.builder()
                        .tableName(dataSourceId)
                        .build()))
                .type(DatasourceEventTypeEnum.DROP_TABLE)
                .build();
        rmqProducer.sendMessage(routingKey, jsonMapper.writeValueAsString(event));
    }

    @Override
    @SneakyThrows
    public void dropColumns(String schemaName, String dataSourceId, List<String> columns) {
        tableMetadataMapper.dropColumns(schemaName, dataSourceId, columns);
        var event = EntityDatasourceSchemaChangeEventDTO.builder()
                .datasource(Lists.newArrayList(EntityDatasourceDTO.builder()
                        .tableName(dataSourceId)
                        .columns(columns.stream().map(v -> EntityDatasourceColumnDTO.builder().columnName(v).build()).collect(Collectors.toList()))
                        .build()))
                .type(DatasourceEventTypeEnum.DELETE_COLUMN)
                .build();
        rmqProducer.sendMessage(routingKey, jsonMapper.writeValueAsString(event));
    }

    @Override
    @SneakyThrows
    public void createSchema(String schemaName) {
        tableMetadataMapper.createSchema(schemaName);
    }

    @Override
    public void initSpaceSchema(String schemaName) {
        tableMetadataMapper.initSpaceSchema(schemaName);
    }

}