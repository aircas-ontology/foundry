package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityNodeExportDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyInstancesExportDTO;
import com.aircas.ptr.foundry.ontology.model.enums.*;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityNodeRepository;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityRelationRepository;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableFieldMappingMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionExecuteResultMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.aircas.ptr.foundry.ontology.converter.DataConverter.convert2DataType;

/**
 * todo 添加事务
 *
 * @className: EntityServiceImpl
 * @author: yangj
 * @date: 2025/4/8 18:40
 * @version: 1.0
 * @description:
 */
@Slf4j
@Service
public class EntityServiceImpl implements EntityService {

    @Resource
    private OntologyMetaMapper metaMapper;

    @Resource
    private OntologyLinkGroupMapper linkGroupMapper;

    @Resource
    private FunctionService functionService;

    @Lazy
    @Resource
    private OntologyActionService actionService;


    @Resource
    private OntologyPropertyMapper propertyMapper;

    @Resource
    private EntityRelationRepository relationRepository;

    @Resource
    private EntityNodeRepository nodeRepository;

    @Resource
    private TableFieldMappingMapper tableFieldMappingMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private TableMetadataMapper tableMetadataMapper;

    @Resource
    private FunctionExecuteResultMapper executeResultMapper;

    @Resource
    private TaskProcessor taskProcessor;

    private Configuration safeConfig = Configuration.builder().build().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);


    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private final ExpressionParser parser = new SpelExpressionParser();


    @Override
    public void createEntityRelations(String linkUniqueIdentifier) {
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, linkUniqueIdentifier));
        PreconditionUtils.checkArgument(link != null, "本体关系不存在");
        var existRelations = relationRepository.queryRelationsByLinkId(link.getUniqueIdentifier());
        var existRelationSet = existRelations.stream()
                .map(v -> v.getFrom().getId() + v.getTo().getId())
                .collect(Collectors.toSet());

        var fromNodes = nodeRepository.findByOntologyUniqIdentifier(link.getOntologyUniqueIdentifierFrom());
        var toNodes = nodeRepository.findByOntologyUniqIdentifier(link.getOntologyUniqueIdentifierTo());

        if (CollectionUtils.isNotEmpty(fromNodes) && CollectionUtils.isNotEmpty(toNodes)) {
            var relations = new ArrayList<EntityRelation>();
            var windows = new ArrayList<VisibilityWindow>();


            fromNodes.forEach(from ->
                    toNodes.forEach(to -> {
                                if (existRelationSet.contains(from.getId() + to.getId())) {
                                    return;
                                }
                                relations.add(EntityRelation.builder()
                                        .ontologyLinkId(link.getUniqueIdentifier())
                                        .from(from)
                                        .to(to)
                                        .status(Status.DELETE)
                                        .timeWindows(windows)
                                        .startTime(null)
                                        .endTime(null)
                                        .createTime(new Date())
                                        .updateTime(new Date())
                                        .type(link.getType())
                                        .name(link.getName())
                                        .build());
                            }
                    )
            );

            if (CollectionUtils.isNotEmpty(relations)) {
                relationRepository.batchSave(relations);
            }
        }
    }


    /**
     * 标题健需要和主键为同一个数据源
     *
     */
    @Override
    public void syncNodes(String ontologyUniqueIdentifier, String schemaName, String datasourceId, String primaryKeyColumnName, String titleKeyColumnName) {

        var existNodes = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier);
        var existNodesMap = existNodes.stream().collect(Collectors.toMap(v -> v.getPrimaryKey(), v -> v));

        List<Map<String, Object>> allRows = objectMapper.queryPrimaryKeyAndTitleKeyValue(schemaName, datasourceId, primaryKeyColumnName, titleKeyColumnName);
        List<EntityNode> nodes = Lists.newArrayList();

        allRows.stream().forEach(r -> {
            var primaryKeyValue = r.get(primaryKeyColumnName);
            if (primaryKeyValue instanceof Integer) {
                primaryKeyValue = Long.parseLong(primaryKeyValue.toString());
            }
            if (existNodesMap.containsKey(primaryKeyValue)) {
                var exist = existNodesMap.get(primaryKeyValue);
                exist.setDisplayName(StringUtils.isEmpty(titleKeyColumnName) ? r.get(primaryKeyColumnName).toString() : r.get(titleKeyColumnName).toString());
                nodes.add(exist);
            } else {
                nodes.add(EntityNode.builder()
                        .ontologyUniqIdentifier(ontologyUniqueIdentifier)
                        .primaryKey(r.get(primaryKeyColumnName))
                        .tableName(datasourceId)
                        .displayName(StringUtils.isEmpty(titleKeyColumnName) ? r.get(primaryKeyColumnName).toString() : r.get(titleKeyColumnName).toString())
                        .build());
            }
        });

        if (CollectionUtils.isNotEmpty(nodes)) {
            nodeRepository.batchSave(nodes);
        }

    }

    @Override
    public void deleteRelationsByLinkId(String linkId) {
        relationRepository.deleteByOntologyLinkId(linkId);
    }

    @Override
    public void deleteNodesAndRelationsByOntologyId(String ontologyUniqueIdentifier) {
        var nodes = nodeRepository.findByOntologyUniqIdentifier(ontologyUniqueIdentifier);
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }
        nodeRepository.deleteByIds(nodes.stream().map(v -> v.getArangoId()).collect(Collectors.toList()));
        var relations = relationRepository.findByFrom(nodes);
        relations.addAll(relationRepository.findByTo(nodes));
        if (CollectionUtils.isEmpty(relations)) {
            return;
        }
        relationRepository.deleteByIds(relations.stream().map(v -> v.getArangoId()).collect(Collectors.toList()));
    }


    @Override
    public List<EntityLinkPropertyVO> getEntityLinksByPrimaryKey(String ontologyUniqueIdentifier,
                                                                 Object entityPrimaryKey) {

        //var relations = relationRepository.queryEnableRelationsByEntity(ontologyUniqueIdentifier, entityPrimaryKey);
        var relations = relationRepository.queryAllRelationsByEntities(ontologyUniqueIdentifier, Lists.newArrayList(entityPrimaryKey));
        if (CollectionUtils.isEmpty(relations)) {
            return Lists.newArrayList();
        }
        return relations.stream().map(v -> DataConverter.convert(v)).collect(Collectors.toList());
    }


    @Override
    public List<EntityLinksVO> getAllLinksByEntityIds(List<EntityIdsQueryParam> params) {
        List<EntityLinksVO> res = Lists.newArrayList();
        params.stream().forEach(p -> {
            var primaryKeys = p.getEntityPrimaryKeys();
            if (CollectionUtils.isEmpty(primaryKeys)) {
                var entities = getByEntityIds(Lists.newArrayList(EntityIdsQueryParam
                        .builder()
                        .ontologyUniqueIdentifier(p.getOntologyUniqueIdentifier())
                        .build()));
                primaryKeys = entities.stream().flatMap(v -> v.getEntityList().stream().map(e -> e.getPrimaryKey())).collect(Collectors.toList());
            }
            var relations = relationRepository.queryAllRelationsByEntities(p.getOntologyUniqueIdentifier(), primaryKeys);

            if (CollectionUtils.isEmpty(relations)) {
                return;
            }

            var map1 = relations.stream().collect(Collectors.groupingBy(v -> v.getFrom().getOntologyUniqIdentifier() + v.getFrom().getPrimaryKey()));
            var map2 = relations.stream().collect(Collectors.groupingBy(v -> v.getTo().getOntologyUniqIdentifier() + v.getTo().getPrimaryKey()));

            map2.forEach((key, list) -> map1.merge(key, list, (list1, list2) -> {
                list1.addAll(list2);
                return list1;
            }));

            primaryKeys.forEach(pk -> {
                var r = map1.get(p.getOntologyUniqueIdentifier() + pk);
                res.add(EntityLinksVO.builder()
                        .links(r.stream().map(v -> DataConverter.convert(v)).collect(Collectors.toList()))
                        .entityPrimaryKey(pk)
                        .ontologyUniqueIdentifier(p.getOntologyUniqueIdentifier())
                        .build());
            });

        });
        return res;
    }

    @Override
    public EntityNode findOneByOntologyUniqIdentifier(String ontologyIdentifier) {
        return nodeRepository.findOneByOntologyUniqIdentifier(ontologyIdentifier);
    }

    @Override
    public List<EntityPropertyDetailVO> getEntityDetail(String ontologyUniqueIdentifier, Object entityPrimaryKey) {
        List<EntityPropertyDetailVO> res = Lists.newArrayList();
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier))
                .stream()
                .filter(v -> StringUtils.isNotEmpty(v.getDatasourceColumnName()))
                .collect(Collectors.toList());

        var primaryKeyProp = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        if (!primaryKeyProp.isPresent() || StringUtils.isEmpty(primaryKeyProp.get().getDatasourceColumnName())) {
            return res;
        }
        var propsMap = props.stream().collect(Collectors.groupingBy(v -> v.getDatasourceId()));
        //查询主键表对应的实体数据
        var pk = primaryKeyProp.get();
        var pkColumns = propsMap.get(pk.getDatasourceId()).stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList());
        var primaryData = objectMapper.queryDataByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pkColumns, pk.getDatasourceColumnName(), entityPrimaryKey);
        var propertyMap = propsMap.get(pk.getDatasourceId()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        var details = primaryData.entrySet().stream().<EntityPropertyDetailVO>map(entry -> {
            var colName = entry.getKey();
            var colValue = entry.getValue();
            var p = propertyMap.get(colName);
            return EntityPropertyDetailVO.builder()
                    .propertyDisplayName(p.getDisplayName())
                    .propertyValues(Lists.newArrayList(colValue))
                    .propertyUniqIdentifier(p.getUniqueIdentifier())
                    .propertyApiName(p.getApiName())
                    .entityPrimaryKey(entityPrimaryKey)
                    .build();
        }).collect(Collectors.toList());
        res.addAll(details);

        propsMap.entrySet().forEach(entry -> {
            if (!entry.getKey().equals(pk.getDatasourceId())) {
                //查询关联表的实体数据
                var tableMapping = tableFieldMappingMapper.selectBySourceAndTarget(
                        pk.getDatasourceSchema(), pk.getDatasourceId(), entry.getKey());
                if (tableMapping == null) {
                    return;
                }
                var orderBy = tableMetadataMapper.queryPrimaryKeyColumnName(pk.getDatasourceSchema(), entry.getKey());
                var columns = entry.getValue().stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList());
                var otherData = objectMapper.queryByJoinTable(
                        pk.getDatasourceSchema(),
                        entityPrimaryKey,
                        tableMapping.getTargetTableName(),
                        columns,
                        tableMapping.getTargetColumnName(),
                        orderBy,
                        10,
                        QuerySortEnum.DESC.getValue()
                );

                if (CollectionUtils.isNotEmpty(otherData)) {
                    var otherColumns = otherData.get(0).keySet().stream().collect(Collectors.toList());
                    var otherPropertyMap = propsMap.get(entry.getKey()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
                    var detail = otherColumns.stream().<EntityPropertyDetailVO>map(col -> {
                        var p = otherPropertyMap.get(col);
                        var values = otherData.stream().map(v -> v.get(col)).collect(Collectors.toList());

                        return EntityPropertyDetailVO.builder()
                                .propertyDisplayName(p.getDisplayName())
                                .propertyUniqIdentifier(p.getUniqueIdentifier())
                                .propertyApiName(p.getApiName())
                                .propertyValues(values)
                                .entityPrimaryKey(entityPrimaryKey)
                                .categoryId(p.getPropertyCategoryId())
                                .metadata(p.getMetadata())
                                .build();
                    }).collect(Collectors.toList());
                    res.addAll(detail);
                }
            }
        });
        return res;
    }

    @Override
    public EntityPropertyRowDetailVO getEntityPropertyRowDetail(EntityPropertyRowQueryParam param) {

        List<EntityPropertyRowDetailVO.PropertyGroup> propertyGroups = Lists.newArrayList();

        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyUniqueIdentifier()))
                .stream()
                .filter(v -> StringUtils.isNotEmpty(v.getDatasourceColumnName()))
                .collect(Collectors.toList());

        var primaryKeyProp = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        if (!primaryKeyProp.isPresent() || StringUtils.isEmpty(primaryKeyProp.get().getDatasourceColumnName())) {
            return EntityPropertyRowDetailVO.builder()
                    .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                    .entityPrimaryKey(param.getEntityPrimaryKey())
                    .build();
        }
        var propsMap = props.stream().collect(Collectors.groupingBy(v -> v.getDatasourceId()));
        //查询主属性表对应的实体数据
        var pk = primaryKeyProp.get();
        var pkColumns = propsMap.get(pk.getDatasourceId()).stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList());
        var primaryData = objectMapper.queryDataByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pkColumns, pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
        var propertyMap = propsMap.get(pk.getDatasourceId()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        var propertyInfoList = primaryData.entrySet().stream().<EntityPropertyRowDetailVO.PropertyInfo>map(entry -> {
            var colName = entry.getKey();
            var colValue = entry.getValue();
            var p = propertyMap.get(colName);
            return EntityPropertyRowDetailVO.PropertyInfo.builder()
                    .propertyDisplayName(p.getDisplayName())
                    .propertyValue(colValue)
                    .propertyUniqIdentifier(p.getUniqueIdentifier())
                    .propertyApiName(p.getApiName())
                    .categoryId(p.getPropertyCategoryId())
                    .metadata(p.getMetadata())
                    .build();
        }).collect(Collectors.toList());
        propertyGroups.add(EntityPropertyRowDetailVO.PropertyGroup.builder()
                .storageGroup(pk.getStorageGroup())
                .groupDataList(Lists.newArrayList(EntityPropertyRowDetailVO.PropertyGroupData.builder()
                        .dataPrimaryKey(param.getEntityPrimaryKey())
                        .props(propertyInfoList)
                        .build()))
                .build());

        //查询关联属性表对应的实体数据
        propsMap.entrySet().forEach(entry -> {
            if (!entry.getKey().equals(pk.getDatasourceId())) {
                //查询关联表的实体数据
                var tableMapping = tableFieldMappingMapper.selectBySourceAndTarget(
                        pk.getDatasourceSchema(), pk.getDatasourceId(), entry.getKey());
                if (tableMapping == null) {
                    return;
                }
                var orderBy = tableMetadataMapper.queryPrimaryKeyColumnName(pk.getDatasourceSchema(), entry.getKey());
                var columns = entry.getValue().stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList());
                columns.add(orderBy);
                var otherData = objectMapper.queryByJoinTable(
                        pk.getDatasourceSchema(),
                        param.getEntityPrimaryKey(),
                        tableMapping.getTargetTableName(),
                        columns,
                        tableMapping.getTargetColumnName(),
                        orderBy,
                        100,
                        param.getSort().getValue()
                );

                if (CollectionUtils.isNotEmpty(otherData)) {
                    List<EntityPropertyRowDetailVO.PropertyGroupData> groupDataList = Lists.newArrayList();
                    var otherPropertyMap = propsMap.get(entry.getKey()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
                    for (var row : otherData) {
                        EntityPropertyRowDetailVO.PropertyGroupData groupData = new EntityPropertyRowDetailVO.PropertyGroupData();
                        List<EntityPropertyRowDetailVO.PropertyInfo> propertyInfos = Lists.newArrayList();
                        for (var col : row.keySet()) {
                            if (col.equals(orderBy)) {
                                var dataPk = row.get(col);
                                groupData.setDataPrimaryKey(dataPk);
                                continue;
                            }
                            var p = otherPropertyMap.get(col);
                            var info = EntityPropertyRowDetailVO.PropertyInfo.builder()
                                    .propertyDisplayName(p.getDisplayName())
                                    .propertyUniqIdentifier(p.getUniqueIdentifier())
                                    .propertyApiName(p.getApiName())
                                    .propertyValue(row.get(col))
                                    .categoryId(p.getPropertyCategoryId())
                                    .metadata(p.getMetadata())
                                    .build();
                            propertyInfos.add(info);
                        }
                        groupData.setProps(propertyInfos);
                        groupDataList.add(groupData);
                    }
                    propertyGroups.add(EntityPropertyRowDetailVO.PropertyGroup.builder()
                            .storageGroup(entry.getValue().get(0).getStorageGroup())
                            .groupDataList(groupDataList)
                            .build());
                }
            }
        });

        return EntityPropertyRowDetailVO.builder()
                .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                .entityPrimaryKey(param.getEntityPrimaryKey())
                .propertyGroups(propertyGroups)
                .build();
    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public List<Object> createEntities(EntityCreateParam param) {
        var ontologyIdentifier = param.getOntologyIdentifier();
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var optionalPkProperty = ontologyProperties.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(optionalPkProperty.isPresent() && StringUtils.isNotEmpty(optionalPkProperty.get().getDatasourceId()), "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = optionalPkProperty.get();
        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTable(pk.getDatasourceSchema(), pk.getDatasourceId());

        var ontologyPropertyMap = ontologyProperties.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v));
        List<Object> entityPrimaryKeys = Lists.newArrayList();
        //插入实体
        param.getEntityList().forEach(entity -> {
            var entityGroupProperties = entity.getEntityProperties();
            var mainStorage = entityGroupProperties.stream().filter(v -> v.getStorageGroup().equals("main")).findFirst().get();
            //插入实体主属性
            var mainProps = mainStorage.getProps().get(0);
            var columnValues = mainProps.stream().collect(Collectors.toMap(v -> {
                        String columnName = ontologyPropertyMap.get(v.getPropertyApiName()).getDatasourceColumnName();
                        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(columnName), v.getPropertyApiName() + "未关联数据源");
                        return columnName;
                    },
                    v -> v.getPropertyValue()));
            Object entityPK = objectMapper.batchInsertObjectReturnKey(pk.getDatasourceSchema(), pk.getDatasourceId(), Lists.newArrayList(columnValues), pk.getDatasourceColumnName()).get(0);
            entityPrimaryKeys.add(entityPK);
            //插入实体关联属性
            var entityProperties = entityGroupProperties.stream().filter(v -> !v.getStorageGroup().equals("main")).collect(Collectors.toList());
            entityProperties.stream().forEach(entityProperty -> {
                var storageGroup = entityProperty.getStorageGroup();
                var ontologyPropertyList = ontologyProperties.stream().filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                        .collect(Collectors.toList());
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ontologyPropertyList), "存储分组" + storageGroup + "未绑定数据源");
                var ds = ontologyPropertyList.get(0).getDatasourceId();
                var tableFieldMapping = tableFieldMappings.stream().filter(v -> v.getTargetTableName().equals(ds)).findFirst().get();

                List<Map<String, Object>> otherPropertyColumnValues = Lists.newArrayList();

                entityProperty.getProps().forEach(props -> {
                    Map<String, Object> columnsMap = new HashMap<>();
                    ontologyPropertyList.forEach(prop -> columnsMap.put(prop.getDatasourceColumnName(), null));
                    props.stream().forEach(v -> {
                        var columnName = ontologyPropertyMap.get(v.getPropertyApiName()).getDatasourceColumnName();
                        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(columnName), v.getPropertyApiName() + "未关联数据源");
                        columnsMap.put(columnName, v.getPropertyValue());
                    });
                    columnsMap.put(tableFieldMapping.getTargetColumnName(), entityPK);
                    otherPropertyColumnValues.add(columnsMap);
                });
                objectMapper.batchInsertObject(pk.getDatasourceSchema(), ds, otherPropertyColumnValues);
            });
        });
        //todo 创建实体节点和关系
        return entityPrimaryKeys;
    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public void insertEntityProperty(EntityPropertyInsertParam param) {

        var ontologyIdentifier = param.getOntologyIdentifier();
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var optionalPkProperty = ontologyProperties.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(optionalPkProperty.isPresent() && StringUtils.isNotEmpty(optionalPkProperty.get().getDatasourceId()), "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = optionalPkProperty.get();
        //校验实体id是否存在
        var pkData = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), Lists.newArrayList(pk.getDatasourceColumnName()), pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
        PreconditionUtils.checkArgument(MapUtils.isNotEmpty(pkData), "实体" + param.getEntityPrimaryKey() + "不存在");

        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTable(pk.getDatasourceSchema(), pk.getDatasourceId());
        var ontologyPropertyMap = ontologyProperties.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v));

        //插入实体关联属性
        var entityProperties = param.getEntityProperties().stream()
                .filter(v -> !v.getStorageGroup().equals("main"))
                .collect(Collectors.toList());

        entityProperties.stream().forEach(entityProperty -> {
            var storageGroup = entityProperty.getStorageGroup();
            var ontologyPropertyList = ontologyProperties.stream().filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                    .collect(Collectors.toList());
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ontologyPropertyList), "存储分组" + storageGroup + "未绑定数据源");
            var ds = ontologyPropertyList.get(0).getDatasourceId();
            var tableFieldMapping = tableFieldMappings.stream().filter(v -> v.getTargetTableName().equals(ds)).findFirst().get();

            List<Map<String, Object>> otherPropertyColumnValues = Lists.newArrayList();

            entityProperty.getProps().forEach(props -> {
                Map<String, Object> columnsMap = new HashMap<>();
                ontologyPropertyList.forEach(prop -> columnsMap.put(prop.getDatasourceColumnName(), null));
                props.stream().forEach(v -> {
                    var columnName = ontologyPropertyMap.get(v.getPropertyApiName()).getDatasourceColumnName();
                    PreconditionUtils.checkArgument(StringUtils.isNotEmpty(columnName), v.getPropertyApiName() + "未关联数据源");
                    columnsMap.put(columnName, v.getPropertyValue());
                });
                columnsMap.put(tableFieldMapping.getTargetColumnName(), param.getEntityPrimaryKey());
                otherPropertyColumnValues.add(columnsMap);
            });
            objectMapper.batchInsertObject(pk.getDatasourceSchema(), ds, otherPropertyColumnValues);
        });

    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public void updateEntityProperty(EntityPropertyUpdateParam param) {
        //获取本体、属性、主键信息
        var ontologyIdentifier = param.getOntologyIdentifier();
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var optionalPkProperty = ontologyProperties.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(optionalPkProperty.isPresent() && StringUtils.isNotEmpty(optionalPkProperty.get().getDatasourceId()), "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = optionalPkProperty.get();
        //校验实体id是否存在
        var pkData = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), Lists.newArrayList(pk.getDatasourceColumnName()), pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
        PreconditionUtils.checkArgument(MapUtils.isNotEmpty(pkData), "实体" + param.getEntityPrimaryKey() + "不存在");
        var ontologyPropertyMap = ontologyProperties.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v));
        // 校验存储分组
        var storageGroup = param.getStorageGroup();
        var ontologyPropertyList = ontologyProperties.stream().filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ontologyPropertyList), "存储分组" + storageGroup + "未绑定数据源");
        var ds = ontologyPropertyList.get(0).getDatasourceId();
        var primaryKeyColumnName = tableMetadataMapper.queryPrimaryKeyColumnName(pk.getDatasourceSchema(), ds);
        // 校验dataPrimaryKey
        if (storageGroup.equals("main")) {
            PreconditionUtils.checkArgument(Objects.equals(param.getEntityPrimaryKey(), param.getDataPrimaryKey()), "属性数据主键和实体主键不一致");
        } else {
            var tableFieldMapping = tableFieldMappingMapper.selectBySourceAndTarget(
                    pk.getDatasourceSchema(), pk.getDatasourceId(), ds);
            var existDataMap = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), ds, Lists.newArrayList(), primaryKeyColumnName, param.getDataPrimaryKey());
            if (MapUtils.isEmpty(existDataMap)) {
                return;
            }
            PreconditionUtils.checkArgument(existDataMap.get(tableFieldMapping.getTargetColumnName()).equals(param.getEntityPrimaryKey()), "无权限修改其他实体的属性数据", HttpStatus.FORBIDDEN);
        }

        //生成属性更新map
        Map<String, Object> columnsMap = new HashMap<>();
        param.getProps().stream().forEach(v -> {
            PreconditionUtils.checkArgument(!v.getPropertyApiName().equals(pk.getApiName()), "属性主键不能修改");
            var columnName = ontologyPropertyMap.get(v.getPropertyApiName()).getDatasourceColumnName();
            PreconditionUtils.checkArgument(StringUtils.isNotEmpty(columnName), v.getPropertyApiName() + "未关联数据源");
            columnsMap.put(columnName, v.getPropertyValue());
        });
        objectMapper.updateObject(pk.getDatasourceSchema(), ds, columnsMap, primaryKeyColumnName, param.getDataPrimaryKey());
    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public void deleteEntityProperty(EntityPropertyDeleteParam param) {
        //获取本体、属性、主键信息
        var ontologyIdentifier = param.getOntologyIdentifier();
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var optionalPkProperty = ontologyProperties.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(optionalPkProperty.isPresent() && StringUtils.isNotEmpty(optionalPkProperty.get().getDatasourceId()), "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = optionalPkProperty.get();
        //校验实体id是否存在
        var pkData = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), Lists.newArrayList(pk.getDatasourceColumnName()), pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
        PreconditionUtils.checkArgument(MapUtils.isNotEmpty(pkData), "实体" + param.getEntityPrimaryKey() + "不存在");
        // 校验存储分组
        var storageGroup = param.getStorageGroup();
        var ontologyPropertyList = ontologyProperties.stream().filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ontologyPropertyList), "存储分组" + storageGroup + "未绑定数据源");
        var ds = ontologyPropertyList.get(0).getDatasourceId();
        var primaryKeyColumnName = tableMetadataMapper.queryPrimaryKeyColumnName(pk.getDatasourceSchema(), ds);
        // 校验dataPrimaryKey
        if (storageGroup.equals("main")) {
            PreconditionUtils.checkArgument(Objects.equals(param.getEntityPrimaryKey(), param.getDataPrimaryKey()), "属性数据主键和实体主键不一致");
            //删除实体和所有关联属性
            objectMapper.deleteByTableNameAndColumn(pk.getDatasourceSchema(), ds, primaryKeyColumnName, param.getDataPrimaryKey());
            var tableFieldMappingList = tableFieldMappingMapper.selectBySourceTable(pk.getDatasourceSchema(), ds);
            tableFieldMappingList.forEach(
                    tableFieldMapping -> objectMapper.deleteByTableNameAndColumn(
                            pk.getDatasourceSchema(),
                            tableFieldMapping.getTargetTableName(),
                            tableFieldMapping.getTargetColumnName(),
                            param.getDataPrimaryKey())
            );
            //todo 删除实体节点和关系

        } else {
            var tableFieldMapping = tableFieldMappingMapper.selectBySourceAndTarget(
                    pk.getDatasourceSchema(), pk.getDatasourceId(), ds);
            var existDataMap = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), ds, Lists.newArrayList(), primaryKeyColumnName, param.getDataPrimaryKey());
            if (MapUtils.isEmpty(existDataMap)) {
                return;
            }
            PreconditionUtils.checkArgument(existDataMap.get(tableFieldMapping.getTargetColumnName()).equals(param.getEntityPrimaryKey()), "无权限删除其他实体的属性数据", HttpStatus.FORBIDDEN);
            //删除单个属性
            objectMapper.deleteByTableNameAndColumn(pk.getDatasourceSchema(), ds, primaryKeyColumnName, param.getDataPrimaryKey());
        }
    }

    @Override
    public Integer countEntity(String datasourceSchema, String datasourceId) {
        return objectMapper.count(datasourceSchema, datasourceId);
    }

    @Override
    public void updateEntityRelation(EntityRelationUpdateParam param) {
        var linkId = param.getLinkUniqIdentifier();
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, linkId));
        PreconditionUtils.checkNotNull(link, "invalid link uniqIdentifier", HttpStatus.BAD_REQUEST);
        var relation = relationRepository.queryRelationByFromNodeAndToNode(
                link.getOntologyUniqueIdentifierFrom(),
                param.getEntityPrimaryKeyFrom(),
                link.getOntologyUniqueIdentifierTo(),
                param.getEntityPrimaryKeyTo(),
                linkId);

        //更新实体关系（已存在）
        if (relation != null) {
            var visibilityWindows = param.getVisibilityWindows();
            Date startTime = null, endTime = null;
            if (CollectionUtils.isNotEmpty(visibilityWindows)) {
                startTime = visibilityWindows.get(0).getStartTime();
                endTime = visibilityWindows.get(0).getEndTime();
            }
            relationRepository.updateRelation(startTime, endTime, visibilityWindows, param.getStatus(), relation.getId());
        }
        //插入新的实体关系（实体节点已存在）
        else {
            var fromNode = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(link.getOntologyUniqueIdentifierFrom(), param.getEntityPrimaryKeyFrom());
            var toNode = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(link.getOntologyUniqueIdentifierTo(), param.getEntityPrimaryKeyTo());
            PreconditionUtils.checkArgument(fromNode != null && toNode != null, "实体节点不存在");
            relation = EntityRelation.builder()
                    .ontologyLinkId(link.getUniqueIdentifier())
                    .from(fromNode)
                    .to(toNode)
                    .status(param.getStatus())
                    .timeWindows(param.getVisibilityWindows())
                    .createTime(new Date())
                    .updateTime(new Date())
                    .type(link.getType())
                    .name(link.getName())
                    .build();
            relationRepository.save(relation);
        }
    }


    @Override
    public Page<EntityInfoVO> getEntities(String ontologyUniqueIdentifier,
                                          String propertyName,
                                          Object propertyValue,
                                          Integer pageNum,
                                          Integer pageSize,
                                          Boolean needFilterVisibility) {
        var ontologyMeta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier));
        Page<EntityInfoVO> result = new Page<EntityInfoVO>().setSize(pageSize).setCurrent(pageNum);
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        if (CollectionUtils.isEmpty(props)) {
            return result;
        }
        var primaryProperty = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst().orElse(null);
        //主键数据源未绑定
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceColumnName())) {
            return result;
        }
        var primaryDatasource = primaryProperty.getDatasourceId();
        var schemaName = primaryProperty.getDatasourceSchema();
        Map<String, OntologyProperty> primaryPropMap;
        if (needFilterVisibility) {
            primaryPropMap = props.stream().filter(v -> StringUtils.equals(v.getDatasourceId(), primaryDatasource) && v.getVisibility() == 1)
                    .collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        } else {
            primaryPropMap = props.stream().filter(v -> StringUtils.equals(v.getDatasourceId(), primaryDatasource))
                    .collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        }
        var titleKey = primaryPropMap.values().stream().filter(v -> v.getIsTitleKey() == 1).findFirst();

        String columnName = null;
        if (StringUtils.isNotEmpty(propertyName) && propertyValue != null) {
            var property = props.stream().filter(v -> v.getDisplayName().equals(propertyName)).findFirst();
            PreconditionUtils.checkArgument(property.isPresent() && StringUtils.isNotEmpty(property.get().getDatasourceColumnName()), "属性名称不存在或没有关联数据源：" + propertyName, HttpStatus.BAD_REQUEST);
            columnName = property.get().getDatasourceColumnName();
            var propertyType = property.get().getPropertyType();
            propertyValue = OntologyDataTypeEnum.convert(propertyType, propertyValue);
        }
        //分页查询实体数据
        var hasDeletedField = tableMetadataMapper.isColumnExist(schemaName, primaryDatasource, "is_deleted");
        var records = objectMapper.pageQuery(
                schemaName,
                primaryDatasource,
                primaryPropMap.values().stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList()),
                columnName,
                propertyValue,
                pageSize,
                (pageNum - 1) * pageSize,
                hasDeletedField
        );
        var total = objectMapper.queryCount(schemaName, primaryDatasource, columnName, propertyValue, hasDeletedField);

        var entityRecords = records.stream().map(r -> {
            var entityPK = r.entrySet().stream()
                    .filter(v -> v.getKey().equals(primaryProperty.getDatasourceColumnName()))
                    .findFirst().get().getValue();

            var title = "";
            if (titleKey.isPresent()) {
                title = r.entrySet().stream()
                        .filter(v -> v.getKey().equals(titleKey.get().getDatasourceColumnName()))
                        .findFirst().get()
                        .getValue().toString();
            }

            var entityProps = r.entrySet().stream().map(entry -> {
                return EntityPropertyVO.builder()
                        .propertyApiName(primaryPropMap.get(entry.getKey()).getApiName())
                        .propertyDisplayName(primaryPropMap.get(entry.getKey()).getDisplayName())
                        .propertyValue(entry.getValue())
                        .build();
            }).collect(Collectors.toList());
            return EntityInfoVO.builder()
                    .properties(entityProps)
                    .displayName(StringUtils.isEmpty(title) ? entityPK.toString() : title)
                    .primaryKey(entityPK)
                    .ontologyUniqueIdentifier(ontologyUniqueIdentifier)
                    .ontologyName(ontologyMeta.getDisplayName())
                    .build();
        }).collect(Collectors.toList());

        return result.setTotal(total)
                .setRecords(entityRecords);
    }

    @Override
    public List<EntityNode> getByByOntologyUniqIdentifier(String ontologyIdentifier) {
        return nodeRepository.findByOntologyUniqIdentifier(ontologyIdentifier);
    }

    @Override
    public String executeAction(EntityActionExecuteParam param) throws Exception {
        var infoDTO = initActionContextInfoDTO(param);
        //获取行为关联关系下的本体的实体详情
        List<List<EntityPropertyDetailVO>> linkedEntities = getLinkedEntities(infoDTO);
        return executeEntityAction(param, infoDTO, linkedEntities);
    }

    public List<List<EntityPropertyDetailVO>> getLinkedEntities(ActionContextInfoDTO infoDTO) {
        if (infoDTO.getLink() != null) {
            //如果行为影响关系，找到关联的本体
            var linkedOntology = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, infoDTO.getLink().getOntologyLinkUniqIdentifier()))
                    .getOntologyUniqueIdentifierTo();
            infoDTO.setLinkToOntologyUniqueIdentifier(linkedOntology);
            //如果已指定单个实体
            var param = infoDTO.getEntityActionExecuteParam();
            Page<EntityInfoVO> entities;
            if (StringUtils.isNotEmpty(param.getLinkedOntologyUniqueIdentifier())
                    && param.getLinkedEntityPrimaryKey() != null) {
                PreconditionUtils.checkArgument(StringUtils.equals(linkedOntology, param.getLinkedOntologyUniqueIdentifier()), "关联的本体id不一致");
                var primaryProp = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getIsPrimaryKey, 1)
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, linkedOntology));
                entities = getEntities(linkedOntology, primaryProp.getDisplayName(), param.getLinkedEntityPrimaryKey(), 1, Integer.MAX_VALUE, false);
            } else {
                //否则获取关联本体下的所有实体详情
                entities = getEntities(linkedOntology, "", "", 1, Integer.MAX_VALUE, false);
            }
            var records = entities.getRecords();
            var linkedEntities = records.stream().map(entity -> getEntityDetail(linkedOntology, entity.getPrimaryKey()))
                    .collect(Collectors.toList());

            return linkedEntities;
        }
        return null;
    }


    public ActionContextInfoDTO initActionContextInfoDTO(EntityActionExecuteParam param) {

        var actionDetailVO = actionService.getActionByApi(param.getActionApi());
        var functionDetailVO = functionService.getFunctionDetailByApi(actionDetailVO.getFunctionApi());
        var link = actionDetailVO.getLinkMapping();
        var mappings = actionDetailVO.getMappingIns();
        //src本体属性
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyUniqueIdentifier()));

        //构造函数的输入参数
        var functionInputParams = functionDetailVO.getParams().stream()
                .filter(v -> v.getCategory().equals(FunctionParamCategoryEnum.INPUT))
                .sorted(Comparator.comparing(v -> v.getParamOrder()))
                .collect(Collectors.toList());

        ActionContextInfoDTO infoDTO = ActionContextInfoDTO
                .builder()
                .actionDetailVO(actionDetailVO)
                .functionDetailVO(functionDetailVO)
                .link(link)
                .mappings(mappings)
                .ontologyProperties(ontologyProperties)
                .functionInputParams(functionInputParams)
                .entityActionExecuteParam(param)
                .build();

        return infoDTO;

    }


    public String executeEntityAction(EntityActionExecuteParam param, ActionContextInfoDTO infoDTO, List<List<EntityPropertyDetailVO>> linkedEntities) throws Exception {

        var link = infoDTO.getLink();
        var linkedOntology = infoDTO.getLinkToOntologyUniqueIdentifier();
        var functionInputParams = infoDTO.getFunctionInputParams();
        var mappings = infoDTO.getMappings();
        var actionDetailVO = infoDTO.getActionDetailVO();
        var functionDetailVO = infoDTO.getFunctionDetailVO();
        var ontologyProperties = infoDTO.getOntologyProperties();

        List<String> executeResult = Lists.newArrayList();

        //存在关联关系,实体数据必须存在
        if (CollectionUtils.isNotEmpty(linkedEntities)) {
            //获取源本体实体属性详情
            var srcEntityDetailMap = getEntityDetail(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey())
                    .stream().collect(Collectors.toMap(v -> v.getPropertyUniqIdentifier(), v -> v.getPropertyValues()));

            //和关联本体下的所有实体计算关系
            var taskResults = taskProcessor.processTask(linkedEntities,
                    partitionRecords -> {
                        return partitionRecords.stream().map(linkedEntityDetail -> {
                            try {
                                Map<String, List<Object>> linkedEntityDetailMap = linkedEntityDetail.stream()
                                        .collect(Collectors.toMap(v -> v.getPropertyUniqIdentifier(), v -> v.getPropertyValues()));
                                // 合并两个实体，propertyId作为key
                                Map<String, List<Object>> mergedEntityDetailMap = Stream.of(srcEntityDetailMap, linkedEntityDetailMap)
                                        .flatMap(map -> map.entrySet().stream())
                                        .collect(Collectors.toMap(v -> v.getKey(), v -> v.getValue(),
                                                (list1, list2) -> {
                                                    list1.addAll(list2);
                                                    return list1;
                                                }));
                                ActionContextInfoDTO contextInfoDTO = ActionContextInfoDTO.builder()
                                        .functionInputParams(functionInputParams)
                                        .mappings(mappings)
                                        .entityDetailMap(mergedEntityDetailMap)
                                        .actionDetailVO(actionDetailVO)
                                        .functionDetailVO(functionDetailVO)
                                        .ontologyProperties(ontologyProperties)
                                        .entityActionExecuteParam(param)
                                        .link(link)
                                        .linkEntityPrimaryKey(linkedEntityDetail.get(0).getEntityPrimaryKey())
                                        .linkToOntologyUniqueIdentifier(linkedOntology)
                                        .build();
                                //执行函数
                                String functionResultJson = callFunction(contextInfoDTO);
                                var functionResult = jsonMapper.readValue(functionResultJson, new TypeReference<FunctionResultVO>() {
                                });
                                //如果函数是同步执行，更新实体属性和关系
                                if (functionResult != null && StringUtils.isEmpty(functionResult.getTaskId())) {
                                    updateEntityPropertyAndRelation(functionResultJson, contextInfoDTO);
                                }
                                return functionResultJson;
                            } catch (Exception e) {
                                log.error("函数执行异常：" + linkedEntityDetail.toString(), e);
                                return "";
                            }
                        }).collect(Collectors.toList());
                    },
                    20);
            executeResult.addAll(taskResults.stream().flatMap(v -> v.stream()).collect(Collectors.toList()));
        }

        //无关联关系
        else {
            //获取源本体实体属性详情
            var srcEntityDetailMap = getEntityDetail(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey())
                    .stream().collect(Collectors.toMap(v -> v.getPropertyUniqIdentifier(), v -> v.getPropertyValues()));
            //函数调用
            var contextInfoDTO = ActionContextInfoDTO.builder()
                    .functionInputParams(functionInputParams)
                    .mappings(mappings)
                    .entityDetailMap(srcEntityDetailMap)
                    .actionDetailVO(actionDetailVO)
                    .functionDetailVO(functionDetailVO)
                    .ontologyProperties(ontologyProperties)
                    .entityActionExecuteParam(param)
                    .build();
            var functionResultJson = callFunction(contextInfoDTO);
            var functionResult = jsonMapper.readValue(functionResultJson, new TypeReference<FunctionResultVO>() {
            });
            //函数同步执行，更新结果
            if (functionResult != null && StringUtils.isEmpty(functionResult.getTaskId())) {
                updateEntityPropertyAndRelation(functionResultJson, contextInfoDTO);
            }
            executeResult.add(functionResultJson);
        }
        return jsonMapper.writeValueAsString(executeResult);
    }


    @Override
    @Transactional(transactionManager = "datalakeTransactionManager")
    public void updateProperty(EntityUpdateParam param) {
        var primaryKeyColumnName = tableMetadataMapper.queryPrimaryKeyColumnName(param.getSchemaName(), param.getDatasourceId());
        var columnMap = param.getColumnUpdates().stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v.getColumnValue()));
        objectMapper.updateObject(param.getSchemaName(), param.getDatasourceId(), columnMap, primaryKeyColumnName, param.getPrimaryKeyValue());
    }

    @Override
    public void createEntityNodes(String ontologyIdentifier) {
        var primaryProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));

        var titleProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                .eq(OntologyProperty::getIsTitleKey, 1));

        var titleColumn = "";
        if (titleProperty != null
                && primaryProperty != null
                && StringUtils.equals(titleProperty.getDatasourceId(), primaryProperty.getDatasourceId())) {
            titleColumn = titleProperty.getDatasourceColumnName();
        }

        if (primaryProperty != null && StringUtils.isNotEmpty(primaryProperty.getDatasourceId())) {
            syncNodes(ontologyIdentifier, primaryProperty.getDatasourceSchema(), primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
        }
    }

    @Override
    public List<EntityIdsQueryVO> getByEntityIds(List<EntityIdsQueryParam> params) {
        if (CollectionUtils.isEmpty(params)) {
            return Lists.newArrayList();
        }
        return params.stream().map(p -> {
            var entities = getEntities(p.getOntologyUniqueIdentifier(), null, null, 1, Integer.MAX_VALUE, false);
            var entityPrimaryKeys = p.getEntityPrimaryKeys();
            List<EntityInfoVO> entityList = Lists.newArrayList();
            if (CollectionUtils.isEmpty(entityPrimaryKeys)) {
                entityList = entities.getRecords();
            } else {
                var entityMap = entities.getRecords().stream().collect(Collectors.toMap(v -> v.getPrimaryKey(), v -> v));
                entityList = entityPrimaryKeys.stream().map(k -> entityMap.get(k)).filter(v -> v != null).collect(Collectors.toList());
            }
            return EntityIdsQueryVO.builder()
                    .entityList(entityList)
                    .ontologyUniqueIdentifier(p.getOntologyUniqueIdentifier())
                    .build();
        }).collect(Collectors.toList());
    }


    public String callFunction(ActionContextInfoDTO actionContext) throws Exception {

        //构造函数参数，list类型返回所有值，其他类型取第一个值
        List<FunctionParameter> parameters = actionContext.getFunctionInputParams().stream().map(p -> {
            var mappingVO = actionContext.getMappings().stream().filter(m -> m.getFunctionParamId().equals(p.getParamId())).findFirst();
            if (!mappingVO.isPresent()) {
                return new FunctionParameter().setParamName(p.getParamName());
            }
            Object value = null;
            // 如果本体没有给属性绑定数据源，则获取的values为null，函数可能执行失败
            List<Object> values = actionContext.getEntityDetailMap().get(mappingVO.get().getPropertyUniqueIdentifier());
            if (CollectionUtils.isNotEmpty(values)) {
                value = p.getParamType().equals(FunctionParamTypeEnum.LIST) ? values : values.get(0);
            }
            return FunctionParameter.builder()
                    .paramName(p.getParamName())
                    .paramValue(value)
                    .build();
        }).collect(Collectors.toList());
        //执行函数
        var functionResultJson = functionService.executeFunction(FunctionExecuteParam.builder()
                .functionApi(actionContext.getActionDetailVO().getFunctionApi())
                .parameters(parameters)
                .build());

        var functionResult = jsonMapper.readValue(functionResultJson, new TypeReference<FunctionResultVO>() {
        });
        //异步接口，需要保存调用时的上下文信息
        if (functionResult != null && StringUtils.isNotEmpty(functionResult.getTaskId())) {
            var executeResult = executeResultMapper.selectOne(new LambdaQueryWrapper<FunctionExecuteResult>().eq(FunctionExecuteResult::getTaskId, functionResult.getTaskId()));
            if (executeResult != null) {
                executeResultMapper.updateById(executeResult
                        .setActionApi(actionContext.getActionDetailVO().getActionApi())
                        .setActionContextInfo(jsonMapper.writeValueAsString(actionContext)));
            }
        }
        return functionResultJson;
    }


    @SneakyThrows
    @Override
    public void updateEntityPropertyAndRelation(String jsonString, ActionContextInfoDTO actionContext) {

        //更新实体属性值
        //根据函数的输出结果更新源实体属性：通过json path获取value
        var functionOutputParam = actionContext.getFunctionDetailVO().getParams().stream()
                .filter(v -> v.getCategory().equals(FunctionParamCategoryEnum.OUTPUT))
                .findFirst().get();

        var output = actionContext.getMappings().stream().filter(m -> m.getFunctionParamId().equals(functionOutputParam.getParamId())).collect(Collectors.toList());
        //primary datasource
        var primaryDatasource = actionContext.getOntologyProperties().stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst().get().getDatasourceId();
        var propMap = actionContext.getOntologyProperties().stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v));
        List<EntityUpdateParam.ColumnUpdate> columnUpdates = Lists.newArrayList();
        Map<String, Map<String, Object>> insertMap = Maps.newHashMap();
        output.stream().forEach(out -> {
            Object actualValue = JsonPath.using(safeConfig).parse(jsonString).read(out.getFunctionParamExpression());
            var prop = propMap.get(out.getPropertyUniqueIdentifier());
            //主数据源列
            if (prop.getDatasourceId().equals(primaryDatasource)) {
                columnUpdates.add(EntityUpdateParam.ColumnUpdate.builder()
                        .datasourceColumnName(prop.getDatasourceColumnName())
                        .columnValue(actualValue)
                        .propertyUniqIdentifier(out.getPropertyUniqueIdentifier())
                        .build());
            } else {
                var map = insertMap.getOrDefault(prop.getDatasourceId(), new HashMap<String, Object>());
                map.put(prop.getDatasourceColumnName(), actualValue);
                insertMap.put(prop.getDatasourceId(), map);
            }

        });
        //主数据源数据update
        if (CollectionUtils.isNotEmpty(columnUpdates)) {
            updateProperty(EntityUpdateParam.builder()
                    .columnUpdates(columnUpdates)
                    .primaryKeyValue(actionContext.getEntityActionExecuteParam().getEntityPrimaryKey())
                    .datasourceId(primaryDatasource)
                    .build());
        }
        //其他数据源数据insert
        if (MapUtils.isNotEmpty(insertMap)) {
            var pkProp = actionContext.getOntologyProperties().stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst().get();
            insertMap.entrySet().forEach(entry -> {
                //查询关联建，补全columns
                var tableName = entry.getKey();
                var mapping = tableFieldMappingMapper.selectByTargetTable(pkProp.getDatasourceSchema(), tableName);
                entry.getValue().put(mapping.getTargetColumnName(), actionContext.getEntityActionExecuteParam().getEntityPrimaryKey());
                objectMapper.insertObject(pkProp.getDatasourceSchema(), tableName, entry.getValue());
            });
        }

        //更新实体关系
        if (actionContext.getLink() != null) {
            //根据函数的输出结果更新实体关系
            EntityRelation relation = relationRepository.queryRelationByFromNodeAndToNode(actionContext.getEntityActionExecuteParam().getOntologyUniqueIdentifier(),
                    actionContext.getEntityActionExecuteParam().getEntityPrimaryKey(),
                    actionContext.getLinkToOntologyUniqueIdentifier(),
                    actionContext.getLinkEntityPrimaryKey(),
                    actionContext.getLink().getOntologyLinkUniqIdentifier());

            if (relation == null) {
                var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, actionContext.getLink().getOntologyLinkUniqIdentifier()));

                var fromNode = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(link.getOntologyUniqueIdentifierFrom(), actionContext.getEntityActionExecuteParam().getEntityPrimaryKey());
                var toNode = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(link.getOntologyUniqueIdentifierTo(), actionContext.getLinkEntityPrimaryKey());

                relation = EntityRelation.builder()
                        .ontologyLinkId(link.getUniqueIdentifier())
                        .from(fromNode)
                        .to(toNode)
                        .status(Status.DELETE)
                        .timeWindows(Lists.newArrayList())
                        .createTime(new Date())
                        .updateTime(new Date())
                        .type(link.getType())
                        .name(link.getName())
                        .build();
                relationRepository.save(relation);
            }
            FunctionResultVO resultVO = jsonMapper.readValue(jsonString, new TypeReference<FunctionResultVO>() {
            });
            //返回结果有可见窗口，直接更新relation startTime/endTime
            if (resultVO.getStartTime() != null && resultVO.getEndTime() != null) {
                relationRepository.updateRelation(resultVO.getStartTime(), resultVO.getEndTime(), resultVO.getTimeWindows(), Status.ENABLE, relation.getId());
            } else {
                //无可见窗口时，解析spel表达式,更新relation enable
                Boolean expResult = evaluateJsonCondition(jsonString, actionContext.getLink().getOntologyLinkFunctionParamExpression());
                relationRepository.updateRelation(null, null, resultVO.getTimeWindows(), expResult ? Status.ENABLE : Status.DELETE, relation.getId());
            }

        }
    }

    /**
     * 新增实体节点和实体关系：适用于实体增量同步场景
     *
     * @param ontologyUniqueIdentifier 本体id
     * @param entityPropertyMap        实体属性
     */
    @Override
    public void completeEntityNodeAndRelations(String ontologyUniqueIdentifier, Map<String, Object> entityPropertyMap) {
        //创建实体节点
        var primaryProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));

        var titleProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsTitleKey, 1));

        var titleColumn = "";
        if (titleProperty != null
                && primaryProperty != null
                && StringUtils.equals(titleProperty.getDatasourceId(), primaryProperty.getDatasourceId())) {
            titleColumn = titleProperty.getDatasourceColumnName();
        }

        //未绑定主键数据源
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceId())) {
            return;
        }

        var entityPrimaryKeyValue = entityPropertyMap.get(primaryProperty.getDatasourceColumnName());
        var node = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(ontologyUniqueIdentifier, entityPrimaryKeyValue);
        //已存在的实体节点不再创建关系
        if (node != null) {
            return;
        }
        var newEntityNode = EntityNode.builder()
                .ontologyUniqIdentifier(ontologyUniqueIdentifier)
                .primaryKey(entityPrimaryKeyValue)
                .tableName(primaryProperty.getDatasourceId())
                .displayName(StringUtils.isEmpty(titleColumn) ? entityPrimaryKeyValue.toString() : entityPropertyMap.get(titleColumn).toString())
                .build();
        nodeRepository.save(newEntityNode);

        //创建实体关系
        var links = linkGroupMapper.selectList(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier));


        var relations = new ArrayList<EntityRelation>();

        links.forEach(link -> {
            if (link.getOntologyUniqueIdentifierFrom().equals(ontologyUniqueIdentifier)) {
                var targetOntology = link.getOntologyUniqueIdentifierTo();
                var nodes = nodeRepository.findByOntologyUniqIdentifier(targetOntology);
                nodes.forEach(n -> relations.add(EntityRelation.builder()
                        .ontologyLinkId(link.getUniqueIdentifier())
                        .from(newEntityNode)
                        .to(n)
                        .status(Status.DELETE)
                        .timeWindows(Lists.newArrayList())
                        .createTime(new Date())
                        .updateTime(new Date())
                        .type(link.getType())
                        .name(link.getName())
                        .build()));
            } else {
                var fromOntology = link.getOntologyUniqueIdentifierFrom();
                var nodes = nodeRepository.findByOntologyUniqIdentifier(fromOntology);
                nodes.forEach(n -> relations.add(EntityRelation.builder()
                        .ontologyLinkId(link.getUniqueIdentifier())
                        .from(n)
                        .to(newEntityNode)
                        .status(Status.DELETE)
                        .timeWindows(Lists.newArrayList())
                        .createTime(new Date())
                        .updateTime(new Date())
                        .type(link.getType())
                        .name(link.getName())
                        .build()));
            }
        });

        if (CollectionUtils.isNotEmpty(relations)) {
            relationRepository.batchSave(relations);
        }

    }

    @Override
    public void deleteEntityNodeAndRelations(String ontologyUniqueIdentifier, Object entityPrimaryKey) {
        var relations = relationRepository.queryAllRelationsByEntities(ontologyUniqueIdentifier, Lists.newArrayList(entityPrimaryKey));
        if (CollectionUtils.isNotEmpty(relations)) {
            relationRepository.deleteByIds(relations.stream().map(EntityRelation::getId).collect(Collectors.toList()));
        }

        var node = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(ontologyUniqueIdentifier, entityPrimaryKey);
        if (node != null) {
            nodeRepository.delete(node);
        }
    }

    @Transactional(value = "datalakeTransactionManager")
    @Override
    public void generateEntities(EntityGenerateParam param) {
        var ontologyMeta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
        PreconditionUtils.checkArgument(ontologyMeta.getCanGenerateEntity(), "该本体不能生成实体对象", ResultCode.NO_PERMISSION, HttpStatus.FORBIDDEN);

        var properties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));
        var pk = properties.stream().filter(v -> v.getIsPrimaryKey().equals(1)).findFirst();
        // 主键不存在或者未绑定主数据源
        if (!pk.isPresent()) {
            return;
        }
        var pkProp = pk.get();
        if (StringUtils.isEmpty(pkProp.getDatasourceId()) || StringUtils.isEmpty(pkProp.getDatasourceColumnName())) {
            return;
        }
        //删除已有实体
        deleteExistEntities(pkProp);
        // 生成新的实体数据
        // 插入实体主属性表
        var allPropMap = properties.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v));
        var propertyValueMap = param.getPropertyValues().stream().collect(Collectors.toMap(v -> v.getPropertyApiName(), v -> v.getPropertyValue()));
        var pkProperties = properties.stream().filter(p -> StringUtils.equals(p.getDatasourceId(), pkProp.getDatasourceId())
                        && !StringUtils.equals(p.getDatasourceColumnName(), pkProp.getDatasourceColumnName()))
                .collect(Collectors.toList());

        HashMap<String, Object> columnValueMap = pkProperties.stream()
                .collect(HashMap::new,
                        (map, p) -> {
                            String key = p.getDatasourceColumnName();
                            Object value = convert2DataType(
                                    propertyValueMap.get(p.getApiName()),
                                    allPropMap.get(p.getApiName()).getPropertyType()
                            );
                            map.put(key, value); // 允许 null value
                        },
                        Map::putAll
                );

        List<Map<String, Object>> columnValues = new ArrayList<>();
        for (int i = 0; i < param.getCount(); i++) {
            // 使用 new HashMap<>(map) 实现浅复制，避免引用同一对象
            columnValues.add(new HashMap<>(columnValueMap));
        }

        var generateIds = objectMapper.batchInsertObjectReturnKey(pkProp.getDatasourceSchema(), pkProp.getDatasourceId(), columnValues, pkProp.getDatasourceColumnName());
        // 插入关联属性表
        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTable(pkProp.getDatasourceSchema(), pkProp.getDatasourceId());
        tableFieldMappings.stream().forEach(
                tableFieldMapping -> {
                    var targetTableName = tableFieldMapping.getTargetTableName();
                    var props = properties.stream().filter(p -> StringUtils.equals(p.getDatasourceId(), targetTableName)).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(props)) {
                        HashMap<String, Object> valueMap = props.stream()
                                .collect(HashMap::new,
                                        (map, p) -> {
                                            String key = p.getDatasourceColumnName();
                                            Object value = convert2DataType(
                                                    propertyValueMap.get(p.getApiName()),
                                                    allPropMap.get(p.getApiName()).getPropertyType()
                                            );
                                            map.put(key, value); // 允许 null value
                                        },
                                        Map::putAll
                                );

                        List<Map<String, Object>> values = new ArrayList<>();
                        for (int i = 0; i < param.getCount(); i++) {
                            // 使用 new HashMap<>(map) 实现浅复制，避免引用同一对象
                            valueMap.put(tableFieldMapping.getTargetColumnName(), generateIds.get(i));
                            values.add(new HashMap<>(valueMap));
                        }
                        objectMapper.batchInsertObject(pkProp.getDatasourceSchema(), tableFieldMapping.getTargetTableName(), values);
                    }
                }
        );
        // 补全实体节点和关系
        for (int i = 0; i < param.getCount(); i++) {
            var pkValue = generateIds.get(i);
            var entityPropertyMap = columnValues.get(i);
            entityPropertyMap.put(pkProp.getDatasourceColumnName(), pkValue);
            completeEntityNodeAndRelations(param.getOntologyIdentifier(), entityPropertyMap);
        }
    }


    @Override
    public Page<List<EntityPropertyGenericQueryVO>> genericQuery(EntityPropertyGenericQueryParam param) {
        // 1. 校验查询属性是否存在或者是否关联了数据源
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));
        // 查询主键是否存在且关联了数据源
        var pkProp = props.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst().orElse(null);
        PreconditionUtils.checkArgument(pkProp != null && StringUtils.isNotEmpty(pkProp.getDatasourceId()), "主键属性不存在或未关联数据源", HttpStatus.BAD_REQUEST);
        // 校验返回属性是否存在或者是否关联了数据源
        var propMap = props.stream().collect(Collectors.toMap(OntologyProperty::getApiName, v -> v));
        param.getSelectProperties().forEach(p -> {
            var findProp = propMap.get(p.getPropertyApiName());
            PreconditionUtils.checkArgument(findProp != null && StringUtils.isNotEmpty(findProp.getDatasourceId()), "属性" + p.getPropertyApiName() + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);
        });
        //主属性数据源表
        var mainDs = pkProp.getDatasourceId();
        // 2. 收集所有表的关联映射
        Map<String, TableFieldMapping> tableMappingMap = new HashMap<>();
        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTable(pkProp.getDatasourceSchema(), mainDs);
        tableFieldMappings.forEach(mapping -> tableMappingMap.put(mapping.getTargetTableName(), mapping));
        // 3. 构建 SELECT 部分
        var selectBuilder = new StringBuilder();
        List<String> selectColumnList = Lists.newArrayList();
        Map<String, OntologySelectPropertyParam> selectParamMap = Maps.newLinkedHashMap();
        for (OntologySelectPropertyParam selectProp : param.getSelectProperties()) {
            if (selectBuilder.length() > 0) {
                selectBuilder.append(",");
            }
            var prop = propMap.get(selectProp.getPropertyApiName());
            var aliasKey = StringUtils.isNotEmpty(selectProp.getAlias()) ? selectProp.getAlias() : selectProp.getPropertyApiName();
            selectParamMap.put(aliasKey, selectProp);
            var columnRef = wrapColumnRef(prop.getDatasourceSchema(), prop.getDatasourceId(), prop.getDatasourceColumnName());

            if (selectProp.getAggFunc() != null) {
                switch (selectProp.getAggFunc()) {
                    case COUNT:
                    case SUM:
                    case AVG:
                    case MAX:
                    case MIN:
                    case DISTINCT:
                        selectBuilder.append(selectProp.getAggFunc().getValue()).append("(").append(columnRef).append(")");
                        break;
                    default:
                        selectBuilder.append(columnRef);
                }
            } else {
                selectBuilder.append(columnRef);
            }
            var selectColumn = StringUtils.isNotEmpty(selectProp.getAlias()) ? selectProp.getAlias() : selectProp.getPropertyApiName();
            selectBuilder.append(" AS ").append(wrapWithDoubleQuotes(selectColumn));
            selectColumnList.add(selectColumn);
        }
        // 4. 构建 FROM 和 LEFT JOIN
        var fromBuilder = new StringBuilder();
        fromBuilder.append(wrapTableRef(pkProp.getDatasourceSchema(), mainDs));
        var joinedTables = Sets.newHashSet();
        joinedTables.add(mainDs);
        // 收集所有涉及的属性API名称
        Set<String> allPropApiNames = new LinkedHashSet<>();
        // select属性
        for (var selectProp : param.getSelectProperties()) {
            allPropApiNames.add(selectProp.getPropertyApiName());
        }
        // 过滤条件属性
        allPropApiNames.addAll(collectFilterPropertyApiNames(param.getFilters()));
        // 分组属性
        if (CollectionUtils.isNotEmpty(param.getGroupBy())) {
            allPropApiNames.addAll(param.getGroupBy());
        }
        // 排序属性
        if (CollectionUtils.isNotEmpty(param.getOrderBy())) {
            for (OrderByParam orderBy : param.getOrderBy()) {
                allPropApiNames.add(orderBy.getPropertyApiName());
            }
        }
        // 统一添加LEFT JOIN
        for (var apiName : allPropApiNames) {
            var prop = propMap.get(apiName);
            if (prop == null || StringUtils.isEmpty(prop.getDatasourceId())) {
                continue;
            }
            var dsId = prop.getDatasourceId();
            if (joinedTables.contains(dsId)) {
                continue;
            }
            var mapping = tableMappingMap.get(dsId);
            PreconditionUtils.checkArgument(mapping != null, "未找到数据源表 " + dsId + " 与主表 " + mainDs + " 的关联关系", HttpStatus.BAD_REQUEST);
            fromBuilder.append(" LEFT JOIN ").append(wrapTableRef(prop.getDatasourceSchema(), dsId))
                    .append(" ON ").append(wrapColumnRef(pkProp.getDatasourceSchema(), mainDs, mapping.getSourceColumnName())).append(" = ")
                    .append(wrapColumnRef(prop.getDatasourceSchema(), dsId, mapping.getTargetColumnName()));
            joinedTables.add(dsId);
        }
        // 5. 构建 WHERE 部分
        var whereBuilder = new StringBuilder();
        if (param.getFilters() != null && CollectionUtils.isNotEmpty(param.getFilters().getChildren())) {
            var whereClause = buildWhereClause(param.getFilters(), propMap);
            if (StringUtils.isNotEmpty(whereClause)) {
                whereBuilder.append(" WHERE ").append(whereClause);
            }
        }
        // 6. 构建 GROUP BY
        var groupByBuilder = new StringBuilder();
        if (CollectionUtils.isNotEmpty(param.getGroupBy())) {
            groupByBuilder.append(" GROUP BY ");
            var first = true;
            for (var groupByProp : param.getGroupBy()) {
                if (!first) {
                    groupByBuilder.append(",");
                }
                var prop = propMap.get(groupByProp);
                PreconditionUtils.checkArgument(prop != null && StringUtils.isNotEmpty(prop.getDatasourceId()),
                        "分组属性" + groupByProp + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);
                groupByBuilder.append(wrapColumnRef(prop.getDatasourceSchema(), prop.getDatasourceId(), prop.getDatasourceColumnName()));
                first = false;
            }
        }
        // 7. 构建 ORDER BY
        var orderByBuilder = new StringBuilder();
        if (CollectionUtils.isNotEmpty(param.getOrderBy())) {
            orderByBuilder.append(" ORDER BY ");
            var first = true;
            for (var orderBy : param.getOrderBy()) {
                if (!first) {
                    orderByBuilder.append(",");
                }
                var prop = propMap.get(orderBy.getPropertyApiName());
                PreconditionUtils.checkArgument(prop != null && StringUtils.isNotEmpty(prop.getDatasourceId()),
                        "排序属性" + orderBy.getPropertyApiName() + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);
                orderByBuilder.append(wrapColumnRef(prop.getDatasourceSchema(), prop.getDatasourceId(), prop.getDatasourceColumnName())).append(" ").append(orderBy.getSort().name());
                first = false;
            }
        }
        // 8. 组装SQL并执行
        var pageSize = param.getPageSize();
        var offset = (param.getPageNum() - 1) * pageSize;
        // base SQL
        var baseSql = "SELECT " + selectBuilder + " FROM " + fromBuilder + whereBuilder + groupByBuilder + orderByBuilder;
        // 计数SQL
        var countSql = "SELECT COUNT(*) FROM (" + baseSql + ") AS t";
        var total = objectMapper.queryCountBySql(countSql);
        if (total == null || total == 0) {
            return new Page<>(param.getPageNum(), pageSize, 0);
        }
        // 数据SQL
        var dataSql = baseSql + " LIMIT " + pageSize + " OFFSET " + offset;
        var resultMaps = objectMapper.queryBySql(dataSql);
        // 9. 转换结果
        List<List<EntityPropertyGenericQueryVO>> resultVOs = Lists.newArrayList();
        for (var row : resultMaps) {
            List<EntityPropertyGenericQueryVO> voList = new ArrayList<>();
            for (var column : selectColumnList) {
                var selectProp = selectParamMap.get(column);
                if (selectProp == null) {
                    continue;
                }
                var prop = propMap.get(selectProp.getPropertyApiName());
                voList.add(EntityPropertyGenericQueryVO.builder()
                        .propertyApiName(selectProp.getPropertyApiName())
                        .propertyDisplayName(prop != null ? prop.getDisplayName() : selectProp.getPropertyApiName())
                        .aggFunc(selectProp.getAggFunc())
                        .alias(selectProp.getAlias())
                        .value(row.get(column))
                        .build());
            }
            resultVOs.add(voList);
        }
        Page<List<EntityPropertyGenericQueryVO>> page = new Page<>(param.getPageNum(), pageSize, total);
        page.setRecords(resultVOs);
        return page;
    }


    private static String wrapWithDoubleQuotes(String str) {
        return StringUtils.wrap(str, "\"");
    }

    private static String wrapTableRef(String schema, String tableName) {
        return wrapWithDoubleQuotes(schema) + "." + wrapWithDoubleQuotes(tableName);
    }

    private static String wrapColumnRef(String schema, String tableName, String columnName) {
        return wrapTableRef(schema, tableName) + "." + wrapWithDoubleQuotes(columnName);
    }

    private String buildWhereClause(FilterGroupParam group, Map<String, OntologyProperty> propMap) {
        if (CollectionUtils.isEmpty(group.getChildren())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String logic = group.getLogic() != null ? group.getLogic().name() : "AND";
        boolean firstGroup = true;
        for (FilterNodeParam node : group.getChildren()) {
            if (!firstGroup) {
                sb.append(" ").append(logic).append(" ");
            }
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                String filterClause = buildFilterClause(node.getFilter(), propMap);
                if (StringUtils.isNotEmpty(filterClause)) {
                    sb.append(filterClause);
                }
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                String subGroup = buildWhereClause(node.getGroup(), propMap);
                if (StringUtils.isNotEmpty(subGroup)) {
                    sb.append("(").append(subGroup).append(")");
                }
            }
            firstGroup = false;
        }
        return sb.toString();
    }

    private Set<String> collectFilterPropertyApiNames(FilterGroupParam group) {
        Set<String> result = Sets.newHashSet();
        if (group == null || CollectionUtils.isEmpty(group.getChildren())) {
            return result;
        }
        for (FilterNodeParam node : group.getChildren()) {
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                result.add(node.getFilter().getPropertyApiName());
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                result.addAll(collectFilterPropertyApiNames(node.getGroup()));
            }
        }
        return result;
    }

    private String buildFilterClause(PropertyFilterParam filter, Map<String, OntologyProperty> propMap) {
        OntologyProperty prop = propMap.get(filter.getPropertyApiName());
        PreconditionUtils.checkArgument(prop != null && StringUtils.isNotEmpty(prop.getDatasourceId()),
                "过滤属性" + filter.getPropertyApiName() + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);

        String columnRef = wrapColumnRef(prop.getDatasourceSchema(), prop.getDatasourceId(), prop.getDatasourceColumnName());
        QueryOpEnum op = filter.getOp() != null ? filter.getOp() : QueryOpEnum.EQ;

        switch (op) {
            case EQ:
                return columnRef + " = " + formatSqlValue(filter.getValue(), prop.getPropertyType());
            case NE:
                return columnRef + " != " + formatSqlValue(filter.getValue(), prop.getPropertyType());
            case LIKE:
                return columnRef + " LIKE " + formatSqlValue("%" + filter.getValue() + "%", OntologyDataTypeEnum.String);
            case LIKE_LEFT:
                return columnRef + " LIKE " + formatSqlValue("%" + filter.getValue(), OntologyDataTypeEnum.String);
            case LIKE_RIGHT:
                return columnRef + " LIKE " + formatSqlValue(filter.getValue() + "%", OntologyDataTypeEnum.String);
            case IN:
                if (CollectionUtils.isEmpty(filter.getValues())) {
                    return "1=0";
                }
                StringBuilder inSb = new StringBuilder(columnRef).append(" IN (");
                for (int i = 0; i < filter.getValues().size(); i++) {
                    if (i > 0) {
                        inSb.append(",");
                    }
                    inSb.append(formatSqlValue(filter.getValues().get(i), prop.getPropertyType()));
                }
                inSb.append(")");
                return inSb.toString();
            case NOT_IN:
                if (CollectionUtils.isEmpty(filter.getValues())) {
                    return "1=1";
                }
                StringBuilder notInSb = new StringBuilder(columnRef).append(" NOT IN (");
                for (int i = 0; i < filter.getValues().size(); i++) {
                    if (i > 0) {
                        notInSb.append(",");
                    }
                    notInSb.append(formatSqlValue(filter.getValues().get(i), prop.getPropertyType()));
                }
                notInSb.append(")");
                return notInSb.toString();
            case BETWEEN:
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(filter.getValues()) && filter.getValues().size() >= 2,
                        "BETWEEN 需要提供两个值", HttpStatus.BAD_REQUEST);
                return columnRef + " BETWEEN " + formatSqlValue(filter.getValues().get(0), prop.getPropertyType())
                        + " AND " + formatSqlValue(filter.getValues().get(1), prop.getPropertyType());
            case NOT_BETWEEN:
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(filter.getValues()) && filter.getValues().size() >= 2,
                        "NOT BETWEEN 需要提供两个值", HttpStatus.BAD_REQUEST);
                return columnRef + " NOT BETWEEN " + formatSqlValue(filter.getValues().get(0), prop.getPropertyType())
                        + " AND " + formatSqlValue(filter.getValues().get(1), prop.getPropertyType());
            case GT:
                return columnRef + " > " + formatSqlValue(filter.getValue(), prop.getPropertyType());
            case GE:
                return columnRef + " >= " + formatSqlValue(filter.getValue(), prop.getPropertyType());
            case LT:
                return columnRef + " < " + formatSqlValue(filter.getValue(), prop.getPropertyType());
            case LE:
                return columnRef + " <= " + formatSqlValue(filter.getValue(), prop.getPropertyType());
            case IS_NULL:
                return columnRef + " IS NULL";
            case IS_NOT_NULL:
                return columnRef + " IS NOT NULL";
            case APPLY:
                String template = filter.getValue() != null ? filter.getValue().toString() : "";
                String applySql = template.replace("{0}", columnRef);
                if (CollectionUtils.isNotEmpty(filter.getValues())) {
                    for (int i = 0; i < filter.getValues().size(); i++) {
                        String placeholder = "{" + (i + 1) + "}";
                        if (applySql.contains(placeholder)) {
                            applySql = applySql.replace(placeholder, formatSqlValue(filter.getValues().get(i), OntologyDataTypeEnum.String));
                        }
                    }
                }
                return applySql;
            default:
                return "";
        }
    }

    private String formatSqlValue(Object value, OntologyDataTypeEnum type) {
        if (value == null) {
            return "NULL";
        }
        if (type == OntologyDataTypeEnum.Float || type == OntologyDataTypeEnum.Double
                || type == OntologyDataTypeEnum.Long || type == OntologyDataTypeEnum.Int) {
            return String.valueOf(value);
        }
        if (type == OntologyDataTypeEnum.Bool) {
            return Boolean.TRUE.equals(value) ? "TRUE" : "FALSE";
        }
        String escaped = value.toString().replace("'", "''");
        return StringUtils.wrap(escaped, "'");
    }


    private void deleteExistEntities(OntologyProperty pkProp) {
        /* 删除本体下已有实体
         *   1 删除属性，包括关联属性信息
         *   2 删除实体节点
         *   3 删除关系
         */

        var entityData = objectMapper.queryDataByPrimaryKeyList(pkProp.getDatasourceSchema(), pkProp.getDatasourceId(), Lists.newArrayList(pkProp.getDatasourceColumnName()), pkProp.getDatasourceColumnName(), null);
        //无实体数据
        if (CollectionUtils.isEmpty(entityData)) {
            return;
        }
        //删除实体主表
        objectMapper.deleteByTableName(pkProp.getDatasourceSchema(), pkProp.getDatasourceId());
        //删除实体属性关联表
        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTableAndColumn(
                pkProp.getDatasourceSchema(), pkProp.getDatasourceId(), pkProp.getDatasourceColumnName());
        tableFieldMappings.stream().forEach(tableFieldMapping -> objectMapper.deleteByTableName(pkProp.getDatasourceSchema(), tableFieldMapping.getTargetTableName()));

        //删除实体关系和节点
        relationRepository.deleteRelationAndNodeByOntologyUniqueIdentifier(pkProp.getOntologyUniqueIdentifier());

    }


    private Boolean evaluateJsonCondition(String jsonStr, String conditionExpr) {
        try {
            if (StringUtils.isEmpty(jsonStr) || StringUtils.isEmpty(conditionExpr)) {
                return false;
            }
            Map<String, Object> dataMap = jsonMapper.readValue(jsonStr, Map.class);
            StandardEvaluationContext context = new StandardEvaluationContext(dataMap);
            context.addPropertyAccessor(new MapAccessor());
            Expression expression = parser.parseExpression(conditionExpr);
            Boolean result = expression.getValue(context, Boolean.class);
            return result != null ? result : false;
        } catch (Exception e) {
            log.error("evaluateJsonCondition failed! jsonStr: " + jsonStr + ", conditionExpr:" + conditionExpr, e);
            return false;
        }
    }

    @Override
    public OntologyInstancesExportDTO exportInstances(String ontologyUniqueIdentifier) {
        var emptyResult = OntologyInstancesExportDTO.builder()
                .nodes(Lists.<EntityNodeExportDTO>newArrayList())
                .build();

        //仅取启用中且绑定了数据源列的属性
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                        .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()))
                .stream()
                .filter(v -> StringUtils.isNotEmpty(v.getDatasourceColumnName()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(props)) {
            return emptyResult;
        }

        //主键属性决定主表；不存在或未绑定数据源则不导实例
        var primaryKeyProp = props.stream()
                .filter(v -> v.getIsPrimaryKey() != null && v.getIsPrimaryKey() == 1)
                .findFirst();
        if (!primaryKeyProp.isPresent()) {
            return emptyResult;
        }
        var pk = primaryKeyProp.get();
        if (StringUtils.isEmpty(pk.getDatasourceId())
                || StringUtils.isEmpty(pk.getDatasourceSchema())
                || StringUtils.isEmpty(pk.getDatasourceColumnName())) {
            return emptyResult;
        }

        var mainSchema = pk.getDatasourceSchema();
        var mainTable = pk.getDatasourceId();
        var pkApiName = pk.getApiName();

        //标题键属性 apiName（用于 displayName），无则用主键值
        var titleApiName = props.stream()
                .filter(v -> v.getIsTitleKey() != null && v.getIsTitleKey() == 1)
                .map(OntologyProperty::getApiName)
                .findFirst().orElse(null);

        //属性按 datasourceId（物理表）分组
        var propsMap = props.stream().collect(Collectors.groupingBy(OntologyProperty::getDatasourceId));

        var exportNodes = Lists.<EntityNodeExportDTO>newArrayList();
        try {
            //主表全量：物理列名 -> 别名(apiName)
            var mainColumnToApiName = new LinkedHashMap<String, String>();
            propsMap.get(mainTable).forEach(p -> mainColumnToApiName.put(p.getDatasourceColumnName(), p.getApiName()));
            var mainRows = objectMapper.queryTableDataByColumn(mainSchema, mainTable, mainColumnToApiName, null);
            if (CollectionUtils.isEmpty(mainRows)) {
                return emptyResult;
            }

            //关联表：按关联键值分组缓存，避免逐行查库
            //结构：物理表名 -> (关联键值 -> List<行(apiName->值)>)
            var joinGroups = Maps.<String, Map<String, List<Map<String, Object>>>>newHashMap();
            //物理表名 -> 关联键列名
            var joinKeyByTable = Maps.<String, String>newHashMap();
            propsMap.forEach((table, tableProps) -> {
                if (StringUtils.equals(table, mainTable)) {
                    return;
                }
                var mapping = tableFieldMappingMapper.selectBySourceAndTarget(mainSchema, mainTable, table);
                if (mapping == null || StringUtils.isEmpty(mapping.getTargetColumnName())) {
                    return;
                }
                var joinKey = mapping.getTargetColumnName();
                //物理列名 -> 别名(apiName)，额外带上关联键列（用列名本身作别名）
                var columnToAlias = new LinkedHashMap<String, String>();
                tableProps.forEach(p -> columnToAlias.put(p.getDatasourceColumnName(), p.getApiName()));
                columnToAlias.put(joinKey, joinKey);
                var rows = objectMapper.queryTableDataByColumn(mainSchema, table, columnToAlias, null);
                if (CollectionUtils.isEmpty(rows)) {
                    return;
                }
                var grouped = rows.stream()
                        .filter(r -> r.get(joinKey) != null)
                        .collect(Collectors.groupingBy(r -> String.valueOf(r.get(joinKey))));
                joinGroups.put(table, grouped);
                joinKeyByTable.put(table, joinKey);
            });

            //内存 join：以主表行为骨架
            for (var row : mainRows) {
                var pkVal = row.get(pkApiName);
                var properties = Maps.<String, Object>newLinkedHashMap();
                //主表属性为标量
                properties.putAll(row);
                //关联表属性：一对一取标量，一对多聚合为 List
                var pkKey = pkVal == null ? null : String.valueOf(pkVal);
                if (pkKey != null) {
                    joinGroups.forEach((table, grouped) -> {
                        var matched = grouped.get(pkKey);
                        if (CollectionUtils.isEmpty(matched)) {
                            return;
                        }
                        var joinKey = joinKeyByTable.get(table);
                        matched.get(0).keySet().stream()
                                .filter(apiName -> !StringUtils.equals(apiName, joinKey))
                                .forEach(apiName -> {
                                    var values = matched.stream().map(m -> m.get(apiName)).collect(Collectors.toList());
                                    properties.put(apiName, matched.size() == 1 ? values.get(0) : values);
                                });
                    });
                }
                var titleVal = titleApiName == null ? null : properties.get(titleApiName);
                var displayName = titleVal != null ? String.valueOf(titleVal)
                        : (pkVal == null ? null : String.valueOf(pkVal));
                exportNodes.add(EntityNodeExportDTO.builder()
                        .primaryKey(pkVal)
                        .displayName(displayName)
                        .properties(properties)
                        .build());
            }
        } catch (Exception e) {
            log.error("导出本体 {} 实例数据失败，数据源不可达或查询异常", ontologyUniqueIdentifier, e);
            return emptyResult;
        }

        return OntologyInstancesExportDTO.builder()
                .nodes(exportNodes)
                .build();
    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public void importInstances(String ontologyUniqueIdentifier, OntologyInstancesExportDTO instances) {
        if (instances == null || CollectionUtils.isEmpty(instances.getNodes())) {
            return;
        }
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        if (CollectionUtils.isEmpty(ontologyProperties)) {
            log.warn("导入本体 {} 实例数据跳过：本体无属性", ontologyUniqueIdentifier);
            return;
        }
        //主键属性 apiName：物理主键为 id SERIAL，导入时丢弃原值，由数据库重新生成
        var pkApiName = ontologyProperties.stream()
                .filter(p -> p.getIsPrimaryKey() != null && p.getIsPrimaryKey() == 1)
                .map(OntologyProperty::getApiName)
                .findFirst().orElse(null);
        //apiName -> 属性（仅取已绑定数据源列的），用于取 storageGroup
        var propertyMap = ontologyProperties.stream()
                .filter(p -> StringUtils.isNotEmpty(p.getDatasourceColumnName()))
                .collect(Collectors.toMap(OntologyProperty::getApiName, v -> v, (a, b) -> a));

        var entities = instances.getNodes().stream()
                .map(node -> buildEntityFromNode(node, propertyMap, pkApiName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(entities)) {
            log.warn("导入本体 {} 实例数据跳过：无有效节点（属性未绑定数据源或缺少主存储分组属性）", ontologyUniqueIdentifier);
            return;
        }
        //复用实体创建逻辑：写数据湖主表 + 关联表（id 由 SERIAL 生成，不建 ArangoDB 节点）
        createEntities(EntityCreateParam.builder()
                .ontologyIdentifier(ontologyUniqueIdentifier)
                .entityList(entities)
                .build());
    }

    /**
     * 将导出节点（扁平 apiName->值）还原为实体创建入参。
     *
     * <p>丢弃主键 id（由数据库 SERIAL 重新生成）与未绑定数据源/未知属性；main 组的 null 值一并丢弃
     * （规避 createEntities 主表插入 Collectors.toMap 对 null 值抛 NPE）。按 storageGroup 分组：
     * main 组还原为单行，非 main 组按 List 下标还原为一对多多行。</p>
     *
     * @return 无可导入的 main 组属性时返回 null（该节点跳过）
     */
    private EntityCreateParam.Entity buildEntityFromNode(EntityNodeExportDTO node,
                                                         Map<String, OntologyProperty> propertyMap,
                                                         String pkApiName) {
        var properties = node.getProperties();
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        //storageGroup -> (apiName -> 值)，丢弃主键、未知/未绑定属性
        var groupedValues = Maps.<String, Map<String, Object>>newLinkedHashMap();
        properties.forEach((apiName, value) -> {
            if (StringUtils.equals(apiName, pkApiName)) {
                return;
            }
            var prop = propertyMap.get(apiName);
            if (prop == null) {
                return;
            }
            var group = StringUtils.isEmpty(prop.getStorageGroup()) ? "main" : prop.getStorageGroup();
            groupedValues.computeIfAbsent(group, k -> Maps.newLinkedHashMap()).put(apiName, value);
        });

        //main 组：单行，跳过 null 值；无有效主存储列则跳过该节点
        var mainInfos = toPropertyInfos(groupedValues.get("main"), true);
        if (CollectionUtils.isEmpty(mainInfos)) {
            log.warn("导入实例节点跳过：无主存储分组(main)有效属性，primaryKey={}", node.getPrimaryKey());
            return null;
        }
        var entityProperties = Lists.<EntityCreateParam.EntityProperty>newArrayList();
        //main 组单行：显式包一层 List<List<PropertyInfo>>，避开 Guava newArrayList 重载歧义
        var mainRows = Lists.<List<EntityCreateParam.PropertyInfo>>newArrayList();
        mainRows.add(mainInfos);
        entityProperties.add(EntityCreateParam.EntityProperty.builder()
                .storageGroup("main")
                .props(mainRows)
                .build());
        //非 main 组：一对多按 List 下标还原多行
        groupedValues.forEach((group, values) -> {
            if (StringUtils.equals(group, "main")) {
                return;
            }
            entityProperties.add(EntityCreateParam.EntityProperty.builder()
                    .storageGroup(group)
                    .props(buildRelatedRows(values))
                    .build());
        });
        return EntityCreateParam.Entity.builder()
                .entityProperties(entityProperties)
                .build();
    }

    /**
     * 属性值映射转 PropertyInfo 列表。
     *
     * @param skipNull 为 true 时跳过 null 值（main 组插入用 Collectors.toMap，null 值会 NPE）
     */
    private List<EntityCreateParam.PropertyInfo> toPropertyInfos(Map<String, Object> values, boolean skipNull) {
        if (values == null || values.isEmpty()) {
            return Lists.newArrayList();
        }
        return values.entrySet().stream()
                .filter(e -> !(skipNull && e.getValue() == null))
                .map(e -> EntityCreateParam.PropertyInfo.builder()
                        .propertyApiName(e.getKey())
                        .propertyValue(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 关联表（非 main 存储分组）属性值还原为多行：List 值按下标展开，标量值在各行重复；
     * 行数取该组内 List 的最大长度（至少 1）。
     */
    private List<List<EntityCreateParam.PropertyInfo>> buildRelatedRows(Map<String, Object> values) {
        //apiName -> 归一化后的值列表
        var normalized = Maps.<String, List<Object>>newLinkedHashMap();
        var rowCount = 1;
        for (var e : values.entrySet()) {
            var list = new ArrayList<Object>();
            if (e.getValue() instanceof List) {
                for (var o : (List<?>) e.getValue()) {
                    list.add(o);
                }
            } else {
                list.add(e.getValue());
            }
            normalized.put(e.getKey(), list);
            rowCount = Math.max(rowCount, list.size());
        }
        var rows = Lists.<List<EntityCreateParam.PropertyInfo>>newArrayList();
        for (int i = 0; i < rowCount; i++) {
            var row = Lists.<EntityCreateParam.PropertyInfo>newArrayList();
            for (var e : normalized.entrySet()) {
                var list = e.getValue();
                Object v;
                if (list.size() > i) {
                    v = list.get(i);
                } else if (list.size() == 1) {
                    //标量在多行间重复
                    v = list.get(0);
                } else {
                    v = null;
                }
                row.add(EntityCreateParam.PropertyInfo.builder()
                        .propertyApiName(e.getKey())
                        .propertyValue(v)
                        .build());
            }
            rows.add(row);
        }
        return rows;
    }


}

