package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.client.EntityClient;
import com.aircas.ptr.foundry.ontology.common.param.EntityAssociateDatasourceParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityDetailQueryParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityRelationQueryParam;
import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityNodeRepository;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityRelationRepository;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
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
    private EntityClient entityClient;

    @Resource
    private OntologyMetaMapper metaMapper;

    @Resource
    private OntologyPropertyMapper propertyMapper;

    @Resource
    private OntologyLinkGroupMapper linkGroupMapper;

    @Resource
    private OntologyActionMapper actionMapper;

    @Resource
    private EntityRelationRepository relationRepository;

    @Resource
    private EntityNodeRepository nodeRepository;


    @Override
    public void deleteNodesAndRelationsByOntologyId(String ontologyUniqueIdentifier) {
        var nodes = nodeRepository.findByOntologyUniqIdentifier(ontologyUniqueIdentifier);
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }
        nodeRepository.deleteByIds(nodes.stream().map(v -> v.getArangoId()).collect(Collectors.toList()));
        var relations = relationRepository.findByFromIn(nodes);
        relations.addAll(relationRepository.findByToIn(nodes));
        if (CollectionUtils.isEmpty(relations)) {
            return;
        }
        relationRepository.deleteByIds(relations.stream().map(v -> v.getArangoId()).collect(Collectors.toList()));
    }

    @Override
    public void createNodesAndRelationsByParentOntology(String parentOntologyUniqueIdentifier, String newOntologyUniqueIdentifier) {
        // 查询所有 OntologyUniqIdentifier=parentOntologyId 的 EntityNode 对象
        var nodesToCopy = nodeRepository.findByOntologyUniqIdentifier(parentOntologyUniqueIdentifier);
        if (CollectionUtils.isEmpty(nodesToCopy)) {
            return;
        }
        // 复制这些对象并更新 tableName 为 newTableName
        var nodesToInsert = nodesToCopy.stream().map(node -> EntityNode.builder()
                .ontologyUniqIdentifier(newOntologyUniqueIdentifier)
                .tableName(node.getTableName())
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
        if (CollectionUtils.isEmpty(relationsToCopy)) {
            return;
        }
        // 复制这些边并更新 from 和 to 字段
        var relationsToInsert = relationsToCopy.stream()
                .map(relation -> EntityRelation.builder()
                        .type(relation.getType())
                        .name(relation.getName())
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

        var links = linkGroupMapper.selectList(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier).or()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifier));
        var ontologyIds = links.stream().flatMap(v -> Stream.of(v.getOntologyUniqueIdentifierFrom(), v.getOntologyUniqueIdentifierTo())).collect(Collectors.toList());

        var metas = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, ontologyIds));

        var relations = entityClient.queryRelation(EntityRelationQueryParam.builder()
                .tableName(metas.stream().filter(v -> v.getUniqueIdentifier().equals(ontologyUniqueIdentifier)).findFirst().get().getApiName())
                .primaryKeyValue(entityPrimaryKey)
                .build());
        if (CollectionUtils.isEmpty(relations)) {
            return Lists.newArrayList();
        }

        var apiMap = metas.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getUniqueIdentifier()));
        return relations.stream().map(v -> EntityLinkPropertyVO.builder()
                .ontologyFrom(apiMap.get(v.getNodeTableNameFrom()))
                .ontologyTo(apiMap.get(v.getNodeTableNameTo()))
                .entityPrimaryKeyFrom(v.getNodePrimaryKeyFrom())
                .entityPrimaryKeyTo(v.getNodePrimaryKeyTo())
                .displayNameFrom(v.getNodeNameFrom())
                .displayNameTo(v.getNodeNameTo())
                .linkName(v.getType())
                .entityNodeFrom(v.getNodeIdFrom())
                .entityNodeTo(v.getNodeIdTo())
                .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<EntityPropertyDetailVO> getEntityDetail(String ontologyUniqueIdentifier, Object entityPrimaryKey) {
        var meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier));
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        var propsMap = props.stream().collect(Collectors.groupingBy(v -> v.getDatasourceId()));
        var associateDatasource = propsMap.entrySet().stream().filter(v -> !v.getKey().equals(meta.getBackingDatasourceId())).<EntityAssociateDatasourceParam>map(entry -> {
            return EntityAssociateDatasourceParam.builder()
                    .datasourceId(entry.getKey())
                    .build();
        }).collect(Collectors.toList());

        var queryRecordDetail = entityClient.queryRecordDetail(EntityDetailQueryParam.builder()
                .associateDatasource(associateDatasource)
                .primaryKeyValue(entityPrimaryKey)
                .primaryTableName(meta.getApiName())
                .build());

        var tagMap = props.stream().collect(Collectors.groupingBy(v -> v.getTag()));
        var apiDisplayMap = props.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getDisplayName()));
        Map<String, List<Object>> detailMap = Maps.newHashMap();
        queryRecordDetail.forEach(detail -> {
            var names = detail.getPropertyName();
            var values = detail.getPropertyValues();
            for (var i = 0; i < names.size(); i++) {
                final int j = i;
                detailMap.put(names.get(i), values.stream().map(v -> v.get(j)).collect(Collectors.toList()));
            }
        });

        var res = tagMap.entrySet().stream().<EntityPropertyDetailVO>map(entry -> {
            var tag = entry.getKey();
            var displayNames = entry.getValue().stream().map(v -> apiDisplayMap.get(v.getApiName())).collect(Collectors.toList());
            var apiNames = entry.getValue().stream().map(v -> v.getApiName()).collect(Collectors.toList());

            int size = detailMap.get(apiNames.get(0)).size();

            List<List<Object>> propValues = Lists.newArrayList();
            for (int i = 0; i < size; i++) {
                List<Object> list = Lists.newArrayList();
                for (int j = 0; j < apiNames.size(); j++) {
                    list.add(detailMap.get(apiNames.get(j)).get(i));
                }
                propValues.add(list);
            }
            return EntityPropertyDetailVO.builder()
                    .propertyDisplayNames(displayNames)
                    .propertyValues(propValues)
                    .tag(tag)
                    .build();
        }).collect(Collectors.toList());
        return res;

    }


    @Override
    public Page<EntityInfoVO> getEntities(String ontologyUniqueIdentifier, Integer pageNum, Integer pageSize) {
        Page<EntityInfoVO> result = new Page<EntityInfoVO>().setSize(pageSize).setCurrent(pageNum);
        var meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier));
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier).eq(OntologyProperty::getDatasourceId, meta.getBackingDatasourceId()));
        if (CollectionUtils.isEmpty(props)) {
            return result;
        }
        var titleKey = props.stream().filter(v -> v.getIsTitleKey() == 1).findFirst().get().getApiName();
        var primaryKey = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst().get().getApiName();

        var propertyMap = props.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v));
        var entityVOPage = entityClient.queryRecords(meta.getApiName(), pageNum, pageSize);

        var records = entityVOPage.getRecords().stream().map(r -> {
            var valueMap = r.getProperties().stream().collect(HashMap::new, (m, p) -> m.put(p.getPropertyName(), p.getPropertyValue()), HashMap::putAll);
            var entityPropertyVOS = valueMap.entrySet().stream().<EntityPropertyVO>map(entry -> {
                var ontologyProperty = propertyMap.get(entry.getKey());
                return EntityPropertyVO.builder()
                        .propertyDisplayName(ontologyProperty.getDisplayName())
                        .propertyValue(entry.getValue())
                        .build();
            }).collect(Collectors.toList());

            return EntityInfoVO.builder()
                    .displayName(valueMap.get(titleKey).toString())
                    .primaryKey(valueMap.get(primaryKey))
                    .properties(entityPropertyVOS)
                    .build();
        }).collect(Collectors.toList());

        result.setCurrent(entityVOPage.getCurrent())
                .setSize(entityVOPage.getSize())
                .setTotal(entityVOPage.getTotal())
                .setRecords(records);
        return result;
    }


}
