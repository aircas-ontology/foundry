package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.DirectoryItem;
import com.aircas.ptr.foundry.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.aircas.ptr.foundry.ontology.entity.vo.DirectoryItemVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;


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
}
