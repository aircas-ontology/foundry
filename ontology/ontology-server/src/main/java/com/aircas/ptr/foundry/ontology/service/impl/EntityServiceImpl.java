package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.util.DateUtils;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.model.enums.*;
import com.aircas.ptr.foundry.ontology.model.param.*;
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
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.http.HttpStatus;
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

    @Resource
    private TaskProcessor taskProcessor;

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
    public void createEntityRelations(String linkUniqueIdentifier) {
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, linkUniqueIdentifier));
        PreconditionUtils.checkArgument(link != null, "本体关系不存在");
        var existRelations = relationRepository.queryRelationsByLinkId(link.getUniqueIdentifier());
        //关系已存在
        if (CollectionUtils.isNotEmpty(existRelations)) {
            return;
        }
        var fromNodes = nodeRepository.findByOntologyUniqIdentifier(link.getOntologyUniqueIdentifierFrom());
        var toNodes = nodeRepository.findByOntologyUniqIdentifier(link.getOntologyUniqueIdentifierTo());

        if (CollectionUtils.isNotEmpty(fromNodes) && CollectionUtils.isNotEmpty(toNodes)) {
            var relations = new ArrayList<EntityRelation>();
            var windows = new ArrayList<VisibilityWindow>();

            Date startTime = link.getType().equals(OntologyLinkTypeEnum.COMPOSITION) ? DateUtils.MIN_DATE : null;
            Date endTime = link.getType().equals(OntologyLinkTypeEnum.COMPOSITION) ? DateUtils.MAX_DATE : null;


            if (link.getType().equals(OntologyLinkTypeEnum.COMPOSITION)) {
                windows.add(VisibilityWindow.builder()
                        .startTime(DateUtils.MIN_DATE)
                        .endTime(DateUtils.MAX_DATE)
                        .build());
            }

            fromNodes.forEach(from ->
                    toNodes.forEach(to ->
                            relations.add(EntityRelation.builder()
                                    .ontologyLinkId(link.getUniqueIdentifier())
                                    .from(from)
                                    .to(to)
                                    .status(OntologyLinkTypeEnum.mappingToStatus(link.getType()))
                                    .timeWindows(windows)
                                    .startTime(startTime)
                                    .endTime(endTime)
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
            var relations = relationRepository.queryAllRelationsByEntities(p.getOntologyUniqueIdentifier(), p.getEntityPrimaryKeys());

            var map1 = relations.stream().collect(Collectors.groupingBy(v -> v.getFrom().getOntologyUniqIdentifier() + v.getFrom().getPrimaryKey()));
            var map2 = relations.stream().collect(Collectors.groupingBy(v -> v.getTo().getOntologyUniqIdentifier() + v.getTo().getPrimaryKey()));

            map2.forEach((key, list) -> map1.merge(key, list, (list1, list2) -> {
                list1.addAll(list2);
                return list1;
            }));
            p.getEntityPrimaryKeys().forEach(pk -> {
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
                    .propertyApiName(p.getApiName())
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
                        15
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
                            .propertyApiName(p.getApiName())
                            .propertyValues(values)
                            .build();
                }).collect(Collectors.toList());
                res.addAll(detail);
            }
        });
        return res;
    }


    @Override
    public Page<EntityInfoVO> getEntities(String ontologyUniqueIdentifier,
                                          String propertyName,
                                          Object propertyValue,
                                          Integer pageNum,
                                          Integer pageSize,
                                          Boolean needFilterVisibility) {
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
        var records = objectMapper.pageQuery(
                primaryDatasource,
                primaryPropMap.values().stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList()),
                columnName,
                propertyValue,
                pageSize,
                (pageNum - 1) * pageSize);
        var total = objectMapper.queryCount(primaryDatasource, columnName, propertyValue);

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
            var entities = getEntities(linkedOntology, "", "", 1, Integer.MAX_VALUE, false);
            var records = entities.getRecords();
            //实体数据必须存在
            if (CollectionUtils.isNotEmpty(records)) {
                //获取源本体实体属性详情
                var srcEntityDetailMap = getEntityDetail(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey())
                        .stream().collect(Collectors.toMap(v -> v.getPropertyUniqIdentifier(), v -> v.getPropertyValues()));

                //和关联本体下的所有实体计算关系
                var taskResults = taskProcessor.processTask(records,
                        partitionRecords -> {
                            return partitionRecords.stream().map(entity -> {
                                try {
                                    Map<String,List<Object>> linkedEntityDetailMap = getEntityDetail(linkedOntology, entity.getPrimaryKey())
                                            .stream().collect(Collectors.toMap(v -> v.getPropertyUniqIdentifier(), v -> v.getPropertyValues()));
                                    // 合并两个实体，propertyId作为key
                                    Map<String,List<Object>> mergedEntityDetailMap = Stream.of(srcEntityDetailMap, linkedEntityDetailMap)
                                            .flatMap(map -> map.entrySet().stream())
                                            .collect(Collectors.toMap(v -> v.getKey(), v -> v.getValue(),
                                                    (list1, list2) -> {
                                                        list1.addAll(list2);
                                                        return list1;
                                                    }));
                                    //构造函数参数，list类型返回所有值，其他类型取第一个值
                                    String functionResult = callFunctionAndUpdateProperty(functionInputParams, mappings, mergedEntityDetailMap, actionDetailVO, functionDetailVO, ontologyProperties, param.getEntityPrimaryKey());
                                    FunctionResultVO resultVO = jsonMapper.readValue(functionResult, new TypeReference<FunctionResultVO>() {
                                    });
                                    //根据函数的输出结果更新实体关系
                                    EntityRelation relation = relationRepository.queryRelationByFromNodeAndToNode(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey(), linkedOntology, entity.getPrimaryKey(), link.getOntologyLinkUniqIdentifier());
                                    if (relation != null) {
                                        //返回结果有可见窗口，直接更新relation startTime/endTime
                                        if (resultVO.getStartTime() != null && resultVO.getEndTime() != null) {
                                            relationRepository.updateRelation(resultVO.getStartTime(), resultVO.getEndTime(), resultVO.getTimeWindows(), Status.ENABLE, relation.getId());
                                        } else {
                                            //无可见窗口时，解析spel表达式,更新relation enable
                                            Boolean expResult = evaluateJsonCondition(functionResult, link.getOntologyLinkFunctionParamExpression());
                                            relationRepository.updateRelation(null, null, resultVO.getTimeWindows(), expResult ? Status.ENABLE : Status.DELETE, relation.getId());
                                        }
                                    }
                                    return functionResult;
                                } catch (Exception e) {
                                    log.error("函数执行异常：" + entity.toString(), e);
                                    return "";
                                }
                            }).collect(Collectors.toList());
                        },
                        20);
                executeResult.addAll(taskResults.stream().flatMap(v -> v.stream()).collect(Collectors.toList()));
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

        var nodes = getByByOntologyUniqIdentifier(ontologyIdentifier);

        if (primaryProperty == null && CollectionUtils.isEmpty(nodes)) {
            return;
        }
        //新增主键数据源
        else if (primaryProperty != null && CollectionUtils.isEmpty(nodes)) {
            if (StringUtils.isNotEmpty(primaryProperty.getDatasourceId())) {
                createNodes(ontologyIdentifier, primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
            }
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

    private String callFunctionAndUpdateProperty(List<FunctionParameterVO> functionInputParams,
                                                 List<ActionParamMappingVO> mappings,
                                                 Map<String, List<Object>> entityDetailMap,
                                                 OntologyActionDetailVO actionDetailVO,
                                                 FunctionDetailVO functionDetailVO,
                                                 List<OntologyProperty> ontologyProperties,
                                                 Object entityPrimaryKey) throws Exception {

        //构造函数参数，list类型返回所有值，其他类型取第一个值
        List<FunctionParameter> parameters = functionInputParams.stream().map(p -> {
            var mappingVO = mappings.stream().filter(m -> m.getFunctionParamId().equals(p.getParamId())).findFirst();
            if (!mappingVO.isPresent()) {
                return new FunctionParameter().setParamName(p.getParamName());
            }
            Object value = null;
            // 如果本体没有给属性绑定数据源，则获取的values为null，函数可能执行失败
            List<Object> values = entityDetailMap.get(mappingVO.get().getPropertyUniqueIdentifier());
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
