package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.TableFieldMapping;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityNodeRepository;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityRelationRepository;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableFieldMappingMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
    private OntologyPropertyMapper propertyMapper;


    @Resource
    private OntologyActionMapper actionMapper;

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

        var rows = objectMapper.queryPrimaryKeyAndTitleKeyValue(datasourceId, primaryKeyColumnName, titleKeyColumnName);
        var nodes = rows.stream().map(r -> EntityNode.builder()
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
    public List<EntityActionVO> getEntityActionsByPrimaryKey(String ontologyUniqueIdentifier) {
        var actions = actionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        return actions.stream().map(action -> EntityActionVO.builder()
                .actionApi(action.getApi())
                .description(action.getDescription())
                .displayName(action.getDisplayName())
                .functionApi(action.getFunctionApi())
                .build()).collect(Collectors.toList());
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


}
