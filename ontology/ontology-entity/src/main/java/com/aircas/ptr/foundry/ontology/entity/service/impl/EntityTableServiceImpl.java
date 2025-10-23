package com.aircas.ptr.foundry.ontology.entity.service.impl;

import com.aircas.ptr.foundry.common.constant.CountTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.ontology.common.param.*;
import com.aircas.ptr.foundry.ontology.common.vo.EntityDetailVO;
import com.aircas.ptr.foundry.ontology.common.vo.EntityVO;
import com.aircas.ptr.foundry.ontology.common.vo.PropertyVO;
import com.aircas.ptr.foundry.ontology.entity.converter.ParamDtoConverter;
import com.aircas.ptr.foundry.ontology.entity.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.entity.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.entity.model.dto.FieldDTO;
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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
    public List<EntityDetailVO> queryEntityDetail(EntityDetailQueryParam param) {
        List<EntityDetailVO> result = Lists.newArrayList();
        var primaryTableName = param.getPrimaryTableName();
        var primaryKeyColumn = tableMapper.queryPrimaryKeyColumnName(primaryTableName);
        var primaryData = tableMapper.selectDataByPrimaryKey(primaryTableName, primaryKeyColumn, param.getPrimaryKeyValue());
        //获取主表实体数据
        var primaryDatasourceId = propertyService.getOne(new LambdaQueryWrapper<EntityPropertyPO>()
                .eq(EntityPropertyPO::getTableName, primaryTableName).last("limit 1"))
                .getDatasourceId();
        var primaryEntityDetail = EntityDetailVO.builder()
                .tableName(primaryTableName)
                .datasourceId(primaryDatasourceId)
                .propertyName(primaryData.get(0).keySet().stream().collect(Collectors.toList()))
                .propertyValues(primaryData.stream().map(row -> row.values().stream().collect(Collectors.toList())).collect(Collectors.toList()))
                .build();
        result.add(primaryEntityDetail);
        //获取关联表实体数据
        var tableMapping = propertyMappingService.list(new LambdaQueryWrapper<EntityPropertyMappingPO>().eq(EntityPropertyMappingPO::getEntityTable, primaryTableName));
        if (CollectionUtils.isEmpty(tableMapping) || CollectionUtils.isEmpty(param.getAssociateDatasource())) {
            return result;
        }
        var dsMap = param.getAssociateDatasource().stream().collect(Collectors.toMap(v -> v.getDatasourceId(), v -> v.getCount()));
        tableMapping.forEach(mapping -> {
            var tableName = mapping.getEntityPropertyTable();
            var datasourceId = propertyService.getOne(new LambdaQueryWrapper<EntityPropertyPO>()
                    .eq(EntityPropertyPO::getTableName, tableName).last("limit 1"))
                    .getDatasourceId();
            if (Objects.isNull(dsMap.get(datasourceId))) {
                return;
            }
            var orderBy = tableMapper.queryPrimaryKeyColumnName(tableName);
            var data = tableMapper.selectByJoinTable(mapping.getEntityTable(),
                    mapping.getEntityTableKey(),
                    primaryKeyColumn,
                    param.getPrimaryKeyValue(),
                    tableName,
                    mapping.getEntityPropertyTableKey(),
                    orderBy,
                    dsMap.get(datasourceId).equals(CountTypeEnum.ONE) ? 1 : null);
            var entityDetail = EntityDetailVO.builder()
                    .tableName(tableName)
                    .datasourceId(datasourceId)
                    .propertyName(data.get(0).keySet().stream().collect(Collectors.toList()))
                    .propertyValues(data.stream().map(row -> row.values().stream().collect(Collectors.toList())).collect(Collectors.toList()))
                    .build();
            result.add(entityDetail);
        });
        return result;
    }


    @Override
    public Page<EntityVO> queryEntitiesByTableName(String tableName, Integer pageNum, Integer pageSize) {
        var records = tableMapper.pageSelect(tableName, pageSize, (pageNum - 1) * pageSize);
        var total = tableMapper.selectCount(tableName);
        var entityVOS = records.stream().map(row -> {
            var properties = row.entrySet().stream().map(entry -> PropertyVO.builder()
                    .propertyName(entry.getKey())
                    .propertyValue(entry.getValue())
                    .build()).collect(Collectors.toList());
            return EntityVO.builder().tableName(tableName).properties(properties).build();
        }).collect(Collectors.toList());
        Page<EntityVO> result = new Page<>();
        result.setRecords(entityVOS)
                .setSize(pageSize)
                .setCurrent(pageNum)
                .setTotal(total);
        return result;
    }

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
    public void createColumns(EntityColumnCreateParam param) {
        var primaryTableName = param.getPrimaryTableName();
        var entityProperties = propertyService.list(new LambdaQueryWrapper<EntityPropertyPO>().eq(EntityPropertyPO::getTableName, primaryTableName));
        //新数据源属性插入
        var newDS = param.getNewDatasource();
        if (CollectionUtils.isNotEmpty(newDS)) {
            // 创建实体其他属性表,插入其他属性表元数据
            newDS.stream().forEach(ds -> {
                // 创建实体其他属性表
                createTableAndRelation(ds, primaryTableName, entityProperties);
                //获取其他数据源数据，并插入实体属性表
                insertRecords(ds);
            });
        }
        //旧数据源属性插入
        var existDS = param.getExistDatasourceColumns();
        if (CollectionUtils.isNotEmpty(existDS)) {

            var list = propertyMappingService.list(new LambdaQueryWrapper<EntityPropertyMappingPO>().eq(EntityPropertyMappingPO::getEntityTable, primaryTableName))
                    .stream().map(v -> v.getEntityPropertyTable())
                    .collect(Collectors.toList());
            list.add(primaryTableName);
            var entityPropertyMap = propertyService.list(new LambdaQueryWrapper<EntityPropertyPO>().in(EntityPropertyPO::getTableName, list));

            var dsTableMap = entityPropertyMap.stream().collect(Collectors.toMap(v -> v.getDatasourceId(), v -> v.getTableName(), (v1, v2) -> v1));
            var existMap = existDS.stream().collect(Collectors.groupingBy(v -> v.getDatasourceId()));

            existMap.entrySet().forEach(entry -> {
                var datasourceId = entry.getKey();
                var tableName = dsTableMap.get(datasourceId);
                //插入entity property表
                var entityPropertyPOS = entry.getValue().stream().<EntityPropertyPO>map(v -> {
                    return EntityPropertyPO.builder()
                            .tableName(tableName)
                            .tableColumnName(v.getColumnName())
                            .datasourceColumnName(v.getDatasourceColumnName())
                            .datasourceId(v.getDatasourceId())
                            .status(Status.ENABLE.getValue())
                            .build();
                }).collect(Collectors.toList());
                propertyService.saveBatch(entityPropertyPOS);
                //增加列
                tableMapper.createColumns(entry.getValue().stream().map(v -> FieldDTO.builder()
                        .tableName(tableName)
                        .fieldType(v.getColumnType())
                        .fieldName(v.getColumnName())
                        .fieldComment(v.getDescription())
                        .isNullable(true)
                        .build())
                        .collect(Collectors.toList()));
                //更新列数据
                var columnMap = entry.getValue().stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v.getColumnName()));
                var dsPK = dataObjectMapper.queryPrimaryKeyColumnName(datasourceId);
                var tablePK = tableMapper.queryPrimaryKeyColumnName(tableName);
                columnMap.put(dsPK, tablePK);
                var datasourceRows = dataObjectMapper.queryTableDataByColumns(datasourceId, columnMap, "");
                tableMapper.updateByPrimaryKey(tableName, tablePK, datasourceRows);
            });


        }

    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void createEntities(EntityCreateParam entityCreateParam) {
        var primaryDataSource = entityCreateParam.getPrimaryDataSource();
        List<EntityDataSourceParam> associateDataSources = CollectionUtils.isEmpty(entityCreateParam.getAssociateDataSources()) ?
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
                .displayName(node.getDisplayName())
                .isDeleted(node.getIsDeleted()).build())
                .collect(Collectors.toList());
        nodeRepository.batchSave(nodesToInsert);

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
        relationRepository.batchSave(relationsToInsert);
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

    private void createTables(EntityDataSourceParam primaryDataSource, List<EntityDataSourceParam> associateDataSources) {
        // 创建主实体表
        var primaryTableName = primaryDataSource.getColumnParamList().get(0).getTableName();
        var fields = primaryDataSource.getColumnParamList().stream().map(v -> ParamDtoConverter.convert(v)).collect(Collectors.toList());

        tableMapper.createTable(TableCreateDTO.builder().fields(fields).tableName(primaryTableName).build());

        // 插入主实体元数据表
        var primaryProperty = fields.stream().map(v -> ParamDtoConverter.convertToEntityProperty(v)).collect(Collectors.toList());
        propertyService.saveBatch(primaryProperty);

        // 创建实体其他属性表,插入其他属性表元数据
        associateDataSources.stream().forEach(ds -> {
            // 创建实体其他属性表
            createTableAndRelation(ds, primaryTableName, primaryProperty);
        });
    }

    private void createTableAndRelation(EntityDataSourceParam ds, String primaryTableName, List<EntityPropertyPO> columnParams) {
        // 创建实体其他属性表
        var fieldList = ds.getColumnParamList().stream().map(v -> ParamDtoConverter.convert(v)).collect(Collectors.toList());
        tableMapper.createTable(TableCreateDTO.builder().fields(fieldList).tableName(fieldList.get(0).getTableName()).build());
        // 插入其他属性表元数据
        var entityPropertyList = fieldList.stream().map(v -> ParamDtoConverter.convertToEntityProperty(v)).collect(Collectors.toList());
        propertyService.saveBatch(entityPropertyList);
        // 插入实体表关联健
        var param = ds.getColumnParamList().stream().filter(v -> v.getIsAssociateKey()).findFirst().get();
        var entityTableKey = columnParams.stream()
                .filter(v -> v.getDatasourceColumnName().equals(param.getAssociateDatasourceColumnName()))
                .findFirst().get().getTableColumnName();
        propertyMappingService.save(EntityPropertyMappingPO.builder()
                .entityTable(primaryTableName)
                .entityPropertyTable(fieldList.get(0).getTableName())
                .entityTableKey(entityTableKey)
                .entityPropertyTableKey(param.getColumnName())
                .build());
    }

    private void insertRecords(EntityDataSourceParam ds) {
        //获取其他数据源数据，并插入实体属性表
        var tableName = ds.getColumnParamList().get(0).getTableName();
        var datasourceId = ds.getColumnParamList().get(0).getDatasourceId();
        var filedMap = ds.getColumnParamList().stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v.getColumnName()));
        var orderBy = dataObjectMapper.checkIdColumnExists(datasourceId);
        List<Map<String, Object>> data = dataObjectMapper.queryTableDataByColumns(datasourceId, filedMap, orderBy);
        tableMapper.batchInsertRows(tableName, data);
    }


    private void insertEntityTables(EntityDataSourceParam primaryDataSource, List<EntityDataSourceParam> associateDataSources) {
        //获取主数据源数据
        var primaryTableName = primaryDataSource.getColumnParamList().get(0).getTableName();
        var primaryDatasource = primaryDataSource.getColumnParamList().get(0).getDatasourceId();
        var primaryFieldMap = primaryDataSource.getColumnParamList().stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v.getColumnName()));
        var primaryData = dataObjectMapper.queryTableDataByColumns(primaryDatasource, primaryFieldMap, dataObjectMapper.checkIdColumnExists(primaryDatasource));
        //插入主实体表
        tableMapper.batchInsertRows(primaryTableName, primaryData);
        associateDataSources.forEach(ds -> insertRecords(ds));
        //写入实体节点数据
        var primaryKey = primaryDataSource.getColumnParamList().stream().filter(v -> v.getIsPrimaryKey()).findFirst().get().getColumnName();
        var titleKey = primaryDataSource.getColumnParamList().stream().filter(v -> v.getIsTitleKey()).findFirst().get().getColumnName();
        var nodes = primaryData.stream().map(data -> EntityNode.builder()
                .tableName(primaryTableName)
                .isDeleted(false)
                .createTime(new Date())
                .updateTime(new Date())
                .primaryKey(data.get(primaryKey))
                .displayName(String.valueOf(data.get(titleKey)))
                .build())
                .collect(Collectors.toList());

        //改成批量插入
        nodeRepository.batchSave(nodes);
    }
}
