package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.DirectoryItem;
import com.aircas.ptr.foundry.model.po.OntologyChildLink;
import com.aircas.ptr.foundry.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyLinkService;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyChildLinkMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.geotools.util.MapEntry;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ObjectServiceImpl implements ObjectService {

    @Resource
    private final OntologyPropertyMapper ontologyPropertyMapper;

    @Resource
    private final ObjectMapper objectMapper;

    @Resource
    private final OntologyLinkGroupMapper ontologyLinkGroupMapper;

    @Resource
    private final OntologyChildLinkMapper ontologyChildLinkMapper;

    @Override
    public List<DirectoryItemVO> queryDirectories(String ontologyUniqueIdentifier) {
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        OntologyProperty primaryKeyProperty = null;
        OntologyProperty titleKeyProperty = null;
        for(OntologyProperty property: ontologyPropertyList) {
            if (property.getIsPrimaryKey() == 1) {
                primaryKeyProperty = property;
            }
            if (property.getIsTitleKey() == 1) {
                titleKeyProperty = property;
            }
        }
        if (primaryKeyProperty == null || titleKeyProperty == null) {
            return null;
        }
        if (primaryKeyProperty.getDatasourceId() != null &&
            primaryKeyProperty.getDatasourceId().equals(titleKeyProperty.getDatasourceId())) {
            String tableName = primaryKeyProperty.getDatasourceId();
            String primaryKeyColumnName = primaryKeyProperty.getDatasourceColumnName();
            String titleKeyColumnName = titleKeyProperty.getDatasourceColumnName();
            List<DirectoryItem>  items = objectMapper.queryDirectory(
                    tableName,
                    primaryKeyColumnName,
                    titleKeyColumnName
            );
            List<DirectoryItemVO> list = new ArrayList();
            for (DirectoryItem item : items){
                DirectoryItemVO directoryItemVO = new DirectoryItemVO();
                BeanUtils.copyProperties(item, directoryItemVO);
                list.add(directoryItemVO);
            }
            return list;
        }
        //对于primaryKey 和 titleKey位于不同的datasource的 先不支持
        return null;
    }

    @Override
    public ObjectValueVo queryObjectByPrimaryKey(String ontologyUniqueIdentifier, String primaryKey) {
        return queryByPrimaryKey(ontologyUniqueIdentifier, primaryKey);
    }

    @Override
    public ObjectWithLinkedInfoVO queryObjectWithLinkedInfoByPrimaryKey(String ontologyUniqueIdentifier, String primaryKey) {
        ObjectWithLinkedInfoVO objectWithLinkedInfoVO = new ObjectWithLinkedInfoVO();
        ObjectValueVo objectValueVo = queryByPrimaryKey(ontologyUniqueIdentifier, primaryKey);
        objectWithLinkedInfoVO.setObjectValue(objectValueVo);

        List<LinkedValueVo> linkedValueVoList = new ArrayList<>();
        objectWithLinkedInfoVO.setLinks(linkedValueVoList);
        List<OntologyLinkGroup> linksMetadata = getLinkedMetadata(ontologyUniqueIdentifier);
        for(int i = 0; i < linksMetadata.size(); i ++) {
            OntologyLinkGroup ontologyLinkGroup = linksMetadata.get(i);
            LinkedValueVo linkedValueVo = getLinkedValue(ontologyLinkGroup, objectValueVo);
            if (linkedValueVo != null) {
                linkedValueVoList.add(linkedValueVo);
            }
        }
        return objectWithLinkedInfoVO;
    }

    private ObjectValueVo queryByPrimaryKey(String ontologyUniqueIdentifier, String primaryKey) {
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        String primaryColumnName = getPrimaryKeyColumnName(ontologyPropertyList);
        List<ObjectValueVo> objectValueVoList = queryByColumnNameValue(ontologyPropertyList, primaryColumnName, primaryKey);
        if (objectValueVoList.size() == 0) {
            return null;
        }
        return objectValueVoList.get(0);
    }

    private List<ObjectValueVo> queryByColumnNameValue(String ontologyUniqueIdentifier, String columnName, String columnValue) {
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        return queryByColumnNameValue(ontologyPropertyList, columnName, columnValue);
    }

    private List<ObjectValueVo> queryByColumnNameValue(List<OntologyProperty> ontologyPropertyList, String columnName, String columnValue) {
        String sql = buildSQLByQueryKey(ontologyPropertyList, columnName, columnValue);
        if (sql == null) return null;
        List<Map<String, Object>> rawResult = objectMapper.queryAnySQL(sql);
        List<ObjectValueVo> ret = new ArrayList();
        if (rawResult.size() == 0) {
            return ret;
        }

        for (Map<String, Object> rawKeyValueMap: rawResult) {
            List<PropertyValueVO> propertyList = new ArrayList<>();
            String primaryKeyValue = null;
            String displayName = null;
            for (String key: rawKeyValueMap.keySet()) {
                PropertyValueVO propertyValueVO = new PropertyValueVO();
                propertyList.add(propertyValueVO);
                //index0 -> 0
                int propertyIndex = Integer.valueOf(key.substring(5));
                OntologyProperty property = ontologyPropertyList.get(propertyIndex);
                propertyValueVO.setIsTitleKey(property.getIsTitleKey());
                propertyValueVO.setIsPrimaryKey(property.getIsPrimaryKey());
                propertyValueVO.setDisplayName(property.getDisplayName());
                propertyValueVO.setDescription(property.getDescription());
                propertyValueVO.setPropertyType(property.getPropertyType());
                propertyValueVO.setApiName(property.getApiName());
                propertyValueVO.setUniqueIdentifier(property.getUniqueIdentifier());
                String value = rawKeyValueMap.get(key).toString();
                propertyValueVO.setValue(value);
                if (property.getIsPrimaryKey() == 1) {
                    primaryKeyValue = rawKeyValueMap.get(key).toString();
                }
                if (property.getIsTitleKey() == 1) {
                    displayName = rawKeyValueMap.get(key).toString();
                }
            }
            ObjectValueVo objectValueVo = new ObjectValueVo();
            objectValueVo.setProperties(propertyList);
            objectValueVo.setPrimaryKey(primaryKeyValue);
            objectValueVo.setDisplayName(displayName);
            ret.add(objectValueVo);
        }
        return ret;
    }

    private String getPrimaryKeyColumnName(List<OntologyProperty> ontologyPropertyList) {
        for (OntologyProperty property: ontologyPropertyList) {
            if (property.getIsPrimaryKey() == 1) {
                return property.getDatasourceColumnName();
            }
        }
        return null;
    }

    private List<OntologyLinkGroup> getLinkedMetadata(String ontologyUniqueIdentifier) {
        List<OntologyLinkGroup> linkGroups = new ArrayList<>();
        List<OntologyLinkGroup> forwardOntologyLinkGroups = ontologyLinkGroupMapper.selectByOntologyUniqueIdentifierFrom(ontologyUniqueIdentifier);
        linkGroups.addAll(forwardOntologyLinkGroups);

        List<OntologyLinkGroup> backwardOntologyLinkGroups = ontologyLinkGroupMapper.selectByOntologyUniqueIdentifierTo(ontologyUniqueIdentifier);
        linkGroups.addAll(backwardOntologyLinkGroups.stream().map(OntologyLinkGroup::revertForwardToBackward).collect(Collectors.toList()));
        return linkGroups;
    }

    private LinkedValueVo getLinkedValue(OntologyLinkGroup ontologyLinkGroup, ObjectValueVo objectValueVo) {
        LinkedValueVo linkedValueVo = new LinkedValueVo();
        OntologyChildLink backwardChildLink = ontologyChildLinkMapper.selectByPrimaryKey(ontologyLinkGroup.getBackwardChildLinkId());
        String linkDisplayName = backwardChildLink.getDisplayName();
        String apiName = backwardChildLink.getApiName();
        linkedValueVo.setName(linkDisplayName);
        linkedValueVo.setApiName(apiName);

        String propertyIdentifierFrom = ontologyLinkGroup.getPropertyUniqueIdentifierFrom();
        String propertyIdentifierTo = ontologyLinkGroup.getPropertyUniqueIdentifierTo();
        String columnValue = null;
        for(PropertyValueVO propertyValueVO: objectValueVo.getProperties()) {
            //TODO: 这里有潜在风险，因为没有考虑join时的数据类型
            if (propertyValueVO.getUniqueIdentifier().equals(propertyIdentifierFrom)) {
                columnValue = propertyValueVO.getValue();
                break;
            }
        }
        List<OntologyProperty> propertyToList = ontologyPropertyMapper.selectByUniqueIdentifier(propertyIdentifierTo);
        if (propertyToList.size() == 0) {
            return null;
        }
        OntologyProperty propertyTo = propertyToList.get(0);

        String toOntologyUniqueIdentifier = ontologyLinkGroup.getOntologyUniqueIdentifierTo();
        List<ObjectValueVo> linkedObjectValueVo = queryByColumnNameValue(
                toOntologyUniqueIdentifier,
                propertyTo.getDatasourceColumnName(),
                columnValue
        );
        linkedValueVo.setJoinedResults(linkedObjectValueVo);
        return linkedValueVo;
    }

    //目前只支持单个table，多个table的先不考虑
    private String buildSQLByQueryKey(List<OntologyProperty> ontologyPropertyList, String columnName, String value) {
        if (columnName == null || value == null ||
            ontologyPropertyList == null || ontologyPropertyList.size() == 0) {
            return null;
        }
        String colunmnsPart = "";
        for (int i = 0; i < ontologyPropertyList.size(); i++) {
            String tmp = "\"" + ontologyPropertyList.get(i).getDatasourceColumnName() + "\" AS " + "index" + i;
            if (i != 0) {
                tmp = ", " + tmp;
            }
            colunmnsPart += tmp;
        }
        String tableName = ontologyPropertyList.get(0).getDatasourceId();
        String fromPart = " FROM " + tableName;
        String wherePart = " WHERE " + columnName + " = '" + value + "'";
        String limitPart = " LIMIT 1000";
        String sql = "SELECT " + colunmnsPart + fromPart + wherePart + limitPart;
        System.out.println(sql);
        return sql;
    }
}
