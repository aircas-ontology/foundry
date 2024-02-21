package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.DirectoryItem;
import com.aircas.ptr.foundry.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
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
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        String primaryKeyColumnName = getPrimaryKeyColumnName(ontologyPropertyList);
        String sql = buildSQLByPrimaryKey(ontologyPropertyList, primaryKeyColumnName, primaryKey);
        if (sql == null) return null;
        List<Map<String, Object>> rawResult = objectMapper.queryAnySQL(sql);
        if (rawResult.size() == 0) {
            return null;
        }
        Map<String, Object> rawKeyValueMap = rawResult.get(0);
        List<PropertyValueVO> propertyList = new ArrayList<>();
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
            String value = rawKeyValueMap.get(key).toString();
            propertyValueVO.setValue(value);
        }
        ObjectValueVo objectValueVo = new ObjectValueVo();
        objectValueVo.setProperties(propertyList);
        return objectValueVo;
    }

    @Override
    public ObjectWithLinkedInfoVO queryObjectWithLinkedInfoByPrimaryKey(String ontologyUniqueIdentifier, String key) {
        return null;
    }


    // helper functions
    private String getPrimaryKeyColumnName(List<OntologyProperty> ontologyPropertyList) {
        for (OntologyProperty property: ontologyPropertyList) {
            if (property.getIsPrimaryKey() == 1) {
                return property.getDatasourceColumnName();
            }
        }
        return null;
    }

    //目前只支持单个table，多个table的先不考虑
    private String buildSQLByPrimaryKey(List<OntologyProperty> ontologyPropertyList, String primaryKeyColumn, String primaryValue) {
        if (primaryKeyColumn == null || primaryValue == null ||
            ontologyPropertyList == null || ontologyPropertyList.size() == 0) {
            return null;
        }
        String colunmnsPart = "";
        for (int i = 0; i < ontologyPropertyList.size(); i++) {
            String tmp = ontologyPropertyList.get(i).getDatasourceColumnName() + " AS " + "index" + i;
            if (i != 0) {
                tmp = ", " + tmp;
            }
            colunmnsPart += tmp;
        }
        String tableName = ontologyPropertyList.get(0).getDatasourceId();
        String fromPart = " FROM " + tableName;
        String wherePart = " WHERE " + primaryKeyColumn + " = '" + primaryValue + "'";
        String sql = "SELECT " + colunmnsPart + fromPart + wherePart;
        System.out.println(sql);
        return sql;
    }
}
