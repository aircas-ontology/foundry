package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.composite.BasicQueryOperator;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.converter.OntologyInstanceConverter;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityNodeExportDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityRelationExportDTO;
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
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 单条关系下实例关系边的导出上限，避免边量过大把导出文件撑爆。
     */
    private static final int EXPORT_RELATION_LIMIT = 5000;

    /**
     * 图节点集合名，与 {@link EntityNode} 的 @Document 一致，用于拼接关系边的端点标识。
     */
    private static final String NODE_COLLECTION = "node";

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


    /**
     * 函数执行结果的自定义键由脚本决定，已由 FunctionResultVO 的 @JsonAnySetter 承接，此处保持默认严格解析。
     */
    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();


    @Override
    public List<EntityRelationExportDTO> exportEntityRelations(String linkUniqueIdentifier) {
        var edges = relationRepository.queryNonDeletedRelationsByLinkIdWithLimit(linkUniqueIdentifier, EXPORT_RELATION_LIMIT);
        if (edges.size() >= EXPORT_RELATION_LIMIT) {
            log.warn("关系 {} 实例关系边数量达到导出上限 {}，结果已截断", linkUniqueIdentifier, EXPORT_RELATION_LIMIT);
        }
        var exported = Lists.<EntityRelationExportDTO>newArrayList();
        edges.forEach(edge -> {
            if (edge.getFrom() == null || edge.getTo() == null) {
                log.warn("关系 {} 实例关系边 {} 导出跳过：端点节点已不存在", linkUniqueIdentifier, edge.getId());
                return;
            }
            exported.add(EntityRelationExportDTO.builder()
                    .entityPrimaryKeyFrom(pkKey(edge.getFrom().getPrimaryKey()))
                    .entityPrimaryKeyTo(pkKey(edge.getTo().getPrimaryKey()))
                    .status(edge.getStatus())
                    .startTime(edge.getStartTime())
                    .endTime(edge.getEndTime())
                    .timeWindows(edge.getTimeWindows())
                    .build());
        });
        return exported;
    }

    @Override
    public void importEntityRelations(String linkUniqueIdentifier,
                                      List<EntityRelationExportDTO> relations,
                                      Map<String, Object> fromPkMapping,
                                      Map<String, Object> toPkMapping) {
        if (CollectionUtils.isEmpty(relations)) {
            return;
        }
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkUniqueIdentifier));
        PreconditionUtils.checkArgument(link != null, "对象关系不存在:" + linkUniqueIdentifier);

        var fromNodes = nodesByPrimaryKey(link.getOntologyUniqueIdentifierFrom());
        var toNodes = nodesByPrimaryKey(link.getOntologyUniqueIdentifierTo());
        //先把导出边解析成新主键对应的图节点；图库里未点亮的实例对没有边，命中的按状态覆盖、缺失的直建
        var pending = Lists.<EntityRelation>newArrayList();
        var skipped = 0;
        for (var relation : relations) {
            var fromNode = fromNodes.get(mappedPkKey(fromPkMapping, relation.getEntityPrimaryKeyFrom()));
            var toNode = toNodes.get(mappedPkKey(toPkMapping, relation.getEntityPrimaryKeyTo()));
            if (fromNode == null || toNode == null) {
                skipped++;
                log.warn("关系 {} 实例关系边落位跳过：实例主键无对应图节点，from={}，to={}", linkUniqueIdentifier,
                        relation.getEntityPrimaryKeyFrom(), relation.getEntityPrimaryKeyTo());
                continue;
            }
            pending.add(EntityRelation.builder()
                    .ontologyLinkId(linkUniqueIdentifier)
                    .from(fromNode)
                    .to(toNode)
                    .status(relation.getStatus() == null ? Status.ENABLE : relation.getStatus())
                    .startTime(relation.getStartTime())
                    .endTime(relation.getEndTime())
                    .timeWindows(relation.getTimeWindows() == null
                            ? Lists.<VisibilityWindow>newArrayList() : relation.getTimeWindows())
                    .createTime(new Date())
                    .updateTime(new Date())
                    .type(link.getType())
                    .name(link.getName())
                    .build());
        }
        if (CollectionUtils.isEmpty(pending)) {
            log.warn("关系 {} 实例关系边落位跳过：导出的 {} 条边均无对应图节点", linkUniqueIdentifier, relations.size());
            return;
        }
        //已存在的端点对按导出状态覆盖，图库里没有的直建；不会为未点亮的实例对补边
        var nodePairs = pending.stream().map(this::nodePairKey).distinct().collect(Collectors.toList());
        var existEdges = relationRepository.queryRelationsByNodePairs(linkUniqueIdentifier, nodePairs).stream()
                .filter(edge -> edge.getFrom() != null && edge.getTo() != null)
                .collect(Collectors.toMap(this::nodePairKey, edge -> edge, (a, b) -> a));
        var newEdges = Lists.<EntityRelation>newArrayList();
        for (var edge : pending) {
            var exist = existEdges.get(nodePairKey(edge));
            if (exist != null) {
                relationRepository.updateRelation(edge.getStartTime(), edge.getEndTime(), edge.getTimeWindows(),
                        edge.getStatus(), exist.getId());
            } else {
                newEdges.add(edge);
            }
        }
        if (CollectionUtils.isNotEmpty(newEdges)) {
            relationRepository.batchSave(newEdges);
        }
        log.info("关系 {} 实例关系边落位完成：导出 {} 条，写入 {} 条，新建 {} 条，跳过 {} 条", linkUniqueIdentifier,
                relations.size(), pending.size(), newEdges.size(), skipped);
    }

    /**
     * 实例关系边的端点标识，形如 {@code node/1|node/2}，与 AQL 中 CONCAT(edge._from, '|', edge._to) 对齐。
     */
    private String nodePairKey(EntityRelation edge) {
        return NODE_COLLECTION + "/" + edge.getFrom().getId() + "|" + NODE_COLLECTION + "/" + edge.getTo().getId();
    }

    /**
     * 实例主键归一化：图库存 Long、JSON 反序列化可能是 Integer 或 String，统一按字符串比较。
     */
    private String pkKey(Object primaryKey) {
        return primaryKey == null ? null : String.valueOf(primaryKey).trim();
    }

    /**
     * 导出主键经映射换成新主键（归一化字符串），映射缺失或源主键为空时返回 null。
     */
    private String mappedPkKey(Map<String, Object> pkMapping, String sourcePrimaryKey) {
        if (pkMapping == null || sourcePrimaryKey == null) {
            return null;
        }
        return pkKey(pkMapping.get(sourcePrimaryKey));
    }

    private Map<String, EntityNode> nodesByPrimaryKey(String ontologyUniqueIdentifier) {
        return getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .filter(node -> node.getPrimaryKey() != null)
                .collect(Collectors.toMap(node -> pkKey(node.getPrimaryKey()), node -> node, (a, b) -> a));
    }


    /**
     * 标题健需要和主键为同一个数据源
     *
     */
    @Override
    public void syncNodes(String ontologyUniqueIdentifier, String schemaName, String datasourceId, String primaryKeyColumnName, String titleKeyColumnName) {

        //图库回读的 Object 主键会退化成 Integer，与数据湖查出的类型不一致，统一按字符串比较，否则同一实例会被建出重复节点
        var existNodesMap = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .filter(v -> v.getPrimaryKey() != null)
                .collect(Collectors.toMap(v -> pkKey(v.getPrimaryKey()), v -> v, (a, b) -> a));

        List<Map<String, Object>> allRows = objectMapper.queryPrimaryKeyAndTitleKeyValue(schemaName, datasourceId, primaryKeyColumnName, titleKeyColumnName);
        List<EntityNode> nodes = Lists.newArrayList();

        allRows.stream().forEach(r -> {
            var primaryKeyValue = r.get(primaryKeyColumnName);
            if (primaryKeyValue instanceof Integer) {
                primaryKeyValue = Long.parseLong(primaryKeyValue.toString());
            }
            var exist = existNodesMap.get(pkKey(primaryKeyValue));
            if (exist != null) {
                var oldDisplayName = exist.getDisplayName();
                var newDisplayName = StringUtils.isEmpty(titleKeyColumnName) ? r.get(primaryKeyColumnName).toString() : r.get(titleKeyColumnName).toString();
                if (!StringUtils.equals(oldDisplayName, newDisplayName)) {
                    exist.setDisplayName(newDisplayName);
                    nodes.add(exist);
                }
            } else {
                nodes.add(EntityNode.builder()
                        .ontologyUniqIdentifier(ontologyUniqueIdentifier)
                        .primaryKey(primaryKeyValue)
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
        //获取对象、属性、主键信息
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
        //获取对象、属性、主键信息
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
            var status = param.getStatus();
            // 行为定义的「修改」只改填了的那几项，没填的保留边上原值，避免改状态顺手把已有时段抹掉
            var partialUpdate = Boolean.TRUE.equals(param.getPartialUpdate());
            var keepWindows = partialUpdate && CollectionUtils.isEmpty(visibilityWindows);
            if (partialUpdate) {
                if (keepWindows) {
                    visibilityWindows = relation.getTimeWindows();
                }
                if (status == null) {
                    status = relation.getStatus();
                }
            }
            // 一级起止时间是「最近的一个存续窗口」，数组存全部窗口；没改时段就保留边上原值
            Date startTime;
            Date endTime;
            if (keepWindows) {
                startTime = relation.getStartTime();
                endTime = relation.getEndTime();
            } else {
                var nearest = nearestWindow(visibilityWindows);
                startTime = nearest == null ? null : nearest.getStartTime();
                endTime = nearest == null ? null : nearest.getEndTime();
            }
            relationRepository.updateRelation(startTime, endTime, visibilityWindows, status, relation.getId());
        }
        //插入新的实体关系（实体节点已存在）
        else {
            var fromPk = normalizePrimaryKey(param.getEntityPrimaryKeyFrom());
            var toPk = normalizePrimaryKey(param.getEntityPrimaryKeyTo());
            var fromNode = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(link.getOntologyUniqueIdentifierFrom(), fromPk);
            var toNode = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(link.getOntologyUniqueIdentifierTo(), toPk);
            PreconditionUtils.checkArgument(fromNode != null && toNode != null, "实体节点不存在");
            var nearest = nearestWindow(param.getVisibilityWindows());
            relation = EntityRelation.builder()
                    .ontologyLinkId(link.getUniqueIdentifier())
                    .from(fromNode)
                    .to(toNode)
                    .status(param.getStatus())
                    .startTime(nearest == null ? null : nearest.getStartTime())
                    .endTime(nearest == null ? null : nearest.getEndTime())
                    .timeWindows(param.getVisibilityWindows())
                    .createTime(new Date())
                    .updateTime(new Date())
                    .type(link.getType())
                    .name(link.getName())
                    .build();
            relationRepository.save(relation);
        }
    }

    /**
     * 取「最近的一个存续窗口」：未结束的窗口里开始最早的那个（正在生效的窗口自然排第一）；
     * 全部窗口都已结束时取最晚结束的那个。
     * <p>
     * 图库边上的一级 startTime/endTime 用它，便于按单个时间区间过滤；
     * 完整的多组时段仍留在 timeWindows 数组里，数组含被提到一级字段的那一段。
     */
    private VisibilityWindow nearestWindow(List<VisibilityWindow> windows) {
        if (CollectionUtils.isEmpty(windows)) {
            return null;
        }
        var now = new Date();
        var nearest = windows.stream()
                .filter(w -> w.getStartTime() != null && w.getEndTime() != null && !w.getEndTime().before(now))
                .min(Comparator.comparing(VisibilityWindow::getStartTime))
                .orElse(null);
        if (nearest != null) {
            return nearest;
        }
        return windows.stream().filter(w -> w.getEndTime() != null)
                .max(Comparator.comparing(VisibilityWindow::getEndTime))
                .orElse(null);
    }


    /**
     * 删除一条实例关系：按两端实体主键定位边并置为 DELETE 状态。
     * 与算子回写链路一致，图库里的边靠状态失效，不做物理删除。
     */
    public void deleteEntityRelation(String linkUniqIdentifier, Object entityPrimaryKeyFrom, Object entityPrimaryKeyTo) {
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkUniqIdentifier));
        PreconditionUtils.checkNotNull(link, "invalid link uniqIdentifier", HttpStatus.BAD_REQUEST);
        var relation = relationRepository.queryRelationByFromNodeAndToNode(link.getOntologyUniqueIdentifierFrom(),
                entityPrimaryKeyFrom, link.getOntologyUniqueIdentifierTo(), entityPrimaryKeyTo, linkUniqIdentifier);
        PreconditionUtils.checkNotNull(relation, "实例关系不存在", HttpStatus.BAD_REQUEST);
        // 删除只翻状态：边上的时段是已有数据，原样保留，便于恢复和追溯
        relationRepository.updateRelation(relation.getStartTime(), relation.getEndTime(),
                relation.getTimeWindows(), Status.DELETE, relation.getId());
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
        //获取行为关联关系下的对象的实体详情
        List<List<EntityPropertyDetailVO>> linkedEntities = getLinkedEntities(infoDTO);
        return executeEntityAction(param, infoDTO, linkedEntities);
    }

    public List<List<EntityPropertyDetailVO>> getLinkedEntities(ActionContextInfoDTO infoDTO) {
        // 不绑算子的行为只动一行属性或一条指定的边，遍历对端全部实例既无意义也昂贵
        if (infoDTO.getFunctionDetailVO() == null) {
            return null;
        }
        if (infoDTO.getLink() != null) {
            //如果行为影响关系，找到关系另一端的对象
            var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getUniqueIdentifier, infoDTO.getLink().getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + infoDTO.getLink().getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
            var linkedOntology = counterpartOntology(linkGroup, infoDTO.getEntityActionExecuteParam().getOntologyUniqueIdentifier());
            infoDTO.setLinkToOntologyUniqueIdentifier(linkedOntology);
            //如果已指定单个实体：优先用关系对端专用参数，其次取各对象实例主键 map 中对端的那个
            var param = infoDTO.getEntityActionExecuteParam();
            var linkedPrimaryKey = param.getLinkedEntityPrimaryKey();
            if (linkedPrimaryKey == null && MapUtils.isNotEmpty(param.getEntityPrimaryKeys())) {
                linkedPrimaryKey = param.getEntityPrimaryKeys().get(linkedOntology);
            }
            Page<EntityInfoVO> entities;
            if (StringUtils.isNotEmpty(param.getLinkedOntologyUniqueIdentifier())
                    && param.getLinkedEntityPrimaryKey() != null) {
                PreconditionUtils.checkArgument(StringUtils.equals(linkedOntology, param.getLinkedOntologyUniqueIdentifier()), "关联的对象id不一致");
            }
            if (linkedPrimaryKey != null) {
                var primaryProp = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getIsPrimaryKey, 1)
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, linkedOntology));
                entities = getEntities(linkedOntology, primaryProp.getDisplayName(), linkedPrimaryKey, 1, Integer.MAX_VALUE, false);
            } else {
                //否则获取关联对象下的所有实体详情
                entities = getEntities(linkedOntology, "", "", 1, Integer.MAX_VALUE, false);
            }
            var records = entities.getRecords();
            var linkedEntities = records.stream().map(entity -> getEntityDetail(linkedOntology, entity.getPrimaryKey()))
                    .collect(Collectors.toList());

            return linkedEntities;
        }
        return null;
    }


    /**
     * 关系的对端对象：行为挂在 From 侧时对端是 To，挂在 To 侧时对端是 From。
     * 判断不出挂在哪一侧时沿用 To，与改造前的取值一致。
     */
    public String counterpartOntology(OntologyLinkGroup linkGroup, String selfOntology) {
        if (StringUtils.isNotEmpty(selfOntology)
                && StringUtils.equals(selfOntology, linkGroup.getOntologyUniqueIdentifierTo())) {
            return linkGroup.getOntologyUniqueIdentifierFrom();
        }
        return linkGroup.getOntologyUniqueIdentifierTo();
    }


    public ActionContextInfoDTO initActionContextInfoDTO(EntityActionExecuteParam param) {

        var actionDetailVO = actionService.getActionByApi(param.getOntologySpaceId(), param.getActionApi());
        // 行为树与行为同表，但它不绑算子也不写单条关系，按单个行为执行会报"算子不存在"这种误导提示
        PreconditionUtils.checkArgument(actionDetailVO.getActionKind() != ActionKindEnum.TREE,
                "行为树不能作为单个行为执行：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        // 属性值、实例关系行为不绑算子，上下文里没有算子定义，执行时按行为定义直接落库；
        // 算子 api 跨空间允许重名，取算子定义必须按行为所属空间，否则会跑到其他空间的同名算子上
        var functionDetailVO = StringUtils.isEmpty(actionDetailVO.getFunctionApi()) ? null
                : functionService.getFunctionDetailByApi(actionDetailVO.getOntologySpaceId(),
                actionDetailVO.getFunctionApi(), null);
        var link = actionDetailVO.getLinkMapping();
        var mappings = actionDetailVO.getMappingIns();
        //src对象属性
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyUniqueIdentifier()));
        // 应用行为的入参/出参可能指向其他对象：按映射收集涉及对象，补全各自属性与实例主键
        var entityPrimaryKeys = resolveEntityPrimaryKeys(param);
        var objectIds = collectMappingObjectIds(actionDetailVO);
        if (StringUtils.isNotEmpty(param.getOntologyUniqueIdentifier())) {
            objectIds.add(param.getOntologyUniqueIdentifier());
        }
        objectIds.addAll(entityPrimaryKeys.keySet());
        var objectProperties = objectIds.isEmpty() ? new LinkedHashMap<String, List<OntologyProperty>>()
                : propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                                .in(OntologyProperty::getOntologyUniqueIdentifier, objectIds))
                        .stream().collect(Collectors.groupingBy(OntologyProperty::getOntologyUniqueIdentifier));

        //构造函数的输入参数
        var functionInputParams = functionDetailVO == null ? Lists.<FunctionParameterVO>newArrayList()
                : functionDetailVO.getParams().stream()
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
                .entityPrimaryKeys(entityPrimaryKeys)
                .objectProperties(objectProperties)
                .functionInputParams(functionInputParams)
                .entityActionExecuteParam(param)
                .build();

        return infoDTO;

    }

    /** 合并显式传入的各对象实例主键与单对象简写，显式 map 优先 */
    private Map<String, Object> resolveEntityPrimaryKeys(EntityActionExecuteParam param) {
        Map<String, Object> keys = new LinkedHashMap<>();
        if (MapUtils.isNotEmpty(param.getEntityPrimaryKeys())) {
            keys.putAll(param.getEntityPrimaryKeys());
        }
        if (StringUtils.isNotEmpty(param.getOntologyUniqueIdentifier()) && param.getEntityPrimaryKey() != null) {
            keys.putIfAbsent(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey());
        }
        if (StringUtils.isNotEmpty(param.getLinkedOntologyUniqueIdentifier()) && param.getLinkedEntityPrimaryKey() != null) {
            keys.putIfAbsent(param.getLinkedOntologyUniqueIdentifier(), param.getLinkedEntityPrimaryKey());
        }
        return keys;
    }

    /** 从行为映射收集需要实例数据的对象：属性/对象/List 来源的入参，以及写入对象属性的出参 */
    private Set<String> collectMappingObjectIds(OntologyActionDetailVO detail) {
        Set<String> objectIds = new LinkedHashSet<>();
        if (detail == null || CollectionUtils.isEmpty(detail.getMappingIns())) {
            return objectIds;
        }
        for (var mapping : detail.getMappingIns()) {
            var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
            var inputNeedsInstance = mapping.getCategory() == FunctionParamCategoryEnum.INPUT
                    && (sourceType == ActionParamSourceEnum.PROPERTY
                    || sourceType == ActionParamSourceEnum.OBJECT
                    || sourceType == ActionParamSourceEnum.LIST);
            var outputWritesProperty = mapping.getCategory() == FunctionParamCategoryEnum.OUTPUT
                    && mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY;
            if ((inputNeedsInstance || outputWritesProperty) && StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())) {
                objectIds.add(mapping.getOntologyUniqueIdentifier());
            }
        }
        return objectIds;
    }

    /**
     * 取某个对象本次执行选中的实例主键：关系对端优先用本次遍历到的实例，
     * 其余对象取显式传入的 map，最后回退到单对象参数。
     */
    private Object resolveEntityPrimaryKey(ActionContextInfoDTO context, EntityActionExecuteParam param, String objectId) {
        if (StringUtils.equals(objectId, context.getLinkToOntologyUniqueIdentifier())
                && context.getLinkEntityPrimaryKey() != null) {
            return context.getLinkEntityPrimaryKey();
        }
        var keys = context.getEntityPrimaryKeys();
        if (keys != null && keys.get(objectId) != null) {
            return keys.get(objectId);
        }
        if (param != null && StringUtils.equals(objectId, param.getOntologyUniqueIdentifier())
                && param.getEntityPrimaryKey() != null) {
            return param.getEntityPrimaryKey();
        }
        return null;
    }

    /**
     * 按映射涉及的对象逐个加载实例属性，属性id作为key合并。
     * <p>只有入参取对象属性、出参写属性才需要实例；入参全是固定值的（如基础算子的条件/聚合查询）不碰实例。
     * 老数据的映射行没写对象id，回退到行为所属对象，保持改造前的取值口径。</p>
     */
    private Map<String, List<Object>> loadEntityDetailMap(ActionContextInfoDTO context, EntityActionExecuteParam param,
                                                          String excludeObjectId) {
        var details = Lists.<EntityPropertyDetailVO>newArrayList();
        var objectIds = collectMappingObjectIds(context.getActionDetailVO());
        if (param != null && StringUtils.isNotEmpty(param.getOntologyUniqueIdentifier())
                && legacyMappingNeedsInstance(context.getActionDetailVO())) {
            objectIds.add(param.getOntologyUniqueIdentifier());
        }
        for (var objectId : objectIds) {
            // 关系对端由本次遍历到的实例提供，不再按选中的单个实例取
            if (StringUtils.equals(objectId, excludeObjectId)) {
                continue;
            }
            var primaryKey = resolveEntityPrimaryKey(context, param, objectId);
            PreconditionUtils.checkNotNull(primaryKey,
                    "对象[" + objectId + "]未选择实例数据，无法执行行为："
                            + (context.getActionDetailVO() == null ? "" : context.getActionDetailVO().getActionApi()),
                    HttpStatus.BAD_REQUEST);
            details.addAll(getEntityDetail(objectId, primaryKey));
        }
        return details.stream().collect(Collectors.toMap(EntityPropertyDetailVO::getPropertyUniqIdentifier,
                EntityPropertyDetailVO::getPropertyValues, (list1, list2) -> list1));
    }

    /** 映射行没写对象id、但取值或回写需要实例时，只能按行为所属对象补 */
    private boolean legacyMappingNeedsInstance(OntologyActionDetailVO detail) {
        if (detail == null || CollectionUtils.isEmpty(detail.getMappingIns())) {
            return false;
        }
        for (var mapping : detail.getMappingIns()) {
            if (StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())) {
                continue;
            }
            var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
            var inputNeedsInstance = mapping.getCategory() == FunctionParamCategoryEnum.INPUT
                    && (sourceType == ActionParamSourceEnum.PROPERTY
                    || sourceType == ActionParamSourceEnum.OBJECT
                    || sourceType == ActionParamSourceEnum.LIST);
            var outputWritesProperty = mapping.getCategory() == FunctionParamCategoryEnum.OUTPUT
                    && mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY;
            if (inputNeedsInstance || outputWritesProperty) {
                return true;
            }
        }
        return false;
    }


    public String executeEntityAction(EntityActionExecuteParam param, ActionContextInfoDTO infoDTO, List<List<EntityPropertyDetailVO>> linkedEntities) throws Exception {

        var link = infoDTO.getLink();
        var linkedOntology = infoDTO.getLinkToOntologyUniqueIdentifier();
        var functionInputParams = infoDTO.getFunctionInputParams();
        var mappings = infoDTO.getMappings();
        var actionDetailVO = infoDTO.getActionDetailVO();
        var functionDetailVO = infoDTO.getFunctionDetailVO();
        var ontologyProperties = infoDTO.getOntologyProperties();

        // 不绑算子的行为按定义对单个实体落库，不进算子调用与结果回写链路
        if (functionDetailVO == null) {
            return actionService.executeBaseAction(param, infoDTO);
        }

        List<String> executeResult = Lists.newArrayList();

        //存在关联关系,实体数据必须存在
        if (CollectionUtils.isNotEmpty(linkedEntities)) {
            //获取映射涉及对象的实体属性详情；对端实例由下面的遍历逐个提供
            var srcEntityDetailMap = loadEntityDetailMap(infoDTO, param, linkedOntology);

            //和关联对象下的所有实体计算关系
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
                                        .externalParams(infoDTO.getExternalParams())
                                        .actionDetailVO(actionDetailVO)
                                        .functionDetailVO(functionDetailVO)
                                        .ontologyProperties(ontologyProperties)
                                        .entityPrimaryKeys(infoDTO.getEntityPrimaryKeys())
                                        .objectProperties(infoDTO.getObjectProperties())
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
            //获取映射涉及对象的实体属性详情
            var srcEntityDetailMap = loadEntityDetailMap(infoDTO, param, null);
            //函数调用
            var contextInfoDTO = ActionContextInfoDTO.builder()
                    .functionInputParams(functionInputParams)
                    .mappings(mappings)
                    .entityDetailMap(srcEntityDetailMap)
                    .externalParams(infoDTO.getExternalParams())
                    .actionDetailVO(actionDetailVO)
                    .functionDetailVO(functionDetailVO)
                    .ontologyProperties(ontologyProperties)
                    .entityPrimaryKeys(infoDTO.getEntityPrimaryKeys())
                    .objectProperties(infoDTO.getObjectProperties())
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
            var paramMappings = actionContext.getMappings().stream()
                    .filter(m -> p.getParamId().equals(m.getFunctionParamId()))
                    .collect(Collectors.toList());
            if (paramMappings.isEmpty()) {
                return new FunctionParameter().setParamName(p.getParamName());
            }
            // 没有子字段路径即整包给这个参数一个值，与逐字段绑定改造前的行为一致
            boolean boundWhole = paramMappings.stream().noneMatch(m -> StringUtils.isNotEmpty(m.getParamPath()));
            if (boundWhole) {
                return FunctionParameter.builder()
                        .paramName(p.getParamName())
                        .paramValue(resolveInputValue(paramMappings.get(0), p.getParamType(), actionContext))
                        .build();
            }
            return assembleObjectParam(p, paramMappings, actionContext);
        }).collect(Collectors.toList());
        // 基础查询算子的对象标识是机制入参，不在 function_param 中声明，由行为绑定的对象单独补上；
        // 其它函数类型不传，assembleParams/buildInputMap 也会过滤未声明入参
        if (actionContext.getFunctionDetailVO().getType() == FunctionTypeEnum.BASIC_QUERY) {
            parameters.add(FunctionParameter.builder()
                    .paramName(BasicQueryOperator.PARAM_ONTOLOGY_IDENTIFIER)
                    .paramValue(actionContext.getEntityActionExecuteParam().getOntologyUniqueIdentifier())
                    .build());
        }
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


    /**
     * 对象（自定义类）参数的逐子字段绑定：按 paramPath 把多条绑定拼成字段树，
     * 由算子执行侧的组装逻辑还原成嵌套对象。
     * <p>
     * 同一参数上若还有一条不带路径的绑定，它作为整包取值，优先于逐字段组装。
     */
    private FunctionParameter assembleObjectParam(FunctionParameterVO param,
                                                  List<ActionParamMappingVO> mappings,
                                                  ActionContextInfoDTO actionContext) {
        var root = new ParamFieldNode();
        mappings.stream()
                .filter(m -> StringUtils.isNotEmpty(m.getParamPath()))
                .forEach(m -> insertFieldPath(root, m));
        var fields = toFieldParams(root, param.getFields(), actionContext);
        var builder = FunctionParameter.builder()
                .paramName(param.getParamName())
                .paramType(param.getParamType());
        mappings.stream()
                .filter(m -> StringUtils.isEmpty(m.getParamPath()))
                .findFirst()
                .ifPresent(m -> builder.paramValue(resolveInputValue(m, param.getParamType(), actionContext)));
        return builder.fields(fields.isEmpty() ? null : fields).build();
    }

    private void insertFieldPath(ParamFieldNode root, ActionParamMappingVO mapping) {
        ParamFieldNode node = root;
        for (var segment : mapping.getParamPath().split("\\.")) {
            node = node.children.computeIfAbsent(segment, k -> new ParamFieldNode());
        }
        node.mapping = mapping;
    }

    /** 按算子声明的字段树还原各层取值；声明里已不存在的子字段跳过，不让一条失效绑定拖垮整次执行 */
    private List<FunctionParameter> toFieldParams(ParamFieldNode node, List<FunctionParameterVO> declared,
                                                  ActionContextInfoDTO actionContext) {
        List<FunctionParameter> fields = Lists.newArrayList();
        node.children.forEach((name, child) -> {
            var declaredField = declared == null ? null : declared.stream()
                    .filter(f -> name.equals(f.getParamName())).findFirst().orElse(null);
            if (declaredField == null) {
                log.warn("算子入参不存在子字段：{}，已跳过该绑定", name);
                return;
            }
            var children = toFieldParams(child, declaredField.getFields(), actionContext);
            var builder = FunctionParameter.builder()
                    .paramName(name)
                    .paramType(declaredField.getParamType());
            if (child.mapping != null) {
                builder.paramValue(resolveInputValue(child.mapping, declaredField.getParamType(), actionContext));
            }
            if (!children.isEmpty()) {
                builder.fields(children);
            }
            fields.add(builder.build());
        });
        return fields;
    }

    /** 子字段绑定树：中间节点只有 children，叶子节点记一条绑定 */
    private static class ParamFieldNode {
        private final Map<String, ParamFieldNode> children = new LinkedHashMap<>();
        private ActionParamMappingVO mapping;
    }


    /**
     * 取一个入参的实参值：固定值取行为定义里的字面量，外部输入取行为测试时传入的值，
     * 其余来源从实体属性详情取。
     */
    private Object resolveInputValue(ActionParamMappingVO mapping, FunctionParamTypeEnum paramType, ActionContextInfoDTO actionContext) {
        if (mapping.getSourceType() == ActionParamSourceEnum.FIXED) {
            return parseFixedValue(mapping.getSourceConfig());
        }
        if (mapping.getSourceType() == ActionParamSourceEnum.EXTERNAL) {
            var externalParams = actionContext.getExternalParams();
            return externalParams == null ? null : externalParams.get(mapping.getParameterName());
        }
        // 如果对象没有给属性绑定数据源，则获取的values为null，函数可能执行失败
        List<Object> values = actionContext.getEntityDetailMap().get(mapping.getPropertyUniqueIdentifier());
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        return FunctionParamTypeEnum.LIST.equals(paramType) ? values : values.get(0);
    }

    /** 固定值按 JSON 字面量解析，前端直接传一段文本时按原样返回 */
    private Object parseFixedValue(String sourceConfig) {
        if (StringUtils.isEmpty(sourceConfig)) {
            return null;
        }
        try {
            return jsonMapper.readValue(sourceConfig, Object.class);
        } catch (Exception e) {
            return sourceConfig;
        }
    }


    @SneakyThrows
    @Override
    public void updateEntityPropertyAndRelation(String jsonString, ActionContextInfoDTO actionContext) {
        // 按行级 outputMode 回写：只有标记为 WRITE_PROPERTY 的出参行才写回属性
        writeFunctionOutputToProperties(jsonString, actionContext);
        // 关系边只有应用行为才落，基础行为（含基础算子）不写库
        if (actionContext.getActionDetailVO().getActionKind() != ActionKindEnum.BASE) {
            updateEntityRelationByFunction(jsonString, actionContext);
        }
    }

    /** 按出参绑定把算子输出写回对象属性：主表列走 update，分组表补一行 insert */
    private void writeFunctionOutputToProperties(String jsonString, ActionContextInfoDTO actionContext) {
        //更新实体属性值
        //根据函数的输出结果更新源实体属性：通过json path获取value
        // 算子可以有多个出参，绑定了哪几个就回写哪几个；没填取值路径的绑定跳过，它本身无法定位出参
        var outputParamIds = actionContext.getFunctionDetailVO().getParams().stream()
                .filter(v -> v.getCategory().equals(FunctionParamCategoryEnum.OUTPUT))
                .map(v -> v.getParamId())
                .collect(Collectors.toSet());

        // 按行级 outputMode 过滤：只有标记 WRITE_PROPERTY 的出参行才回写
        var output = actionContext.getMappings().stream()
                .filter(m -> m.getFunctionParamId() != null && outputParamIds.contains(m.getFunctionParamId()))
                .filter(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY)
                .filter(m -> StringUtils.isNotEmpty(m.getFunctionParamExpression()))
                .collect(Collectors.toList());
        if (output.isEmpty()) {
            return;
        }
        // 出参行可以指向不同对象：属性表按对象合并，回写时各用自己的主键
        var propMap = new LinkedHashMap<String, OntologyProperty>();
        if (CollectionUtils.isNotEmpty(actionContext.getOntologyProperties())) {
            actionContext.getOntologyProperties().forEach(p -> propMap.putIfAbsent(p.getUniqueIdentifier(), p));
        }
        if (MapUtils.isNotEmpty(actionContext.getObjectProperties())) {
            actionContext.getObjectProperties().values()
                    .forEach(list -> list.forEach(p -> propMap.putIfAbsent(p.getUniqueIdentifier(), p)));
        }
        var primaryPropByObject = propMap.values().stream()
                .filter(v -> v.getIsPrimaryKey() == 1 && StringUtils.isNotEmpty(v.getOntologyUniqueIdentifier()))
                .collect(Collectors.toMap(OntologyProperty::getOntologyUniqueIdentifier, v -> v, (a, b) -> a));
        var param = actionContext.getEntityActionExecuteParam();
        Map<String, List<EntityUpdateParam.ColumnUpdate>> columnUpdatesByObject = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Object>>> insertMap = new LinkedHashMap<>();
        output.stream().forEach(out -> {
            Object actualValue = JsonPath.using(safeConfig).parse(jsonString).read(out.getFunctionParamExpression());
            var prop = propMap.get(out.getPropertyUniqueIdentifier());
            if (prop == null) {
                log.warn("行为写回属性不存在，跳过：{}", out.getPropertyUniqueIdentifier());
                return;
            }
            var objectId = StringUtils.isNotEmpty(out.getOntologyUniqueIdentifier())
                    ? out.getOntologyUniqueIdentifier() : prop.getOntologyUniqueIdentifier();
            var primaryProp = primaryPropByObject.get(objectId);
            PreconditionUtils.checkNotNull(primaryProp,
                    "对象[" + objectId + "]未配置主键属性，无法写回", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkNotNull(resolveEntityPrimaryKey(actionContext, param, objectId),
                    "对象[" + objectId + "]未选择实例数据，无法写回", HttpStatus.BAD_REQUEST);
            //主数据源列
            if (prop.getDatasourceId().equals(primaryProp.getDatasourceId())) {
                columnUpdatesByObject.computeIfAbsent(objectId, key -> Lists.newArrayList())
                        .add(EntityUpdateParam.ColumnUpdate.builder()
                                .datasourceColumnName(prop.getDatasourceColumnName())
                                .columnValue(actualValue)
                                .propertyUniqIdentifier(out.getPropertyUniqueIdentifier())
                                .build());
            } else {
                insertMap.computeIfAbsent(objectId, key -> new LinkedHashMap<>())
                        .computeIfAbsent(prop.getDatasourceId(), key -> new HashMap<>())
                        .put(prop.getDatasourceColumnName(), actualValue);
            }

        });
        //主数据源数据update：逐对象用自己的主键
        columnUpdatesByObject.forEach((objectId, columnUpdates) -> {
            var primaryProp = primaryPropByObject.get(objectId);
            updateProperty(EntityUpdateParam.builder()
                    .columnUpdates(columnUpdates)
                    .primaryKeyValue(resolveEntityPrimaryKey(actionContext, param, objectId))
                    .datasourceId(primaryProp.getDatasourceId())
                    .build());
        });
        //其他数据源数据insert：逐对象用自己的主键与主键表结构
        if (MapUtils.isNotEmpty(insertMap)) {
            insertMap.forEach((objectId, byDatasource) -> {
                var primaryProp = primaryPropByObject.get(objectId);
                var primaryKey = resolveEntityPrimaryKey(actionContext, param, objectId);
                byDatasource.forEach((tableName, columns) -> {
                    //查询关联建，补全columns
                    var mapping = tableFieldMappingMapper.selectByTargetTable(primaryProp.getDatasourceSchema(), tableName);
                    columns.put(mapping.getTargetColumnName(), primaryKey);
                    objectMapper.insertObject(primaryProp.getDatasourceSchema(), tableName, columns);
                });
            });
        }
    }

    /**
     * 按算子结果落关系边：算子在 {@link FunctionResultVO} 里返回可见窗口（startTime + endTime）即这条关系成立，
     * 没有返回窗口就整个跳过。
     * <p>
     * 不建也不删：一个只想回写属性的行为不能顺带把图库里已有的边置为删除；
     * 边的启停由实例关系行为负责，它按人工指定的目标状态改边，不走算子结果。
     */
    @SneakyThrows
    private void updateEntityRelationByFunction(String jsonString, ActionContextInfoDTO actionContext) {
        if (actionContext.getLink() == null) {
            return;
        }
        FunctionResultVO resultVO = jsonMapper.readValue(jsonString, new TypeReference<FunctionResultVO>() {
        });
        Date startTime = resultVO.getStartTime();
        Date endTime = resultVO.getEndTime();
        if (startTime == null || endTime == null) {
            return;
        }

        //根据函数的输出结果更新实体关系
        var linkGroupId = actionContext.getLink().getOntologyLinkUniqIdentifier();
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, linkGroupId));
        PreconditionUtils.checkNotNull(link, "无效的对象关系：" + linkGroupId, HttpStatus.BAD_REQUEST);
        // 边按图库里的 From/To 定向：行为对象挂在 To 侧时本端主键落在 To 上，不能一律当 From
        var selfIsFrom = !StringUtils.equals(actionContext.getEntityActionExecuteParam().getOntologyUniqueIdentifier(),
                link.getOntologyUniqueIdentifierTo());
        // 本端主键同样按对象解析：只传各对象实例主键 map 时也能拿到本端实例
        var selfPrimaryKey = resolveEntityPrimaryKey(actionContext, actionContext.getEntityActionExecuteParam(),
                actionContext.getEntityActionExecuteParam().getOntologyUniqueIdentifier());
        var counterpartPrimaryKey = actionContext.getLinkEntityPrimaryKey();
        var primaryKeyFrom = selfIsFrom ? selfPrimaryKey : counterpartPrimaryKey;
        var primaryKeyTo = selfIsFrom ? counterpartPrimaryKey : selfPrimaryKey;

        EntityRelation relation = relationRepository.queryRelationByFromNodeAndToNode(link.getOntologyUniqueIdentifierFrom(),
                primaryKeyFrom, link.getOntologyUniqueIdentifierTo(), primaryKeyTo, linkGroupId);

        if (relation == null) {
            var fromPk = normalizePrimaryKey(primaryKeyFrom);
            var toPk = normalizePrimaryKey(primaryKeyTo);
            var fromNode = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(link.getOntologyUniqueIdentifierFrom(), fromPk);
            var toNode = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(link.getOntologyUniqueIdentifierTo(), toPk);
            if (fromNode == null || toNode == null) {
                //图库里的节点可能被清理过，缺端点时无法建边
                log.warn("行为函数实例关系边落位跳过：端点节点不存在，link={}, fromPk={}, toPk={}",
                        link.getUniqueIdentifier(), fromPk, toPk);
            } else {
                relationRepository.save(EntityRelation.builder()
                        .ontologyLinkId(link.getUniqueIdentifier())
                        .from(fromNode)
                        .to(toNode)
                        .status(Status.ENABLE)
                        .startTime(startTime)
                        .endTime(endTime)
                        .timeWindows(resultVO.getTimeWindows() == null ? Lists.<VisibilityWindow>newArrayList() : resultVO.getTimeWindows())
                        .createTime(new Date())
                        .updateTime(new Date())
                        .type(link.getType())
                        .name(link.getName())
                        .build());
            }
        } else {
            relationRepository.updateRelation(startTime, endTime, resultVO.getTimeWindows(), Status.ENABLE, relation.getId());
        }
    }

    /**
     * 新增实体节点：适用于实体增量同步场景
     * <p>
     * 与数据湖 CDC 链路共用 {@link #upsertEntityNode}：节点按归一化主键判存，命中则只刷新展示名，
     * 因此重复调用同一入参只覆盖、不会产生第二份节点。实例关系边不在这里生成，只在关系真实成立或人工激活时落库。
     *
     * @param ontologyUniqueIdentifier 对象id
     * @param entityPropertyMap        实体属性（列名 -> 列值，列名大小写不敏感）
     */
    @Override
    public void completeEntityNode(String ontologyUniqueIdentifier, Map<String, Object> entityPropertyMap) {
        //创建实体节点
        var primaryProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));

        //未绑定主键数据源
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceId())) {
            return;
        }

        var entityPrimaryKeyValue = OntologyInstanceConverter.getColumnValue(
                entityPropertyMap, primaryProperty.getDatasourceColumnName());
        if (entityPrimaryKeyValue == null) {
            log.warn("对象[{}]补全实体节点失败：主键列[{}]在入参中取值为空",
                    ontologyUniqueIdentifier, primaryProperty.getDatasourceColumnName());
            return;
        }

        // 名称键与主键同表时才取名称列，缺省回退主键值，与实例文档 name 同口径
        var displayName = entityPrimaryKeyValue.toString();
        var titleProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsTitleKey, 1));
        if (titleProperty != null
                && StringUtils.equals(titleProperty.getDatasourceId(), primaryProperty.getDatasourceId())) {
            var titleValue = OntologyInstanceConverter.getColumnValue(
                    entityPropertyMap, titleProperty.getDatasourceColumnName());
            if (titleValue != null) {
                displayName = titleValue.toString();
            }
        }

        upsertEntityNode(ontologyUniqueIdentifier, primaryProperty.getDatasourceId(),
                entityPrimaryKeyValue, displayName);
    }

    @Override
    public void deleteEntityNodeAndRelations(String ontologyUniqueIdentifier, Object entityPrimaryKey) {
        var relations = relationRepository.queryAllRelationsByEntities(ontologyUniqueIdentifier, Lists.newArrayList(entityPrimaryKey));
        if (CollectionUtils.isNotEmpty(relations)) {
            // deleteByIds 过滤的是 _id，取 getId()（映射 _key）会一条都删不掉
            relationRepository.deleteByIds(relations.stream().map(EntityRelation::getArangoId).collect(Collectors.toList()));
        }

        var node = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(ontologyUniqueIdentifier, entityPrimaryKey);
        if (node != null) {
            nodeRepository.delete(node);
        }
    }

    @Override
    public boolean upsertEntityNode(String ontologyUniqueIdentifier, String tableName,
                                    Object primaryKeyValue, String displayName) {
        if (primaryKeyValue == null) {
            return false;
        }
        var normalizedPk = normalizePrimaryKey(primaryKeyValue);
        var exist = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(ontologyUniqueIdentifier, normalizedPk);
        if (exist != null) {
            if (!StringUtils.equals(exist.getDisplayName(), displayName)) {
                exist.setDisplayName(displayName);
                exist.setUpdateTime(new Date());
                nodeRepository.save(exist);
            }
            return false;
        }
        nodeRepository.save(buildEntityNode(ontologyUniqueIdentifier, tableName, normalizedPk, displayName));
        return true;
    }

    @Override
    public int upsertEntityNodes(String ontologyUniqueIdentifier, String tableName, Map<Object, String> pkToDisplayName) {
        if (MapUtils.isEmpty(pkToDisplayName)) {
            return 0;
        }
        // 一次捞该对象现存节点建索引，避免逐行回读图库
        var existNodes = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .filter(v -> v.getPrimaryKey() != null)
                .collect(Collectors.toMap(v -> pkKey(v.getPrimaryKey()), v -> v, (a, b) -> a));
        var staged = Lists.<EntityNode>newArrayList();
        for (var entry : pkToDisplayName.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            var normalizedPk = normalizePrimaryKey(entry.getKey());
            var existNodeKey = pkKey(normalizedPk);
            var exist = existNodes.get(existNodeKey);
            if (exist != null) {
                if (!StringUtils.equals(exist.getDisplayName(), entry.getValue())) {
                    exist.setDisplayName(entry.getValue());
                    exist.setUpdateTime(new Date());
                    staged.add(exist);
                }
                continue;
            }
            var created = buildEntityNode(ontologyUniqueIdentifier, tableName, normalizedPk, entry.getValue());
            existNodes.put(existNodeKey, created);
            staged.add(created);
        }
        if (CollectionUtils.isNotEmpty(staged)) {
            nodeRepository.batchSave(staged);
        }
        return staged.size();
    }

    @Override
    public int deleteEntityNodeOrphans(String ontologyUniqueIdentifier, Collection<Object> keepPrimaryKeyValues) {
        var keepPkKeys = keepPrimaryKeyValues == null ? Sets.<String>newHashSet()
                : keepPrimaryKeyValues.stream().filter(Objects::nonNull)
                        .map(pk -> pkKey(normalizePrimaryKey(pk))).collect(Collectors.toSet());
        var orphans = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .filter(node -> node.getPrimaryKey() != null && !keepPkKeys.contains(pkKey(node.getPrimaryKey())))
                .collect(Collectors.toList());
        return deleteGraphNodes(orphans);
    }

    /**
     * 删除给定节点及其全部相邻关系边（含已激活的边），先删边再删节点，避免留下悬空边。
     */
    private int deleteGraphNodes(List<EntityNode> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return 0;
        }
        var relations = relationRepository.findByFrom(nodes);
        relations.addAll(relationRepository.findByTo(nodes));
        if (CollectionUtils.isNotEmpty(relations)) {
            relationRepository.deleteByIds(relations.stream().map(EntityRelation::getArangoId).collect(Collectors.toList()));
        }
        nodeRepository.deleteByIds(nodes.stream().map(EntityNode::getArangoId).collect(Collectors.toList()));
        return nodes.size();
    }

    /**
     * 图库主键归一化：数据湖 bigint 经 JDBC 是 Long、经 Debezium JSON 可能是 Integer，
     * 与 {@link #syncNodes} 的转换口径保持一致，否则同一实例会被建出重复节点。
     */
    private Object normalizePrimaryKey(Object primaryKeyValue) {
        if (primaryKeyValue == null) {
            return null;
        }
        if (primaryKeyValue instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                return str;
            }
        }
        if (primaryKeyValue instanceof Integer) {
            return Long.parseLong(primaryKeyValue.toString());
        }
        return primaryKeyValue;
    }

    private EntityNode buildEntityNode(String ontologyUniqueIdentifier, String tableName,
                                       Object primaryKeyValue, String displayName) {
        var now = new Date();
        return EntityNode.builder()
                .ontologyUniqIdentifier(ontologyUniqueIdentifier)
                .primaryKey(primaryKeyValue)
                .tableName(tableName)
                .displayName(displayName)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    @Transactional(value = "datalakeTransactionManager")
    @Override
    public void generateEntities(EntityGenerateParam param) {
        var ontologyMeta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
        PreconditionUtils.checkArgument(ontologyMeta.getCanGenerateEntity(), "该对象不能生成实体对象", ResultCode.NO_PERMISSION, HttpStatus.FORBIDDEN);

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
        // 补全实体节点
        for (int i = 0; i < param.getCount(); i++) {
            var pkValue = generateIds.get(i);
            var entityPropertyMap = columnValues.get(i);
            entityPropertyMap.put(pkProp.getDatasourceColumnName(), pkValue);
            completeEntityNode(param.getOntologyIdentifier(), entityPropertyMap);
        }
    }


    @Override
    public Page<List<EntityPropertyGenericQueryVO>> genericQuery(EntityPropertyGenericQueryParam param) {
        // 1. 校验查询属性是否存在或者是否关联了数据源
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));
        // 查询主键是否存在且关联了数据源
        var pkProp = props.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst().orElse(null);
        PreconditionUtils.checkArgument(pkProp != null && StringUtils.isNotEmpty(pkProp.getDatasourceId()), "主键属性不存在或未关联数据源", HttpStatus.BAD_REQUEST);
        // 校验 schema 不为空
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(pkProp.getDatasourceSchema()), 
                "对象数据源 schema 为空，请检查数据源配置", HttpStatus.BAD_REQUEST);
        // 同一对象的所有数据源表都在对象所属空间的 schema 下，属性行的 datasource_schema 不可靠（可能为空串或残留 'public'），统一以主表 schema 为准
        var mainSchema = pkProp.getDatasourceSchema();
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
        boolean hasAggregation = false;
        for (OntologySelectPropertyParam selectProp : param.getSelectProperties()) {
            if (selectBuilder.length() > 0) {
                selectBuilder.append(",");
            }
            var prop = propMap.get(selectProp.getPropertyApiName());
            var aliasKey = StringUtils.isNotEmpty(selectProp.getAlias()) ? selectProp.getAlias() : selectProp.getPropertyApiName();
            selectParamMap.put(aliasKey, selectProp);
            var columnRef = wrapColumnRef(mainSchema, prop.getDatasourceId(), prop.getDatasourceColumnName());

            if (selectProp.getAggFunc() != null) {
                hasAggregation = true;
                switch (selectProp.getAggFunc()) {
                    case COUNT:
                    case MAX:
                    case MIN:
                    case DISTINCT:
                        selectBuilder.append(selectProp.getAggFunc().getValue()).append("(").append(columnRef).append(")");
                        break;
                    case SUM:
                    case AVG:
                        selectBuilder.append(selectProp.getAggFunc().getValue()).append("(")
                                .append(toNumericExpr(columnRef, prop.getPropertyType())).append(")");
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
        fromBuilder.append(wrapTableRef(mainSchema, mainDs));
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
            fromBuilder.append(" LEFT JOIN ").append(wrapTableRef(mainSchema, dsId))
                    .append(" ON ").append(wrapColumnRef(mainSchema, mainDs, mapping.getSourceColumnName())).append(" = ")
                    .append(wrapColumnRef(mainSchema, dsId, mapping.getTargetColumnName()));
            joinedTables.add(dsId);
        }
        // 5. 构建 WHERE 部分
        // 先校验过滤值类型（属性存在性 + 数值/布尔/日期可解析），拦截空串等脏值，
        // 否则数值型空串会在 formatSqlValue 拼出 "col = " 残缺 SQL，抛数据库层语法错误
        if (param.getFilters() != null) {
            validateFilterGroup(param.getFilters(), propMap);
        }
        var whereBuilder = new StringBuilder();
        if (param.getFilters() != null && CollectionUtils.isNotEmpty(param.getFilters().getChildren())) {
            var whereClause = buildWhereClause(param.getFilters(), propMap, mainSchema);
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
                groupByBuilder.append(wrapColumnRef(mainSchema, prop.getDatasourceId(), prop.getDatasourceColumnName()));
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
                orderByBuilder.append(wrapColumnRef(mainSchema, prop.getDatasourceId(), prop.getDatasourceColumnName())).append(" ").append(orderBy.getSort().name());
                first = false;
            }
        }
        // 8. 组装SQL并执行
        var pageSize = param.getPageSize();
        var offset = (param.getPageNum() - 1) * pageSize;
        // base SQL
        var baseSql = "SELECT " + selectBuilder + " FROM " + fromBuilder + whereBuilder + groupByBuilder + orderByBuilder;
        
        long total;
        String dataSql;
        if (hasAggregation) {
            // 聚合查询不需要分页，直接返回结果
            total = 1;
            dataSql = baseSql;
        } else {
            // 计数SQL
            var countSql = "SELECT COUNT(*) FROM (" + baseSql + ") AS t";
            total = objectMapper.queryCountBySql(countSql);
            if (total == 0) {
                return new Page<>(param.getPageNum(), pageSize, 0);
            }
            // 数据SQL
            dataSql = baseSql + " LIMIT " + pageSize + " OFFSET " + offset;
        }
        List<Map<String, Object>> resultMaps;
        try {
            resultMaps = objectMapper.queryBySql(dataSql);
        } catch (DataAccessException e) {
            log.error("通用查询执行失败, sql: {}", dataSql, e);
            if (StringUtils.defaultString(e.getMostSpecificCause().getMessage())
                    .contains("invalid input syntax for type numeric")) {
                throw new BusinessException("聚合列存在非数字数据，无法转换为数值类型", HttpStatus.BAD_REQUEST);
            }
            throw e;
        }
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
                // row 为 null 时（聚合无匹配行），value 为 null
                Object value = (row != null) ? row.get(column) : null;
                voList.add(EntityPropertyGenericQueryVO.builder()
                        .propertyApiName(selectProp.getPropertyApiName())
                        .propertyDisplayName(prop != null ? prop.getDisplayName() : selectProp.getPropertyApiName())
                        .aggFunc(selectProp.getAggFunc())
                        .alias(selectProp.getAlias())
                        .value(value)
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

    /**
     * SUM/AVG 可直接下推到数据库的数值属性类型。
     */
    private static final Set<OntologyDataTypeEnum> NUMERIC_PROPERTY_TYPES = EnumSet.of(
            OntologyDataTypeEnum.Byte, OntologyDataTypeEnum.Short, OntologyDataTypeEnum.Int,
            OntologyDataTypeEnum.Long, OntologyDataTypeEnum.Float, OntologyDataTypeEnum.Double,
            OntologyDataTypeEnum.Decimal);

    private static String toNumericExpr(String columnRef, OntologyDataTypeEnum propType) {
        if (propType != null && NUMERIC_PROPERTY_TYPES.contains(propType)) {
            return columnRef;
        }
        return "(NULLIF((" + columnRef + ")::text, '')::numeric)";
    }

    private String buildWhereClause(FilterGroupParam group, Map<String, OntologyProperty> propMap, String schema) {
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
                String filterClause = buildFilterClause(node.getFilter(), propMap, schema);
                if (StringUtils.isNotEmpty(filterClause)) {
                    sb.append(filterClause);
                }
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                String subGroup = buildWhereClause(node.getGroup(), propMap, schema);
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

    private String buildFilterClause(PropertyFilterParam filter, Map<String, OntologyProperty> propMap, String schema) {
        OntologyProperty prop = propMap.get(filter.getPropertyApiName());
        PreconditionUtils.checkArgument(prop != null && StringUtils.isNotEmpty(prop.getDatasourceId()),
                "过滤属性" + filter.getPropertyApiName() + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);

        String columnRef = wrapColumnRef(schema, prop.getDatasourceId(), prop.getDatasourceColumnName());
        QueryOpEnum op = filter.getOp() != null ? filter.getOp() : QueryOpEnum.EQ;
        
        // 优先使用 filter 声明的 dataType，否则使用属性定义的类型
        OntologyDataTypeEnum effectiveType = getEffectiveType(filter.getDataType(), prop.getPropertyType());

        switch (op) {
            case EQ:
                return columnRef + " = " + formatSqlValue(filter.getValue(), effectiveType);
            case NE:
                return columnRef + " != " + formatSqlValue(filter.getValue(), effectiveType);
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
                    inSb.append(formatSqlValue(filter.getValues().get(i), effectiveType));
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
                    notInSb.append(formatSqlValue(filter.getValues().get(i), effectiveType));
                }
                notInSb.append(")");
                return notInSb.toString();
            case BETWEEN:
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(filter.getValues()) && filter.getValues().size() >= 2,
                        "BETWEEN 需要提供两个值", HttpStatus.BAD_REQUEST);
                return columnRef + " BETWEEN " + formatSqlValue(filter.getValues().get(0), effectiveType)
                        + " AND " + formatSqlValue(filter.getValues().get(1), effectiveType);
            case NOT_BETWEEN:
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(filter.getValues()) && filter.getValues().size() >= 2,
                        "NOT BETWEEN 需要提供两个值", HttpStatus.BAD_REQUEST);
                return columnRef + " NOT BETWEEN " + formatSqlValue(filter.getValues().get(0), effectiveType)
                        + " AND " + formatSqlValue(filter.getValues().get(1), effectiveType);
            case GT:
                return columnRef + " > " + formatSqlValue(filter.getValue(), effectiveType);
            case GE:
                return columnRef + " >= " + formatSqlValue(filter.getValue(), effectiveType);
            case LT:
                return columnRef + " < " + formatSqlValue(filter.getValue(), effectiveType);
            case LE:
                return columnRef + " <= " + formatSqlValue(filter.getValue(), effectiveType);
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

    /**
     * 获取有效的类型：优先使用 filter 声明的 dataType，否则使用属性定义的类型。
     */
    private OntologyDataTypeEnum getEffectiveType(String dataType, OntologyDataTypeEnum propType) {
        if (StringUtils.isNotEmpty(dataType)) {
            switch (dataType.toUpperCase()) {
                case "NUMBER":
                    return OntologyDataTypeEnum.Double;
                case "BOOLEAN":
                    return OntologyDataTypeEnum.Bool;
                case "STRING":
                default:
                    return OntologyDataTypeEnum.String;
            }
        }
        return propType;
    }

    private String formatSqlValue(Object value, OntologyDataTypeEnum type) {
        if (value == null) {
            return "NULL";
        }
        if (type == OntologyDataTypeEnum.Float || type == OntologyDataTypeEnum.Double
                || type == OntologyDataTypeEnum.Long || type == OntologyDataTypeEnum.Int) {
            String strVal = value.toString();
            if (StringUtils.isBlank(strVal)) {
                throw new BusinessException("数值型过滤值不能为空", HttpStatus.BAD_REQUEST);
            }
            return strVal;
        }
        if (type == OntologyDataTypeEnum.Bool) {
            return Boolean.TRUE.equals(value) ? "TRUE" : "FALSE";
        }
        String escaped = value.toString().replace("'", "''");
        return StringUtils.wrap(escaped, "'");
    }


    private void deleteExistEntities(OntologyProperty pkProp) {
        /* 删除对象下已有实体
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
                //关联表属性：按 storageGroup 组织为行对象列表
                Map<String, List<Map<String, Object>>> relatedTables = null;
                var pkKey = pkVal == null ? null : String.valueOf(pkVal);
                if (pkKey != null) {
                    for (var entry : joinGroups.entrySet()) {
                        var table = entry.getKey();
                        var grouped = entry.getValue();
                        var matched = grouped.get(pkKey);
                        if (CollectionUtils.isEmpty(matched)) {
                            continue;
                        }
                        var joinKey = joinKeyByTable.get(table);
                        var rowMaps = matched.stream().<Map<String, Object>>map(m -> {
                            Map<String, Object> rowMap = new LinkedHashMap<>();
                            m.forEach((k, v) -> {
                                if (!StringUtils.equals(k, joinKey)) {
                                    rowMap.put(k, v);
                                }
                            });
                            return rowMap;
                        }).collect(Collectors.toList());
                        if (relatedTables == null) {
                            relatedTables = Maps.newLinkedHashMap();
                        }
                        relatedTables.put(table, rowMaps);
                    }
                }
                var titleVal = titleApiName == null ? null : properties.get(titleApiName);
                var displayName = titleVal != null ? String.valueOf(titleVal)
                        : (pkVal == null ? null : String.valueOf(pkVal));
                exportNodes.add(EntityNodeExportDTO.builder()
                        .primaryKey(pkVal)
                        .displayName(displayName)
                        .properties(properties)
                        .relatedTables(relatedTables)
                        .build());
            }
        } catch (Exception e) {
            log.error("导出对象 {} 实例数据失败，数据源不可达或查询异常", ontologyUniqueIdentifier, e);
            return emptyResult;
        }

        return OntologyInstancesExportDTO.builder()
                .nodes(exportNodes)
                .build();
    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public Map<String, Object> importInstances(String ontologyUniqueIdentifier, OntologyInstancesExportDTO instances) {
        var pkMapping = Maps.<String, Object>newLinkedHashMap();
        if (instances == null || CollectionUtils.isEmpty(instances.getNodes())) {
            return pkMapping;
        }
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        if (CollectionUtils.isEmpty(ontologyProperties)) {
            log.warn("导入对象 {} 实例数据跳过：对象无属性", ontologyUniqueIdentifier);
            return pkMapping;
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

        //保留导出节点与实体的配对：createEntities 返回的主键与入参同序，据此回传导出主键到新主键的映射
        var entities = Lists.<EntityCreateParam.Entity>newArrayList();
        var sourceNodes = Lists.<EntityNodeExportDTO>newArrayList();
        instances.getNodes().forEach(node -> {
            var entity = buildEntityFromNode(node, propertyMap, pkApiName);
            if (entity == null) {
                return;
            }
            entities.add(entity);
            sourceNodes.add(node);
        });
        if (CollectionUtils.isEmpty(entities)) {
            log.warn("导入对象 {} 实例数据跳过：无有效节点（属性未绑定数据源或缺少主存储分组属性）", ontologyUniqueIdentifier);
            return pkMapping;
        }
        //复用实体创建逻辑：写数据湖主表 + 关联表（id 由 SERIAL 生成）
        var newPrimaryKeys = createEntities(EntityCreateParam.builder()
                .ontologyIdentifier(ontologyUniqueIdentifier)
                .entityList(entities)
                .build());
        for (var i = 0; i < entities.size() && i < newPrimaryKeys.size(); i++) {
            var sourceKey = pkKey(sourceNodes.get(i).getPrimaryKey());
            if (sourceKey != null) {
                pkMapping.put(sourceKey, newPrimaryKeys.get(i));
            }
        }
        saveImportedEntityNodes(ontologyUniqueIdentifier, sourceNodes, newPrimaryKeys);
        return pkMapping;
    }

    /**
     * 实例落库后直接用新主键补齐图库节点，不等后续的 createEntityNodes 回查数据湖物理表。
     *
     * <p>实例关系边必须挂在图库已有的 {@code node} 上，而回查依赖数据湖可见性：同一次导入请求里刚写入的
     * 实例行未必查得到，节点建不出来，边就全部跳过。主键类型与 {@code syncNodes} 对齐（Integer 归一为 Long），
     * 避免两条路径为同一实例建出重复节点。</p>
     */
    private void saveImportedEntityNodes(String ontologyUniqueIdentifier,
                                         List<EntityNodeExportDTO> sourceNodes,
                                         List<Object> newPrimaryKeys) {
        if (CollectionUtils.isEmpty(sourceNodes) || CollectionUtils.isEmpty(newPrimaryKeys)) {
            return;
        }
        var primaryProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceId())) {
            log.warn("导入对象 {} 图库节点跳过：主键属性未绑定数据源", ontologyUniqueIdentifier);
            return;
        }
        var existPks = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .map(node -> pkKey(node.getPrimaryKey())).collect(Collectors.toSet());
        var nodes = Lists.<EntityNode>newArrayList();
        var size = Math.min(sourceNodes.size(), newPrimaryKeys.size());
        for (var i = 0; i < size; i++) {
            var primaryKey = newPrimaryKeys.get(i);
            if (primaryKey == null) {
                continue;
            }
            if (primaryKey instanceof Integer) {
                primaryKey = Long.parseLong(primaryKey.toString());
            }
            if (existPks.contains(pkKey(primaryKey))) {
                continue;
            }
            nodes.add(EntityNode.builder()
                    .ontologyUniqIdentifier(ontologyUniqueIdentifier)
                    .primaryKey(primaryKey)
                    .tableName(primaryProperty.getDatasourceId())
                    .displayName(StringUtils.defaultIfBlank(sourceNodes.get(i).getDisplayName(), pkKey(primaryKey)))
                    .build());
        }
        if (CollectionUtils.isNotEmpty(nodes)) {
            nodeRepository.batchSave(nodes);
        }
        log.info("导入对象 {} 图库节点补齐：实例 {} 个，新建节点 {} 个", ontologyUniqueIdentifier, size, nodes.size());
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
        //main 组属性：从 properties 中取，丢弃主键、未知/未绑定属性
        var mainValues = Maps.<String, Object>newLinkedHashMap();
        properties.forEach((apiName, value) -> {
            if (StringUtils.equals(apiName, pkApiName)) {
                return;
            }
            var prop = propertyMap.get(apiName);
            if (prop == null) {
                return;
            }
            var group = StringUtils.isEmpty(prop.getStorageGroup()) ? "main" : prop.getStorageGroup();
            if (!"main".equals(group)) {
                return;
            }
            mainValues.put(apiName, value);
        });

        var mainInfos = toPropertyInfos(mainValues, propertyMap, true);
        if (CollectionUtils.isEmpty(mainInfos)) {
            log.warn("导入实例节点跳过：无主存储分组(main)有效属性，primaryKey={}", node.getPrimaryKey());
            return null;
        }
        var entityProperties = Lists.<EntityCreateParam.EntityProperty>newArrayList();
        var mainRows = Lists.<List<EntityCreateParam.PropertyInfo>>newArrayList();
        mainRows.add(mainInfos);
        entityProperties.add(EntityCreateParam.EntityProperty.builder()
                .storageGroup("main")
                .props(mainRows)
                .build());

        //关联表数据：优先取 relatedTables（新格式：行对象列表），否则从 properties 里还原（旧格式：平行数组）
        var relatedTables = node.getRelatedTables();
        if (relatedTables != null && !relatedTables.isEmpty()) {
            relatedTables.forEach((storageGroup, rows) -> {
                if (CollectionUtils.isEmpty(rows)) {
                    return;
                }
                var propRows = Lists.<List<EntityCreateParam.PropertyInfo>>newArrayList();
                for (var rowMap : rows) {
                    var rowInfos = toPropertyInfos(rowMap, propertyMap, false);
                    if (CollectionUtils.isNotEmpty(rowInfos)) {
                        propRows.add(rowInfos);
                    }
                }
                if (CollectionUtils.isNotEmpty(propRows)) {
                    entityProperties.add(EntityCreateParam.EntityProperty.builder()
                            .storageGroup(storageGroup)
                            .props(propRows)
                            .build());
                }
            });
        } else {
            //旧格式兼容：非 main 组属性散落在 properties 里，值为 List 或标量
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
                if ("main".equals(group)) {
                    return;
                }
                groupedValues.computeIfAbsent(group, k -> Maps.newLinkedHashMap()).put(apiName, value);
            });
            groupedValues.forEach((group, values) -> {
                var rows = buildRelatedRows(values, propertyMap);
                if (CollectionUtils.isNotEmpty(rows)) {
                    entityProperties.add(EntityCreateParam.EntityProperty.builder()
                            .storageGroup(group)
                            .props(rows)
                            .build());
                }
            });
        }

        return EntityCreateParam.Entity.builder()
                .entityProperties(entityProperties)
                .build();
    }

    /**
     * 属性值映射转 PropertyInfo 列表。
     *
     * @param skipNull 为 true 时跳过 null 值（main 组插入用 Collectors.toMap，null 值会 NPE）
     */
    private List<EntityCreateParam.PropertyInfo> toPropertyInfos(Map<String, Object> values, 
                                                                 Map<String, OntologyProperty> propertyMap,
                                                                 boolean skipNull) {
        if (values == null || values.isEmpty()) {
            return Lists.newArrayList();
        }
        return values.entrySet().stream()
                .filter(e -> !(skipNull && e.getValue() == null))
                .map(e -> {
                    var prop = propertyMap.get(e.getKey());
                    var convertedValue = e.getValue();
                    if (prop != null && prop.getPropertyType() != null && convertedValue != null) {
                        try {
                            convertedValue = DataConverter.convert2DataType(convertedValue, prop.getPropertyType());
                        } catch (Exception ex) {
                            log.warn("属性 {} 类型转换失败，保留原值：{}", e.getKey(), ex.getMessage());
                        }
                    }
                    return EntityCreateParam.PropertyInfo.builder()
                            .propertyApiName(e.getKey())
                            .propertyValue(convertedValue)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 关联表（非 main 存储分组）属性值还原为多行：List 值按下标展开，标量值在各行重复；
     * 行数取该组内 List 的最大长度（至少 1）。
     */
    private List<List<EntityCreateParam.PropertyInfo>> buildRelatedRows(Map<String, Object> values,
                                                                        Map<String, OntologyProperty> propertyMap) {
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
                //类型转换
                var prop = propertyMap.get(e.getKey());
                if (prop != null && prop.getPropertyType() != null && v != null) {
                    try {
                        v = DataConverter.convert2DataType(v, prop.getPropertyType());
                    } catch (Exception ex) {
                        log.warn("属性 {} 类型转换失败，保留原值：{}", e.getKey(), ex.getMessage());
                    }
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

    // ────────────────── 实例数据属性查询（带条件校验） ──────────────────

    @Override
    public Page<List<EntityPropertyGenericQueryVO>> queryInstancePropertyData(EntityInstanceDataQueryParam param) {
        // 1. 查询该对象的所有属性
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyUniqueIdentifier()));
        var propMap = props.stream().collect(Collectors.toMap(OntologyProperty::getApiName, v -> v));

        // 2. 校验返回属性：必须存在且已关联数据源
        for (String apiName : param.getPropertyApiNames()) {
            var prop = propMap.get(apiName);
            PreconditionUtils.checkArgument(prop != null && StringUtils.isNotEmpty(prop.getDatasourceId()),
                    "属性 " + apiName + " 不存在或未关联数据源", HttpStatus.BAD_REQUEST);
        }

        // 3. 校验过滤条件（属性存在性 + 类型兼容性），不通过则直接抛异常
        if (param.getFilters() != null) {
            validateFilterGroup(param.getFilters(), propMap);
        }

        // 4. 转换为 EntityPropertyGenericQueryParam，复用已有查询逻辑
        var genericParam = new EntityPropertyGenericQueryParam();
        genericParam.setOntologyIdentifier(param.getOntologyUniqueIdentifier());
        genericParam.setSelectProperties(param.getPropertyApiNames().stream()
                .map(name -> OntologySelectPropertyParam.builder().propertyApiName(name).build())
                .collect(Collectors.toList()));
        genericParam.setFilters(param.getFilters());
        genericParam.setPageNum(param.getPageNum());
        genericParam.setPageSize(param.getPageSize());

        return genericQuery(genericParam);
    }

    /**
     * 递归校验过滤条件树：每个 filter 节点的属性必须属于该对象，
     * 值类型必须与属性定义的类型兼容。
     */
    private void validateFilterGroup(FilterGroupParam group, Map<String, OntologyProperty> propMap) {
        if (group == null || CollectionUtils.isEmpty(group.getChildren())) {
            return;
        }
        for (FilterNodeParam node : group.getChildren()) {
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                var filter = node.getFilter();
                var prop = propMap.get(filter.getPropertyApiName());
                PreconditionUtils.checkArgument(prop != null,
                        "过滤条件中的属性 " + filter.getPropertyApiName() + " 不属于该对象", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(StringUtils.isNotEmpty(prop.getDatasourceId()),
                        "过滤条件中的属性 " + filter.getPropertyApiName() + " 未关联数据源", HttpStatus.BAD_REQUEST);

                OntologyDataTypeEnum propType = prop.getPropertyType();
                QueryOpEnum op = filter.getOp() != null ? filter.getOp() : QueryOpEnum.EQ;

                // IS_NULL / IS_NOT_NULL 不需要校验值
                if (op == QueryOpEnum.IS_NULL || op == QueryOpEnum.IS_NOT_NULL) {
                    continue;
                }

                // LIKE 类操作只适用于字符串类型
                if (op == QueryOpEnum.LIKE || op == QueryOpEnum.LIKE_LEFT || op == QueryOpEnum.LIKE_RIGHT) {
                    PreconditionUtils.checkArgument(propType == OntologyDataTypeEnum.String,
                            "属性 " + filter.getPropertyApiName() + " 类型为 " + propType.getValue() + "，不支持 " + op + " 操作",
                            HttpStatus.BAD_REQUEST);
                }

                // 校验单值
                if (filter.getValue() != null) {
                    validateFilterValue(filter.getPropertyApiName(), filter.getValue(), propType, op);
                }
                // 校验多值（IN / BETWEEN）
                if (CollectionUtils.isNotEmpty(filter.getValues())) {
                    for (Object v : filter.getValues()) {
                        validateFilterValue(filter.getPropertyApiName(), v, propType, op);
                    }
                }
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                validateFilterGroup(node.getGroup(), propMap);
            }
        }
    }

    /**
     * 校验单个过滤值与属性类型是否兼容：
     * 数值类型必须可解析为数字，日期/时间戳类型必须可解析，布尔类型必须为 true/false。
     */
    private void validateFilterValue(String apiName, Object value, OntologyDataTypeEnum propType, QueryOpEnum op) {
        if (value == null) {
            return;
        }
        String strVal = value.toString();
        try {
            switch (propType) {
                case Int:
                    Integer.parseInt(strVal);
                    break;
                case Long:
                    Long.parseLong(strVal);
                    break;
                case Float:
                    Float.parseFloat(strVal);
                    break;
                case Double:
                case Decimal:
                    Double.parseDouble(strVal);
                    break;
                case Bool:
                    if (!"true".equalsIgnoreCase(strVal) && !"false".equalsIgnoreCase(strVal)) {
                        throw new BusinessException(
                                "属性 " + apiName + " 类型为 Boolean，值 \"" + strVal + "\" 不合法，应为 true 或 false",
                                HttpStatus.BAD_REQUEST);
                    }
                    break;
                case Date:
                    java.time.LocalDate.parse(strVal);
                    break;
                case Timestamp:
                    java.time.LocalDateTime.parse(strVal);
                    break;
                case String:
                    // 字符串类型接受任何值
                    break;
                default:
                    // 其他复杂类型（Array/Map/Ontology 等）不做比较操作，拒绝 GT/LT/GE/LE/BETWEEN
                    if (op == QueryOpEnum.GT || op == QueryOpEnum.GE || op == QueryOpEnum.LT
                            || op == QueryOpEnum.LE || op == QueryOpEnum.BETWEEN || op == QueryOpEnum.NOT_BETWEEN) {
                        throw new BusinessException(
                                "属性 " + apiName + " 类型为 " + propType.getValue() + "，不支持 " + op + " 比较操作",
                                HttpStatus.BAD_REQUEST);
                    }
                    break;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    "属性 " + apiName + " 类型为 " + propType.getValue() + "，值 \"" + strVal + "\" 无法解析",
                    HttpStatus.BAD_REQUEST);
        }
    }


}

