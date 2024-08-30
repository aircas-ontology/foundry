package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.OntologyDataType;
import com.aircas.ptr.foundry.model.po.OntologyProperty;
import com.aircas.ptr.foundry.model.po.TableColumnDesc;
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.TableMetadataMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OntologyPropertyServiceImpl implements OntologyPropertyService {

    private final OntologyPropertyMapper ontologyPropertyMapper;

    @Resource
    private final TableMetadataMapper tableMetadataMapper;

    @Autowired
    private OntologyMetaMapper ontologyMetaMapper;

    @Autowired
    private OntologyMetaService ontologyMetaService;

    @Override
    public Integer add(OntologyPropertyBO ontologyPropertyBO) {
        OntologyProperty ontologyProperty = new OntologyProperty();
        BeanUtils.copyProperties(ontologyPropertyBO, ontologyProperty);
        ontologyProperty.setUniqueIdentifier(UUID.randomUUID().toString());
        Date now = new Date();
        ontologyProperty.setCreateTime(now);
        ontologyProperty.setCreateTime(now);
        return ontologyPropertyMapper.insert(ontologyProperty);
    }

    @Override
    public Integer batchAdd(List<OntologyPropertyBO> ontologyPropertyBOs) {
        for (OntologyPropertyBO bo : ontologyPropertyBOs) {
            this.add(bo);
        }
        //TODO: 这里需要修改返回正确的status
        return 1;
    }

    @Override
    public Integer batchUpdate(List<OntologyPropertyBO> ontologyPropertyBOs) {
        for (OntologyPropertyBO bo : ontologyPropertyBOs) {
            if (isExist(bo.getUniqueIdentifier())) {
                update(bo);
            } else {
                add(bo);
            }
        }
        //TODO: 这里需要修改返回正确的status
        return 1;
    }

    private Boolean isExist(String uniqueIdentifier) {
        return selectByUniqueIdentifier(uniqueIdentifier) != null;
    }


    @Override
    public Integer delete(String uniqueIdentifier) {
        return ontologyPropertyMapper.deleteByUniqueIdentifier(uniqueIdentifier);
    }

    @Override
    public Integer update(OntologyPropertyBO ontologyPropertyBO) {
        OntologyProperty ontologyProperty = new OntologyProperty();
        BeanUtils.copyProperties(ontologyPropertyBO, ontologyProperty);
        ontologyProperty.setUpdateTime(new Date());
        return ontologyPropertyMapper.updateSelective(ontologyProperty);
    }

    @Override
    public List<OntologyPropertyVO> selectByOntologyUniqueIdentifier(String uniqueIdentifier) {
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByOntologyUniqueIdentifier(uniqueIdentifier);
        List<OntologyPropertyVO> list = new ArrayList<>();

        List<String> dataSouceIdList = ontologyPropertyList.stream().map(property -> property.getDatasourceId()).collect(Collectors.toList());
        Map<String, Map<String, TableColumnDesc>> propertySourceMap = new HashMap<>();
        for (String dataSourceId : dataSouceIdList) {
            List<TableColumnDesc> tableColumnDescs = tableMetadataMapper.getColumnMetadata(dataSourceId);
            Map<String, TableColumnDesc> columnNameDescMap = new HashMap<>();
            for (TableColumnDesc tableColumnDesc : tableColumnDescs) {
                columnNameDescMap.put(tableColumnDesc.getColumnName(), tableColumnDesc);
            }
            propertySourceMap.put(dataSourceId, columnNameDescMap);
        }

        for (OntologyProperty ontologyProperty : ontologyPropertyList) {
            OntologyPropertyVO propertyVO = new OntologyPropertyVO();
            BeanUtils.copyProperties(ontologyProperty, propertyVO);
            TableColumnDesc tableColumnDesc = propertySourceMap.get(ontologyProperty.getDatasourceId()).get(ontologyProperty.getDatasourceColumnName());
            OntologyDataType type = OntologyDataType.valueFromPgType(tableColumnDesc.getType());
            propertyVO.setPropertyType(type);
            list.add(propertyVO);
        }
        return list;
    }

    @Override
    public OntologyPropertyVO selectByUniqueIdentifier(String uniqueIdentifier) {
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByUniqueIdentifier(uniqueIdentifier);
        if (ontologyPropertyList.size() == 0) {
            return null;
        }
        OntologyPropertyVO propertyVO = new OntologyPropertyVO();
        BeanUtils.copyProperties(ontologyPropertyList.get(0), propertyVO);
        return propertyVO;
    }

    @Override
    public List<OntologyPropertyVO> selectByOntologyApi(String api) {

        OntologyMetaVO metaVO = ontologyMetaService.getOntologyByApi(api);
        return selectByOntologyUniqueIdentifier(metaVO.getUniqueIdentifier());
    }
}
