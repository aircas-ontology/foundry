package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.aircas.ptr.foundry.model.po.OntologyProperty;
import com.aircas.ptr.foundry.model.po.TableColumnDesc;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.model.request.EntityTableFieldParam;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OntologyPropertyServiceImpl implements OntologyPropertyService {

    private static final Logger log = LoggerFactory.getLogger(OntologyPropertyServiceImpl.class);
    private final OntologyPropertyMapper ontologyPropertyMapper;

    @Resource
    private final TableMetadataMapper tableMetadataMapper;

    @Autowired
    private OntologyMetaMapper ontologyMetaMapper;

    @Autowired
    private EntityService entityService;

    @Override
    public Integer add(OntologyPropertyBO ontologyPropertyBO) {

        if (ontologyPropertyMapper.selectByApiName(ontologyPropertyBO.getOntologyUniqueIdentifier(), ontologyPropertyBO.getApiName()) != null) {
            log.warn("apiName:{} existed", ontologyPropertyBO.getApiName());
            return 0;
        }
        OntologyProperty ontologyProperty = new OntologyProperty();
        BeanUtils.copyProperties(ontologyPropertyBO, ontologyProperty);
        ontologyProperty.setUniqueIdentifier(UUID.randomUUID().toString());
        Date now = new Date();
        ontologyProperty.setCreateTime(now);
        ontologyProperty.setUpdateTime(now);
        int inserted = ontologyPropertyMapper.insert(ontologyProperty);
        if (inserted <= 0) {
            return 0;
        }
        return entityTableMake(ontologyProperty.getOntologyUniqueIdentifier()) ? 1 : 0;
    }

    @Override
    public Integer batchAdd(List<OntologyPropertyBO> ontologyPropertyBOs) {

        // 验证是同一个ontology的属性，才可以批量插入
        if (ontologyPropertyBOs.stream().map(OntologyPropertyBO::getOntologyUniqueIdentifier).distinct().count() != 1) {
            return 0;
        }
        ontologyPropertyBOs.forEach(this::add);
        //TODO: 这里需要修改返回正确的status
        return entityTableMake(ontologyPropertyBOs.get(0).getOntologyUniqueIdentifier()) ? 1 : 0;
    }

    @Override
    public Integer batchUpdate(List<OntologyPropertyBO> ontologyPropertyBOs) {

        // 验证是同一个ontology的属性，才可以批量插入
        if (ontologyPropertyBOs.stream().map(OntologyPropertyBO::getOntologyUniqueIdentifier).distinct().count() != 1) {
            return 0;
        }
        for (OntologyPropertyBO bo : ontologyPropertyBOs) {
            if (isExist(bo.getOntologyUniqueIdentifier(), bo.getApiName())) {
                update(bo);
            } else {
                add(bo);
            }
        }
        //TODO: 这里需要修改返回正确的status
        return entityTableMake(ontologyPropertyBOs.get(0).getOntologyUniqueIdentifier()) ? 1 : 0;
    }

    private Boolean isExist(String ontologyUniqueIdentifier, String apiName) {

        return ontologyPropertyMapper.selectByApiName(ontologyUniqueIdentifier, apiName) != null;
    }


    @Override
    public Integer delete(String uniqueIdentifier) {
        int deleted = ontologyPropertyMapper.deleteByUniqueIdentifier(uniqueIdentifier);
        if (deleted <= 0) {
            return 0;
        }
        return entityTableMake(ontologyPropertyMapper.selectByUniqueIdentifier(uniqueIdentifier).get(0).getOntologyUniqueIdentifier()) ? 1 : 0;
    }

    @Override
    public Integer update(OntologyPropertyBO ontologyPropertyBO) {
        OntologyProperty ontologyProperty = new OntologyProperty();
        BeanUtils.copyProperties(ontologyPropertyBO, ontologyProperty);
        ontologyProperty.setUpdateTime(new Date());
        int updated = ontologyPropertyMapper.updateSelective(ontologyProperty);
        if (updated <= 0) {
            return 0;
        }
        return entityTableMake(ontologyPropertyBO.getOntologyUniqueIdentifier()) ? 1 : 0;
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
            String dataSourceId = ontologyProperty.getDatasourceId();
            if (dataSourceId != null && dataSourceId.length() > 0) {
                Map<String, TableColumnDesc> tableColumnsDesc = propertySourceMap.get(dataSourceId);
                if (tableColumnsDesc != null && tableColumnsDesc.size() > 0) {
                    TableColumnDesc tableColumnDesc = tableColumnsDesc.get(ontologyProperty.getDatasourceColumnName());
                    OntologyDataTypeEnum type = OntologyDataTypeEnum.valueOfPg(tableColumnDesc.getType());
                    propertyVO.setPropertyType(type);
                }
            }
            list.add(propertyVO);
        }
        return list;
    }

    public List<OntologyPropertyVO> getAllProperty(int justPrimary) {
        List<OntologyPropertyVO> list = new ArrayList<>();
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.getAllProperty(justPrimary);
        for (OntologyProperty ontologyProperty : ontologyPropertyList) {
            OntologyPropertyVO propertyVO = new OntologyPropertyVO();
            BeanUtils.copyProperties(ontologyProperty, propertyVO);
//            TableColumnDesc tableColumnDesc = propertySourceMap.get(ontologyProperty.getDatasourceId()).get(ontologyProperty.getDatasourceColumnName());
//            OntologyDataType type = OntologyDataType.valueFromPgType(tableColumnDesc.getType());
//            propertyVO.setPropertyType(type);
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

        return selectByOntologyUniqueIdentifier(ontologyMetaMapper.selectByApi(api).getUniqueIdentifier());
    }

    private Boolean entityTableMake(String ontologyUniqueIdentifier) {

        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyUniqueIdentifier);
        if (ontologyMeta == null || ontologyMeta.getApiName() == null) {
            return false;
        }
        List<EntityTableFieldParam> collect = ontologyPropertyMapper.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier)
                .stream()
                .map(property -> new EntityTableFieldParam(
                        property.getDescription(),
                        property.getApiName(),
                        property.getPropertyType().transfer2Pg(),
                        property.getIsPrimaryKey() != 1,
                        property.getIsPrimaryKey() == 1))
                .collect(Collectors.toList());
        if (collect.isEmpty()) {
            log.warn("本体{}没有属性，跳过建表", ontologyUniqueIdentifier);
            return false;
        }
        Boolean existed = entityService.existsEntityTable(ontologyMeta.getApiName());
        if (existed && entityService.countEntityTable(ontologyMeta.getApiName()) <= 0 && !entityService.deleteEntityTable(ontologyMeta.getApiName())) {
            return false;
        }

        return entityService.createEntityTable(ontologyMeta.getApiName(), ontologyMeta.getDescription(), collect);
    }
}
