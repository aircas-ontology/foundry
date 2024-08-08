package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.*;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyChildLinkMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.param.FilterParam;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
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
    private final OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private final ObjectMapper objectMapper;

    @Resource
    private final TableMetadataMapper tableMetadataMapper;

    @Resource
    private final OntologyLinkGroupMapper ontologyLinkGroupMapper;

    @Resource
    private final OntologyChildLinkMapper ontologyChildLinkMapper;

    @Resource
    private final OntologyPropertyService ontologyPropertyService;

    @Override
    public PageInfo<DirectoryItemVO> queryDirectories(String ontologyUniqueIdentifier, Integer page, Integer size) {

        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        OntologyProperty primaryKeyProperty = null;
        OntologyProperty titleKeyProperty = null;
        for (OntologyProperty property : ontologyPropertyList) {
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
        if (primaryKeyProperty.getDatasourceId() != null && primaryKeyProperty.getDatasourceId().equals(titleKeyProperty.getDatasourceId())) {
            String tableName = primaryKeyProperty.getDatasourceId();
            String primaryKeyColumnName = primaryKeyProperty.getDatasourceColumnName();
            String titleKeyColumnName = titleKeyProperty.getDatasourceColumnName();
            PageHelper.startPage(page, size);
            PageInfo<DirectoryItem> pageInfo = new PageInfo<>(objectMapper.queryDirectory(tableName, primaryKeyColumnName, titleKeyColumnName));
            List<DirectoryItemVO> collect = pageInfo.getList().stream().map(item -> {
                DirectoryItemVO directoryItemVO = new DirectoryItemVO();
                directoryItemVO.setDisplayName(item.getDisplayName());
                directoryItemVO.setPrimaryKey(item.getPrimaryKey());
                return directoryItemVO;
            }).collect(Collectors.toList());
            PageInfo<DirectoryItemVO> pageResult = new PageInfo<>(collect);
            BeanUtils.copyProperties(pageInfo, pageResult);
            return pageResult;
        }
        //对于primaryKey 和 titleKey位于不同的datasource的 先不支持
        return null;
    }

    @Override
    public ObjectOneInfoVO queryObjectByPrimaryKey(String ontologyUniqueIdentifier, String primaryKey) {
        return queryByPrimaryKey(ontologyUniqueIdentifier, primaryKey);
    }


    @Override
    public ObjectOneInfoVO queryObjectByApiAndPrimaryKey(String api, String primaryKey) {
        String identifier = queryIdentifierByAPI(api);
        return queryObjectByPrimaryKey(identifier, primaryKey);
    }

    private String queryIdentifierByAPI(String api) {
        return ontologyMetaMapper.selectByApi(api).getUniqueIdentifier();
    }

    public ObjectWithLinkedInfoVO queryObjectWithLinkedInfoByApiAndPrimaryKey(String api, String primaryKey) {
        String identifier = queryIdentifierByAPI(api);
        return queryObjectWithLinkedInfoByPrimaryKey(identifier, primaryKey);
    }

    @Override
    public PageInfo<Map<String, Object>> queryObjectList(String ontologyUniqueIdentifier, Integer page, Integer size) {

        List<OntologyPropertyVO> ontologyPropertyList = ontologyPropertyService.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        String sql = buildBaseSQL(ontologyPropertyList);
        if (sql == null) return null;
        PageHelper.startPage(page, size);
        List<Map<String, Object>> rawResult = objectMapper.queryAnySQL(sql);
        PageInfo pageResult = new PageInfo<>(rawResult);
        BeanUtils.copyProperties(pageResult, rawResult);
        return pageResult;
    }

    @Override
    public PageInfo<Map<String, Object>> queryObjectByFilter(String ontologyUniqueIdentifier, List<FilterParam> filter, Integer page, Integer size) {

        List<OntologyPropertyVO> ontologyPropertyList = ontologyPropertyService.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        String sql = buildBaseSQL(ontologyPropertyList);
        String where = filter.stream().map(item -> item.getFilterKey() + "='" + item.getFilterValue() + "'").collect(Collectors.joining(" and "));
        if (sql.equals(null) || sql.isEmpty() || where.equals(null) || where.isEmpty()) return null;
        PageHelper.startPage(page, size);
        List<Map<String, Object>> rawResult = objectMapper.queryAnySQL(sql + " where " + where);
        PageInfo pageResult = new PageInfo<>(rawResult);
        BeanUtils.copyProperties(pageResult, rawResult);
        return pageResult;
    }

    @Override
    public ObjectWithLinkedInfoVO queryObjectWithLinkedInfoByPrimaryKey(String ontologyUniqueIdentifier, String primaryKey) {
        ObjectWithLinkedInfoVO objectWithLinkedInfoVO = new ObjectWithLinkedInfoVO();
        ObjectOneInfoVO objectOneInfoVO = queryByPrimaryKey(ontologyUniqueIdentifier, primaryKey);
        objectWithLinkedInfoVO.setObjectValue(objectOneInfoVO);

        List<LinkedValueVo> linkedValueVoList = new ArrayList<>();
        objectWithLinkedInfoVO.setLinks(linkedValueVoList);
        List<OntologyLinkGroup> linksMetadata = getLinkedMetadata(ontologyUniqueIdentifier);
        for (int i = 0; i < linksMetadata.size(); i++) {
            OntologyLinkGroup ontologyLinkGroup = linksMetadata.get(i);
            LinkedValueVo linkedValueVo = getLinkedValue(ontologyLinkGroup, objectOneInfoVO);
            if (linkedValueVo != null) {
                linkedValueVoList.add(linkedValueVo);
            }
        }
        return objectWithLinkedInfoVO;
    }


    private ObjectOneInfoVO queryByPrimaryKey(String ontologyUniqueIdentifier, String primaryKey) {
        List<OntologyPropertyVO> ontologyPropertyList = ontologyPropertyService.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);

        //根据表，列，求解数据类型，填充到OntologyProperty中
        String primaryColumnName = getPrimaryKeyColumnName(ontologyPropertyList);
        List<ObjectOneInfoVO> objectOneInfoVOList = queryByColumnNameValue(ontologyPropertyList, primaryColumnName, primaryKey);
        if (objectOneInfoVOList.size() == 0) {
            return null;
        }
        return objectOneInfoVOList.get(0);
    }


    private List<ObjectOneInfoVO> queryByColumnNameValue(String ontologyUniqueIdentifier, String columnName, String columnValue) {
        List<OntologyPropertyVO> ontologyPropertyList = ontologyPropertyService.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        return queryByColumnNameValue(ontologyPropertyList, columnName, columnValue);
    }

    private List<ObjectOneInfoVO> queryByColumnNameValue(List<OntologyPropertyVO> ontologyPropertyList, String columnName, String columnValue) {
        String sql = buildSQLByQueryKey(ontologyPropertyList, columnName, columnValue);
        if (sql == null) return null;
        List<Map<String, Object>> rawResult = objectMapper.queryAnySQL(sql);
        List<ObjectOneInfoVO> ret = new ArrayList();
        if (rawResult.size() == 0) {
            return ret;
        }

        for (Map<String, Object> rawKeyValueMap : rawResult) {
            List<PropertyValueVO> propertyList = new ArrayList<>();
            String primaryKeyValue = null;
            String displayName = null;
            for (String key : rawKeyValueMap.keySet()) {
                PropertyValueVO propertyValueVO = new PropertyValueVO();
                propertyList.add(propertyValueVO);
                //index0 -> 0
                int propertyIndex = Integer.valueOf(key.substring(5));
                OntologyPropertyVO property = ontologyPropertyList.get(propertyIndex);
                propertyValueVO.setIsTitleKey(property.getIsTitleKey());
                propertyValueVO.setIsPrimaryKey(property.getIsPrimaryKey());
                propertyValueVO.setDisplayName(property.getDisplayName());
                propertyValueVO.setDescription(property.getDescription());
                propertyValueVO.setApiName(property.getApiName());
                propertyValueVO.setPropertyType(property.getPropertyType());
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
            ObjectOneInfoVO objectOneInfoVO = new ObjectOneInfoVO();
            objectOneInfoVO.setProperties(propertyList);
            objectOneInfoVO.setPrimaryKey(primaryKeyValue);
            objectOneInfoVO.setDisplayName(displayName);
            ret.add(objectOneInfoVO);
        }
        return ret;
    }

    private String getPrimaryKeyColumnName(List<OntologyPropertyVO> ontologyPropertyList) {
        for (OntologyPropertyVO property : ontologyPropertyList) {
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

    private LinkedValueVo getLinkedValue(OntologyLinkGroup ontologyLinkGroup, ObjectOneInfoVO objectOneInfoVO) {
        LinkedValueVo linkedValueVo = new LinkedValueVo();
        OntologyChildLink backwardChildLink = ontologyChildLinkMapper.selectByPrimaryKey(ontologyLinkGroup.getBackwardChildLinkId());
        String linkDisplayName = backwardChildLink.getDisplayName();
        String apiName = backwardChildLink.getApiName();
        linkedValueVo.setName(linkDisplayName);
        linkedValueVo.setApiName(apiName);

        String propertyIdentifierFrom = ontologyLinkGroup.getPropertyUniqueIdentifierFrom();
        String propertyIdentifierTo = ontologyLinkGroup.getPropertyUniqueIdentifierTo();
        String columnValue = null;
        for (PropertyValueVO propertyValueVO : objectOneInfoVO.getProperties()) {
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
        List<ObjectOneInfoVO> linkedObjectOneInfoVO = queryByColumnNameValue(
                toOntologyUniqueIdentifier,
                propertyTo.getDatasourceColumnName(),
                columnValue
        );
        linkedValueVo.setJoinedResults(linkedObjectOneInfoVO);
        return linkedValueVo;
    }

    //目前只支持单个table，多个table的先不考虑
    private String buildSQLByQueryKey(List<OntologyPropertyVO> ontologyPropertyList, String columnName, String value) {
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
        return sql;
    }

    private String buildBaseSQL(List<OntologyPropertyVO> ontologyPropertyList) {

        if (ontologyPropertyList == null || ontologyPropertyList.size() == 0) {
            return null;
        }
        String columns = ontologyPropertyList
                .stream()
                .map(column -> column.getDatasourceColumnName() + " AS " + column.getApiName())
                .collect(Collectors.joining(","));
        String tableName = ontologyPropertyList.get(0).getDatasourceId();

        return "SELECT " + columns + " FROM " + tableName;
    }
}
