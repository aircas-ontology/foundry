package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.FunctionExecuteResult;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.TableFieldMapping;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityNodeRepository;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityRelationRepository;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableFieldMappingMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionExecuteResultMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.SneakyThrows;
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
     * @param ontologyUniqueIdentifier
     * @param datasourceId
     * @param primaryKeyColumnName
     * @param titleKeyColumnName
     */
    @Override
    public void syncNodes(String ontologyUniqueIdentifier, String datasourceId, String primaryKeyColumnName, String titleKeyColumnName) {

        var existNodes = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier);
        var existNodesMap = existNodes.stream().collect(Collectors.toMap(v -> v.getPrimaryKey(), v -> v));

        List<Map<String, Object>> allRows = objectMapper.queryPrimaryKeyAndTitleKeyValue(datasourceId, primaryKeyColumnName, titleKeyColumnName);
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
        var primaryData = objectMapper.queryDataByPrimaryKey(pk.getDatasourceId(), pkColumns, pk.getDatasourceColumnName(), entityPrimaryKey);
        var propertyMap = propsMap.get(pk.getDatasourceId()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        var details = primaryData.get(0).entrySet().stream().<EntityPropertyDetailVO>map(entry -> {
            var colName = entry.getKey();
            var colValue = entry.getValue();
            var p = propertyMap.get(colName);
            return EntityPropertyDetailVO.builder()
                    .tag(p.getTag())
                    .primaryCategory(p.getPrimaryCategory().getName())
                    .secondaryCategory(p.getSecondaryCategory())
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
                            .primaryCategory(p.getPrimaryCategory().getName())
                            .secondaryCategory(p.getSecondaryCategory())
                            .entityPrimaryKey(entityPrimaryKey)
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
        var infoDTO = initActionContextInfoDTO(param.getOntologyUniqueIdentifier(), param.getActionApi());
        //获取行为关联关系下的本体的所有实体详情
        List<List<EntityPropertyDetailVO>> linkedEntities = getLinkedEntities(infoDTO);
        return executeEntityAction(param, infoDTO, linkedEntities);
    }

    public List<List<EntityPropertyDetailVO>> getLinkedEntities(ActionContextInfoDTO infoDTO) {
        if (infoDTO.getLink() != null) {
            //如果行为影响关系，找到关联的本体
            var linkedOntology = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, infoDTO.getLink().getOntologyLinkUniqIdentifier()))
                    .getOntologyUniqueIdentifierTo();
            infoDTO.setLinkToOntologyUniqueIdentifier(linkedOntology);
            //获取关联本体下的所有实体详情
            var entities = getEntities(linkedOntology, "", "", 1, Integer.MAX_VALUE, false);
            var records = entities.getRecords();

            var linkedEntities = records.stream().map(entity -> getEntityDetail(linkedOntology, entity.getPrimaryKey()))
                    .collect(Collectors.toList());

            return linkedEntities;
        }
        return null;
    }


    public ActionContextInfoDTO initActionContextInfoDTO(String ontologyUniqueIdentifier,
                                                         String actionApi) {

        var actionDetailVO = actionService.getActionByApi(actionApi);
        var functionDetailVO = functionService.getFunctionDetailByApi(actionDetailVO.getFunctionApi());
        var link = actionDetailVO.getLinkMapping();
        var mappings = actionDetailVO.getMappingIns();
        //src本体属性
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));

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

        if (primaryProperty != null && StringUtils.isNotEmpty(primaryProperty.getDatasourceId())) {
            syncNodes(ontologyIdentifier, primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
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
            insertMap.entrySet().forEach(entry -> {
                //查询关联建，补全columns
                var tableName = entry.getKey();
                var mapping = tableFieldMappingMapper.selectOne(new LambdaQueryWrapper<TableFieldMapping>().eq(TableFieldMapping::getTargetTableName, tableName));
                entry.getValue().put(mapping.getTargetColumnName(), actionContext.getEntityActionExecuteParam().getEntityPrimaryKey());
                objectMapper.insertObject(tableName, entry.getValue());
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
            if (relation != null) {
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


}
