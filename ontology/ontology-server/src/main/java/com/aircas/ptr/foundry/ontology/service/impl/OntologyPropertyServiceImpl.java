package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.client.EntityClient;
import com.aircas.ptr.foundry.ontology.common.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.model.param.EntityTableFieldParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyDataSourceCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.TableColumnDesc;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyDatasourcePropertyVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OntologyPropertyServiceImpl extends ServiceImpl<OntologyPropertyMapper, OntologyProperty> implements OntologyPropertyService {

    private static final Logger log = LoggerFactory.getLogger(OntologyPropertyServiceImpl.class);

    private final OntologyPropertyMapper ontologyPropertyMapper;

    private final TableMetadataMapper tableMetadataMapper;

    private final OntologyMetaMapper ontologyMetaMapper;

    private final EntityService entityService;

    private final EntityClient entityClient;

    private final OntologyMetaMapper metaMapper;

    @Override
    public void createDatasource(OntologyDataSourceCreateParam dataSourceCreateParam) {
        // 主数据源属性
        var meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, dataSourceCreateParam.getOntologyIdentifier()));
        var primaryDataSource = dataSourceCreateParam.getPrimaryDataSource();
        PreconditionUtils.checkArgument(primaryDataSource != null && CollectionUtils.isNotEmpty(primaryDataSource.getColumnParamList()), "primaryDataSource is null");
        var primaryTableName = primaryDataSource.getColumnParamList().get(0).getDatasourceId();
        meta.setBackingDatasourceId(primaryTableName);
        var associateDataSources = dataSourceCreateParam.getAssociateDataSources();
        if (CollectionUtils.isNotEmpty(associateDataSources)) {
            var dataSources = associateDataSources.stream().map(ds -> ds.getColumnParamList().get(0).getDatasourceId()).collect(Collectors.toList());
            meta.setOtherDatasourceId(String.join(",", dataSources));
        }
        metaMapper.updateById(meta);
        // 创建本体属性
        var properties = primaryDataSource.getColumnParamList().stream()
                .map(v -> DataConverter.convert(v)
                        .setOntologyUniqueIdentifier(dataSourceCreateParam.getOntologyIdentifier()))
                .collect(Collectors.toList());
        //  其他数据源属性
        if (CollectionUtils.isNotEmpty(associateDataSources)) {
            //校验datasource 是否冲突
            var datasourceIds = associateDataSources.stream().map(v -> v.getColumnParamList().get(0).getDatasourceId()).collect(Collectors.toList());
            datasourceIds.add(primaryTableName);
            PreconditionUtils.checkArgument(datasourceIds.stream().collect(Collectors.toSet()).size() == datasourceIds.size(), "datasourceId存在冲突");
            //校验关联健
            associateDataSources.stream().forEach(ds -> {
                var associateKey = ds.getColumnParamList().stream().filter(v -> v.getIsAssociateKey()).findFirst().orElse(null);
                PreconditionUtils.checkArgument(associateKey != null &&
                        properties.stream().anyMatch(v -> v.getDatasourceColumnName().equals(associateKey.getAssociateDatasourceColumnName())), "找不到关联健或者关联的属性错误");
            });
            //生成属性表数据
            var otherProps = associateDataSources.stream()
                    .flatMap(ds -> ds.getColumnParamList().stream().map(v ->
                            DataConverter.convert(v)
                                    .setOntologyUniqueIdentifier(dataSourceCreateParam.getOntologyIdentifier())
                                    .setCategory(ds.getCategory().getValue())
                    )).collect(Collectors.toList());
            properties.addAll(otherProps);
        }
        //校验property apiName是否有冲突
        PreconditionUtils.checkArgument(properties.stream().map(v -> StringUtils.lowerCase(v.getApiName())).collect(Collectors.toSet()).size() == properties.size(), "apiName存在冲突");
        //校验titleKey
        var titleProperties = properties.stream().filter(v -> v.getIsTitleKey() == 1).collect(Collectors.toList());
        PreconditionUtils.checkArgument(titleProperties.size() == 1 && titleProperties.get(0).getDatasourceId().equals(primaryTableName),
                "名称健不存在或多个");
        //批量插入
        saveBatch(properties);
        //创建实体表、实体数据和实体节点
        entityClient.createTableAndEntities(EntityCreateParam.builder()
                .primaryDataSource(DataConverter.convert(primaryDataSource, meta.getApiName()))
                .associateDataSources(CollectionUtils.isNotEmpty(associateDataSources) ?
                        associateDataSources.stream()
                                .map(v -> DataConverter.convert(v, meta.getApiName() + "_" + v.getColumnParamList().get(0).getDatasourceId()))
                                .collect(Collectors.toList())
                        : null)
                .build());
    }


    @Override
    public OntologyDatasourcePropertyVO getPropertyDetailByOntologyId(String ontologyUniqueIdentifier) {
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier));
        var primaryDS = meta.getBackingDatasourceId();
        var primaryProps = list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getDatasourceId, primaryDS));

        var res = OntologyDatasourcePropertyVO.builder()
                .primaryDataSource(primaryProps.stream().map(DataConverter::convert).collect(Collectors.toList()))
                .build();

        if (StringUtils.isNotEmpty(meta.getOtherDatasourceId())) {
            var otherProps = list(new LambdaQueryWrapper<OntologyProperty>()
                    .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                    .in(OntologyProperty::getDatasourceId, meta.getOtherDatasourceId().split(",")));
            var otherPropsMap = otherProps.stream().collect(Collectors.groupingBy(v -> v.getDatasourceId()));
            var otherPropDetails = otherPropsMap.entrySet().stream()
                    .map(v -> v.getValue().stream().map(p -> DataConverter.convert(p)).collect(Collectors.toList()))
                    .collect(Collectors.toList());
            res.setAssociateDataSources(otherPropDetails);
        }

        return res;
    }

    @Override
    public List<OntologyPropertyInfoVO> getPropertyInfoByOntologyId(String ontologyUniqueIdentifier) {

        var props = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));

        return props.stream().map(v -> OntologyPropertyInfoVO.builder()
                .apiName(v.getApiName())
                .description(v.getDescription())
                .displayName(v.getDisplayName())
                .isPrimaryKey(v.getIsPrimaryKey() == 1)
                .isTitleKey(v.getIsTitleKey() == 1)
                .propertyType(v.getPropertyType())
                .tag(v.getTag())
                .uniqueIdentifier(v.getUniqueIdentifier())
                .build())
                .collect(Collectors.toList());
    }

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
