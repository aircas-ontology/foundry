package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.CountTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyPropertyCategoryEnum;
import com.aircas.ptr.foundry.ontology.client.EntityClient;
import com.aircas.ptr.foundry.ontology.common.param.EntityAssociateDatasourceParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityDetailQueryParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityRelationQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityNodeParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityTableFieldParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.EntityInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityLinkPropertyVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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


    @Override
    public List<EntityLinkPropertyVO> getEntityLinksByPrimaryKey(String ontologyUniqueIdentifier,
                                                                 Object entityPrimaryKey) {

        var meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier));
        var relations = entityClient.queryRelation(EntityRelationQueryParam.builder()
                .tableName(meta.getApiName())
                .primaryKeyValue(entityPrimaryKey)
                .build());
        if (CollectionUtils.isEmpty(relations)) {
            return Lists.newArrayList();
        }
        return relations.stream().map(v -> EntityLinkPropertyVO.builder()
                .displayNameFrom(v.getNodeNameFrom())
                .displayNameTo(v.getNodeNameTo())
                .linkName(v.getType())
                .entityKeyFrom(v.getNodeIdFrom())
                .entityKeyTo(v.getNodeIdTo())
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
                    .count(CountTypeEnum.convert(OntologyPropertyCategoryEnum.getByValue(entry.getValue().get(0).getCategory())))
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
                    .primaryKey(valueMap.get(primaryKey).toString())
                    .properties(entityPropertyVOS)
                    .build();
        }).collect(Collectors.toList());

        result.setCurrent(entityVOPage.getCurrent())
                .setSize(entityVOPage.getSize())
                .setTotal(entityVOPage.getTotal())
                .setRecords(records);
        return result;
    }


    @Override
    public Boolean createEntityTable(String tableName, String tableComment, List<EntityTableFieldParam> fields) {
        return null;
    }

    @Override
    public Boolean deleteEntityTable(String tableName) {
        return null;
    }

    @Override
    public Boolean existsEntityTable(String tableName) {
        return null;
    }

    @Override
    public Integer countEntityTable(String tableName) {
        return null;
    }

    @Override
    public Integer batchInsertEntityTable(String tableName, List<Map<String, Object>> entities) {
        return null;
    }

    @Override
    public Boolean createEntityNode(String id, String name, String description, String category, String type) {
        return null;
    }

    @Override
    public Boolean createEntityNode(List<EntityNodeParam> nodes) {
        return null;
    }

    @Override
    public Boolean deleteEntityNode(String nodeId) {
        return null;
    }

    @Override
    public Boolean existsEntityNode(String nodeId) {
        return null;
    }
}
