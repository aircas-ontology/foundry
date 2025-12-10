package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.util.DateUtils;
import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.model.param.EntityActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionParameter;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.TableFieldMapping;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityNodeRepository;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityRelationRepository;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableFieldMappingMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private OntologyLinkGroupMapper linkGroupMapper;

    @Resource
    private FunctionService functionService;

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

    private Configuration safeConfig = Configuration.builder().build().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);


    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private final ExpressionParser parser = new SpelExpressionParser();


    @Override
    public void updateNodesDisplayName(String ontologyUniqueIdentifier, String datasourceId, String primaryKeyColumnName, String titleKeyColumnName) {
        if (StringUtils.isEmpty(titleKeyColumnName)) {
            nodeRepository.updateDisplayNameEqualPrimaryKey(ontologyUniqueIdentifier);
            return;
        }
        var nodes = nodeRepository.findByOntologyUniqIdentifier(ontologyUniqueIdentifier);
        if (CollectionUtils.isEmpty(nodes)) {
            createNodes(ontologyUniqueIdentifier, datasourceId, primaryKeyColumnName, titleKeyColumnName);
            return;
        }
        var rows = objectMapper.queryPrimaryKeyAndTitleKeyValue(datasourceId, primaryKeyColumnName, titleKeyColumnName);
        var rowMap = rows.stream().collect(Collectors.toMap(
                v -> {
                    var primaryKeyValue = v.get(primaryKeyColumnName);
                    if (primaryKeyValue instanceof Integer) {
                        return ((Integer) primaryKeyValue).longValue();
                    } else {
                        return primaryKeyValue;
                    }
                },
                v -> v.get(titleKeyColumnName) != null ? v.get(titleKeyColumnName).toString() : "",
                (existingValue, newValue) -> existingValue // 处理键冲突，保留第一个值
        ));

        nodes.forEach(n -> {
            var pk = n.getPrimaryKey();
            n.setDisplayName(rowMap.get(pk));
        });
        nodeRepository.batchSave(nodes);
    }

    @Override
    public void createEntityRelations(OntologyLinkGroup link) {
        var fromNodes = nodeRepository.findByOntologyUniqIdentifier(link.getOntologyUniqueIdentifierFrom());
        var toNodes = nodeRepository.findByOntologyUniqIdentifier(link.getOntologyUniqueIdentifierTo());

        if (CollectionUtils.isNotEmpty(fromNodes) && CollectionUtils.isNotEmpty(toNodes)) {
            var relations = new ArrayList<EntityRelation>();
            fromNodes.forEach(from ->
                    toNodes.forEach(to ->
                            relations.add(EntityRelation.builder()
                                    .ontologyLinkId(link.getUniqueIdentifier())
                                    .from(from)
                                    .to(to)
                                    .status(OntologyLinkTypeEnum.mappingToStatus(link.getType()))
                                    .createTime(new Date())
                                    .updateTime(new Date())
                                    .type(link.getType())
                                    .name(link.getName())
                                    .build())
                    )
            );
            relationRepository.batchSave(relations);
        }
    }


    /**
     * 标题健需要和主键为同一个数据源
     *
     * @param ontologyUniqueIdentifier
     * @param datasourceId
     * @param primaryKeyColumnName
     * @param titleKeyColumnName
     */
    @Override
    public void createNodes(String ontologyUniqueIdentifier, String datasourceId, String primaryKeyColumnName, String titleKeyColumnName) {

        List<Map<String, Object>> rows = objectMapper.queryPrimaryKeyAndTitleKeyValue(datasourceId, primaryKeyColumnName, titleKeyColumnName);
        List<EntityNode> nodes = rows.stream().map(r -> EntityNode.builder()
                .ontologyUniqIdentifier(ontologyUniqueIdentifier)
                .primaryKey(r.get(primaryKeyColumnName))
                .tableName(datasourceId)
                .displayName(StringUtils.isEmpty(titleKeyColumnName) ? r.get(primaryKeyColumnName).toString() : r.get(titleKeyColumnName).toString())
                .build()).collect(Collectors.toList());
        nodeRepository.batchSave(nodes);
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

        var relations = relationRepository.queryEnableRelationsByEntity(ontologyUniqueIdentifier, entityPrimaryKey);

        if (CollectionUtils.isEmpty(relations)) {
            return Lists.newArrayList();
        }

        return relations.stream().map(v -> EntityLinkPropertyVO.builder()
                        .ontologyFrom(v.getFrom().getOntologyUniqIdentifier())
                        .ontologyTo(v.getTo().getOntologyUniqIdentifier())
                        .entityPrimaryKeyFrom(v.getFrom().getPrimaryKey())
                        .entityPrimaryKeyTo(v.getTo().getPrimaryKey())
                        .displayNameFrom(v.getFrom().getDisplayName())
                        .displayNameTo(v.getTo().getDisplayName())
                        .linkName(v.getName())
                        .linkType(v.getType())
                        .entityNodeFrom(v.getFrom().getId())
                        .entityNodeTo(v.getTo().getId())
                        .build())
                .collect(Collectors.toList());
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
        var primaryData = objectMapper.queryDataByPrimaryKey(pk.getDatasourceId(), pkColumns, pk.getDatasourceColumnName(), entityPrimaryKey);
        var propertyMap = propsMap.get(pk.getDatasourceId()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        var details = primaryData.get(0).entrySet().stream().<EntityPropertyDetailVO>map(entry -> {
            var colName = entry.getKey();
            var colValue = entry.getValue();
            var p = propertyMap.get(colName);
            return EntityPropertyDetailVO.builder()
                    .tag(p.getTag())
                    .propertyDisplayName(p.getDisplayName())
                    .propertyValues(Lists.newArrayList(colValue))
                    .propertyUniqIdentifier(p.getUniqueIdentifier())
                    .build();
        }).collect(Collectors.toList());
        res.addAll(details);

        propsMap.entrySet().forEach(entry -> {
            if (!entry.getKey().equals(pk.getDatasourceId())) {
                //查询关联表的实体数据
                var tableMapping = tableFieldMappingMapper.selectOne(new LambdaQueryWrapper<TableFieldMapping>()
                        .eq(TableFieldMapping::getSourceTableName, pk.getDatasourceId())
                        .eq(TableFieldMapping::getTargetTableName, entry.getKey()));
                if (tableMapping == null) {
                    return;
                }
                var orderBy = tableMetadataMapper.queryPrimaryKeyColumnName(entry.getKey());
                var columns = entry.getValue().stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList());
                var otherData = objectMapper.queryByJoinTable(tableMapping.getSourceTableName(),
                        tableMapping.getSourceColumnName(),
                        pk.getDatasourceColumnName(),
                        entityPrimaryKey,
                        tableMapping.getTargetTableName(),
                        columns,
                        tableMapping.getTargetColumnName(),
                        orderBy,
                        10
                );

                var otherColumns = otherData.get(0).keySet().stream().collect(Collectors.toList());
                var otherPropertyMap = propsMap.get(entry.getKey()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
                var detail = otherColumns.stream().<EntityPropertyDetailVO>map(col -> {
                    var p = otherPropertyMap.get(col);
                    var values = otherData.stream().map(v -> v.get(col)).collect(Collectors.toList());

                    return EntityPropertyDetailVO.builder()
                            .tag(p.getTag())
                            .propertyDisplayName(p.getDisplayName())
                            .propertyUniqIdentifier(p.getUniqueIdentifier())
                            .propertyValues(values)
                            .build();
                }).collect(Collectors.toList());
                res.addAll(detail);
            }
        });
        return res;

    }


    @Override
    public Page<EntityInfoVO> getEntities(String ontologyUniqueIdentifier, Integer pageNum, Integer pageSize) {
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
        var primaryPropMap = props.stream().filter(v -> v.getDatasourceId().equals(primaryDatasource)).collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        var titleKey = primaryPropMap.values().stream().filter(v -> v.getIsTitleKey() == 1).findFirst();

        //分页查询实体数据
        var records = objectMapper.pageQuery(primaryDatasource, primaryPropMap.values().stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList()), pageSize, (pageNum - 1) * pageSize);
        var total = objectMapper.queryCount(primaryDatasource);
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
                        .propertyDisplayName(primaryPropMap.get(entry.getKey()).getDisplayName())
                        .propertyValue(entry.getValue())
                        .build();
            }).collect(Collectors.toList());
            return EntityInfoVO.builder()
                    .properties(entityProps)
                    .displayName(StringUtils.isEmpty(title) ? entityPK.toString() : title)
                    .primaryKey(entityPK)
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
        List<String> executeResult = Lists.newArrayList();
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

        //存在关联关系
        if (link != null) {
            //如果行为影响关系，找到关联的本体
            var linkedOntology = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, link.getOntologyLinkUniqIdentifier()))
                    .getOntologyUniqueIdentifierTo();
            //获取关联本体下的所有实体详情
            var entities = getEntities(linkedOntology, 1, Integer.MAX_VALUE);
            var records = entities.getRecords();
            //实体数据必须存在
            if (CollectionUtils.isNotEmpty(records)) {
                //获取源本体实体属性详情
                var srcEntityDetailMap = getEntityDetail(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey())
                        .stream().collect(Collectors.toMap(v -> v.getPropertyUniqIdentifier(), v -> v.getPropertyValues()));
                //和关联本体下的所有实体计算关系
                records.stream().forEach(entity -> {
                    try {
                        var linkedEntityDetailMap = getEntityDetail(linkedOntology, entity.getPrimaryKey())
                                .stream().collect(Collectors.toMap(v -> v.getPropertyUniqIdentifier(), v -> v.getPropertyValues()));
                        // 合并两个实体，propertyId作为key
                        var mergedEntityDetailMap = Stream.of(srcEntityDetailMap, linkedEntityDetailMap)
                                .flatMap(map -> map.entrySet().stream())
                                .collect(Collectors.toMap(v -> v.getKey(), v -> v.getValue(),
                                        (list1, list2) -> {
                                            list1.addAll(list2);
                                            return list1;
                                        }));
                        //构造函数参数，list类型返回所有值，其他类型取第一个值
                        var functionResult = callFunctionAndUpdateProperty(functionInputParams, mappings, mergedEntityDetailMap, actionDetailVO, functionDetailVO, ontologyProperties, param.getEntityPrimaryKey());
                        executeResult.add(functionResult);
                        JsonNode jsonNode = jsonMapper.readTree(functionResult);
                        //根据函数的输出结果更新实体关系
                        var relation = relationRepository.queryRelationByFromNodeAndToNode(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey(), linkedOntology, entity.getPrimaryKey());
                        if (relation != null) {
                            var startTime = jsonNode.get("startTime").asText("");
                            var endTime = jsonNode.get("endTime").asText("");

                            //返回结果有可见窗口，直接更新relation startTime/endTime
                            if (StringUtils.isNotEmpty(startTime) && StringUtils.isNotEmpty(endTime)) {
                                relationRepository.updateRelation(DateUtils.parseISO8601(startTime), DateUtils.parseISO8601(endTime), relation.getStatus(), relation.getId());
                            } else {
                                //无可见窗口时，解析spel表达式,更新relation enable
                                Boolean expResult = evaluateJsonCondition(functionResult, link.getOntologyLinkFunctionParamExpression());
                                relationRepository.updateRelation(relation.getStartTime(), relation.getEndTime(), expResult ? Status.ENABLE : Status.DELETE, relation.getId());
                            }
                        }
                    } catch (Exception e) {
                        log.error("函数执行异常：" + entity.toString(), e);
                    }
                });
            }
        }
        //无关联关系
        else {
            //获取源本体实体属性详情
            var srcEntityDetailMap = getEntityDetail(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey())
                    .stream().collect(Collectors.toMap(v -> v.getPropertyUniqIdentifier(), v -> v.getPropertyValues()));
            //函数调用
            var functionResult = callFunctionAndUpdateProperty(functionInputParams, mappings, srcEntityDetailMap, actionDetailVO, functionDetailVO, ontologyProperties, param.getEntityPrimaryKey());
            executeResult.add(functionResult);
        }
        return jsonMapper.writeValueAsString(executeResult);
    }

    @Override
    @Transactional(transactionManager = "datalakeTransactionManager")
    public void updateEntity(EntityUpdateParam param) {
        var primaryKeyColumnName = tableMetadataMapper.queryPrimaryKeyColumnName(param.getDatasourceId());
        var columnMap = param.getColumnUpdates().stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v.getColumnValue()));
        objectMapper.updateObject(param.getDatasourceId(), columnMap, primaryKeyColumnName, param.getPrimaryKeyValue());
    }

    private Boolean evaluateJsonCondition(String jsonStr, String conditionExpr) {
        try {
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

    private String callFunctionAndUpdateProperty(List<FunctionParameterVO> functionInputParams,
                                                 List<ActionParamMappingVO> mappings,
                                                 Map<String, List<Object>> entityDetailMap,
                                                 OntologyActionDetailVO actionDetailVO,
                                                 FunctionDetailVO functionDetailVO,
                                                 List<OntologyProperty> ontologyProperties,
                                                 Object entityPrimaryKey) throws Exception {

        //构造函数参数，list类型返回所有值，其他类型取第一个值
        List<FunctionParameter> parameters = functionInputParams.stream().map(p -> {
            var mappingVO = mappings.stream().filter(m -> m.getFunctionParamId().equals(p.getParamId())).findFirst().get();
            Object value = null;
            // 如果本体没有给属性绑定数据源，则获取的values为null，函数执行失败
            List<Object> values = entityDetailMap.get(mappingVO.getPropertyUniqueIdentifier());
            if (CollectionUtils.isNotEmpty(values)) {
                value = p.getParamType().equals(FunctionParamTypeEnum.List) ? values : values.get(0);
            }
            return FunctionParameter.builder()
                    .paramName(p.getParamName())
                    .paramValue(value)
                    .build();
        }).collect(Collectors.toList());
        //执行函数
        var functionResult = functionService.executeFunction(FunctionExecuteParam.builder()
                .functionApi(actionDetailVO.getFunctionApi())
                .parameters(parameters)
                .build());
        //根据函数的输出结果更新源实体属性：通过json path获取value
        var functionOutputParam = functionDetailVO.getParams().stream()
                .filter(v -> v.getCategory().equals(FunctionParamCategoryEnum.OUTPUT))
                .findFirst().get();

        var output = mappings.stream().filter(m -> m.getFunctionParamId().equals(functionOutputParam.getParamId())).collect(Collectors.toList());
        JsonNode jsonNode = jsonMapper.readTree(functionResult);
        //primary datasource
        var primaryDatasource = ontologyProperties.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst().get().getDatasourceId();
        var propMap = ontologyProperties.stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v));
        List<EntityUpdateParam.ColumnUpdate> columnUpdates = Lists.newArrayList();
        Map<String, Map<String, Object>> insertMap = Maps.newHashMap();
        output.stream().forEach(out -> {
            Object actualValue = JsonPath.using(safeConfig).parse(jsonNode.toString()).read(out.getFunctionParamExpression());
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
            updateEntity(EntityUpdateParam.builder()
                    .columnUpdates(columnUpdates)
                    .primaryKeyValue(entityPrimaryKey)
                    .datasourceId(primaryDatasource)
                    .build());
        }
        //其他数据源数据insert
        if (MapUtils.isNotEmpty(insertMap)) {
            insertMap.entrySet().forEach(entry -> {
                //查询关联建，补全columns
                var tableName = entry.getKey();
                var mapping = tableFieldMappingMapper.selectOne(new LambdaQueryWrapper<TableFieldMapping>().eq(TableFieldMapping::getTargetTableName, tableName));
                entry.getValue().put(mapping.getTargetColumnName(), entityPrimaryKey);
                objectMapper.insertObject(tableName, entry.getValue());
            });
        }
        return functionResult;
    }
}
