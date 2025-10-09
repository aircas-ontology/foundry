package com.aircas.ptr.foundry.ontology.entity.service.impl;

import com.aircas.ptr.foundry.ontology.common.param.DataSourceParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityCopyParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.entity.converter.ParamDtoConverter;
import com.aircas.ptr.foundry.ontology.entity.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.entity.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.entity.model.dto.TableCreateDTO;
import com.aircas.ptr.foundry.ontology.entity.model.po.EntityPropertyMappingPO;
import com.aircas.ptr.foundry.ontology.entity.model.po.EntityPropertyPO;
import com.aircas.ptr.foundry.ontology.entity.repository.arangodb.EntityNodeRepository;
import com.aircas.ptr.foundry.ontology.entity.repository.arangodb.EntityRelationRepository;
import com.aircas.ptr.foundry.ontology.entity.repository.mapper.datalake.DataObjectMapper;
import com.aircas.ptr.foundry.ontology.entity.repository.mapper.main.EntityTableMapper;
import com.aircas.ptr.foundry.ontology.entity.service.EntityPropertyMappingService;
import com.aircas.ptr.foundry.ontology.entity.service.EntityPropertyService;
import com.aircas.ptr.foundry.ontology.entity.service.EntityTableService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntityTableServiceImpl extends ServiceImpl<EntityTableMapper, Object> implements EntityTableService {

    private final EntityPropertyMappingService propertyMappingService;

    private final EntityPropertyService propertyService;

    private final EntityTableMapper tableMapper;

    private final DataObjectMapper dataObjectMapper;

    private final EntityNodeRepository nodeRepository;

    private final EntityRelationRepository relationRepository;


    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteEntitiesByTableName(String entityTableName) {
        //删除实体属性表、实体关联表、实体表
        var tableNames = propertyMappingService.list(new LambdaQueryWrapper<EntityPropertyMappingPO>().eq(EntityPropertyMappingPO::getEntityTable, entityTableName))
                .stream().map(v -> v.getEntityPropertyTable()).collect(Collectors.toList());
        propertyMappingService.remove(new LambdaQueryWrapper<EntityPropertyMappingPO>().eq(EntityPropertyMappingPO::getEntityTable, entityTableName));
        tableNames.add(entityTableName);
        propertyService.remove(new LambdaQueryWrapper<EntityPropertyPO>().in(EntityPropertyPO::getTableName, tableNames));
        tableNames.forEach(table -> tableMapper.dropTable(table));
        //删除实体节点、实体关联关系
        deleteNodesByTableName(entityTableName);
    }

    @Override
    public void deleteNodesByTableName(String entityTableName) {
        var nodes = nodeRepository.findByTableName(entityTableName);
        var relations = relationRepository.findByFromIn(nodes);
        relations.addAll(relationRepository.findByToIn(nodes));
        nodeRepository.deleteByIds(nodes.stream().map(v -> v.getArangoId()).collect(Collectors.toList()));
        relationRepository.deleteByIds(relations.stream().map(v -> v.getArangoId()).collect(Collectors.toList()));
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void createEntities(EntityCreateParam entityCreateParam) {
        var primaryDataSource = entityCreateParam.getPrimaryDataSource();
        List<DataSourceParam> associateDataSources = CollectionUtils.isEmpty(entityCreateParam.getAssociateDataSources()) ?
                Lists.newArrayList() : entityCreateParam.getAssociateDataSources();

        // 创建实体表和实体属性表
        this.createTables(primaryDataSource, associateDataSources);
        // 获取数据源数据,写入数据源数据到实体表和属性表,写入实体节点数据
        // todo 改成异步执行,失败重试确保实体最终插入
        this.insertEntityTables(primaryDataSource, associateDataSources);

    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void copyEntities(EntityCopyParam entityCopyParam) {
        var srcTable = entityCopyParam.getSourceTableName();
        var newTable = entityCopyParam.getNewTableName();
        //copy主实体表
        this.copyTable(srcTable, newTable);
        //copy 关联的其他属性表
        var tableMapping = propertyMappingService.list(new LambdaQueryWrapper<EntityPropertyMappingPO>().eq(EntityPropertyMappingPO::getEntityTable, srcTable));
        tableMapping.forEach(v -> {
            var propertyTable = v.getEntityPropertyTable();
            var newPropertyTable = newTable + propertyTable.substring(propertyTable.indexOf("_"));
            var mappingPO = EntityPropertyMappingPO.builder()
                    .entityTableKey(v.getEntityTableKey())
                    .entityTable(newTable)
                    .entityPropertyTableKey(v.getEntityPropertyTableKey())
                    .entityPropertyTable(newPropertyTable)
                    .status(v.getStatus())
                    .build();
            propertyMappingService.save(mappingPO);
            this.copyTable(propertyTable, newPropertyTable);
        });
        //copy实体节点和实体关系
        this.copyNodesAndRelations(srcTable, newTable);
    }

    private void copyNodesAndRelations(String srcTableName, String newTableName) {
        // 查询所有 tableName=srcTableName 的 EntityNode 对象
        var nodesToCopy = nodeRepository.findByTableName(srcTableName);
        // 复制这些对象并更新 tableName 为 newTableName
        var nodesToInsert = nodesToCopy.stream().map(node -> EntityNode.builder().tableName(newTableName)
                .createTime(new Date())
                .updateTime(new Date())
                .primaryKey(node.getPrimaryKey())
                .isDeleted(node.getIsDeleted()).build())
                .collect(Collectors.toList());
        nodeRepository.saveAll(nodesToInsert);

        var newNodeMap = nodesToInsert.stream().collect(Collectors.toMap(EntityNode::getPrimaryKey, node -> node));
        // 查询与这些节点相关的边
        var relationsToCopy = relationRepository.findByFromIn(nodesToCopy);
        relationsToCopy.addAll(relationRepository.findByToIn(nodesToCopy));
        // 复制这些边并更新 from 和 to 字段
        var relationsToInsert = relationsToCopy.stream()
                .map(relation -> EntityRelation.builder()
                        .type(relation.getType())
                        .description(relation.getDescription())
                        .createTime(new Date())
                        .updateTime(new Date())
                        .isDeleted(relation.getIsDeleted())
                        .from(Optional.ofNullable(newNodeMap.get(relation.getFrom().getPrimaryKey())).orElse(relation.getFrom()))
                        .to(Optional.ofNullable(newNodeMap.get(relation.getTo().getPrimaryKey())).orElse(relation.getTo()))
                        .build()
                ).collect(Collectors.toList());
        relationRepository.saveAll(relationsToInsert);
    }

    private void copyTable(String srcTable, String newTable) {
        var srcProps = propertyService.list(new LambdaQueryWrapper<EntityPropertyPO>().eq(EntityPropertyPO::getTableName, srcTable));
        var newProps = srcProps.stream().<EntityPropertyPO>map(v -> EntityPropertyPO.builder()
                .status(v.getStatus())
                .datasourceId(v.getDatasourceId())
                .datasourceColumnName(v.getDatasourceColumnName())
                .tableColumnName(v.getTableColumnName())
                .tableName(newTable)
                .build()).collect(Collectors.toList());
        propertyService.saveBatch(newProps);
        tableMapper.copyTable(srcTable, newTable);
    }

    private void createTables(DataSourceParam primaryDataSource, List<DataSourceParam> associateDataSources) {
        // 创建主实体表
        var primaryTableName = primaryDataSource.getColumnParamList().get(0).getTableName();
        var fields = primaryDataSource.getColumnParamList().stream().map(v -> ParamDtoConverter.convert(v)).collect(Collectors.toList());

        var otherDS = associateDataSources.stream().flatMap(list -> list.getColumnParamList().stream()
                .filter(v -> !v.getIsPrimaryKey() && !v.getIsAssociateKey()))
                .collect(Collectors.toList());
        var otherFields = otherDS.stream().map(v -> ParamDtoConverter.convert(v).setTableName(primaryTableName)).collect(Collectors.toList());
        fields.addAll(otherFields);
        tableMapper.createTable(TableCreateDTO.builder().fields(fields).tableName(primaryTableName).build());

        // 插入主实体元数据表
        var primaryProperty = fields.stream().map(v -> ParamDtoConverter.convertToEntityProperty(v)).collect(Collectors.toList());
        propertyService.saveBatch(primaryProperty);

        // 创建实体其他属性表,插入其他属性表元数据
        associateDataSources.stream().forEach(ds -> {
            // 创建实体其他属性表
            var fieldList = ds.getColumnParamList().stream().map(v -> ParamDtoConverter.convert(v)).collect(Collectors.toList());
            tableMapper.createTable(TableCreateDTO.builder().fields(fieldList).tableName(fieldList.get(0).getTableName()).build());
            // 插入其他属性表元数据
            var entityPropertyList = fieldList.stream().map(v -> ParamDtoConverter.convertToEntityProperty(v)).collect(Collectors.toList());
            propertyService.saveBatch(entityPropertyList);
            // 插入实体表关联健
            var param = ds.getColumnParamList().stream().filter(v -> v.getIsAssociateKey()).findFirst().get();
            var entityTableKey = primaryDataSource.getColumnParamList().stream()
                    .filter(v -> v.getDatasourceColumnName().equals(param.getAssociateDatasourceColumnName()))
                    .findFirst().get().getColumnName();
            propertyMappingService.save(EntityPropertyMappingPO.builder()
                    .entityTable(primaryTableName)
                    .entityPropertyTable(fieldList.get(0).getTableName())
                    .entityTableKey(entityTableKey)
                    .entityPropertyTableKey(param.getColumnName())
                    .build());
        });
    }


    private void insertEntityTables(DataSourceParam primaryDataSource, List<DataSourceParam> associateDataSources) {
        //获取主数据源数据
        var primaryTableName = primaryDataSource.getColumnParamList().get(0).getTableName();
        var primaryDatasource = primaryDataSource.getColumnParamList().get(0).getDatasourceId();
        var primaryFieldMap = primaryDataSource.getColumnParamList().stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v.getColumnName()));
        var primaryData = dataObjectMapper.queryTableDataByColumn(primaryDatasource, primaryFieldMap, dataObjectMapper.checkIdColumnExists(primaryDatasource));

        associateDataSources.stream().forEach(ds -> {
            //获取其他数据源数据，并插入实体属性表
            var tableName = ds.getColumnParamList().get(0).getTableName();
            var datasourceId = ds.getColumnParamList().get(0).getDatasourceId();
            var filedMap = ds.getColumnParamList().stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v.getColumnName()));
            var orderBy = dataObjectMapper.checkIdColumnExists(datasourceId);
            List<Map<String, Object>> data = dataObjectMapper.queryTableDataByColumn(datasourceId, filedMap, orderBy);
            tableMapper.batchInsertRows(tableName, data);

            //抽取关联的实体表属性值（取最新数据），并整合到主实体表
            var associate = ds.getColumnParamList().stream().filter(v -> v.getIsAssociateKey()).findFirst().get();
            // 其他数据源关联健
            var associateColumn = associate.getColumnName();
            var firstDataPerGroup = data.stream()
                    .collect(Collectors.groupingBy(map -> map.get(associateColumn)))
                    .entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey(),
                            entry -> entry.getValue().get(0)));

            //查找关联的主表数据列
            var associateKey = associate.getAssociateDatasourceColumnName();
            var primaryAssociateColumn = primaryDataSource.getColumnParamList().stream().filter(v -> v.getDatasourceColumnName().equals(associateKey)).findFirst().get().getColumnName();
            //待整合的数据字段
            var populatedColumn = ds.getColumnParamList().stream().filter(v -> !v.getIsPrimaryKey() && !v.getIsAssociateKey()).map(v -> v.getColumnName()).collect(Collectors.toList());
            primaryData.forEach(d -> {
                var value = d.getOrDefault(primaryAssociateColumn, null);
                if (value != null && firstDataPerGroup.get(value) != null) {
                    var props = firstDataPerGroup.get(value);
                    populatedColumn.forEach(col -> d.put(col, props.get(col)));
                } else {
                    populatedColumn.forEach(col -> d.put(col, null));
                }
            });
        });
        //插入主实体表
        tableMapper.batchInsertRows(primaryTableName, primaryData);
        //写入实体节点数据
        var primaryKey = primaryDataSource.getColumnParamList().stream().filter(v -> v.getIsPrimaryKey()).findFirst().get().getColumnName();
        var nodes = primaryData.stream().map(data -> EntityNode.builder()
                .tableName(primaryTableName)
                .isDeleted(false)
                .createTime(new Date())
                .updateTime(new Date())
                .primaryKey(data.get(primaryKey))
                .build())
                .collect(Collectors.toList());
        nodeRepository.saveAll(nodes);
    }
}
