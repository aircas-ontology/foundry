package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.client.EntityClient;
import com.aircas.ptr.foundry.ontology.common.param.EntityColumnCreateParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityDataSourceColumnParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityDataSourceParam;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.param.OntologyDataSourceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.TableColumnDesc;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyDatasourcePropertyVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.jsonldjava.shaded.com.google.common.collect.Sets;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyPropertyServiceImpl extends ServiceImpl<OntologyPropertyMapper, OntologyProperty> implements OntologyPropertyService {

    private final OntologyPropertyMapper ontologyPropertyMapper;

    private final TableMetadataMapper tableMetadataMapper;

    private final OntologyMetaMapper ontologyMetaMapper;

    private final EntityClient entityClient;

    private final OntologyMetaMapper metaMapper;

    @Override
    @Transactional(value = "mainTransactionManager")
    public void createProperties(OntologyPropertyCreateParam propertyCreateParam) {
        var meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, propertyCreateParam.getOntologyIdentifier()));
        var properties = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, meta.getUniqueIdentifier()));
        List<EntityDataSourceParam> newDS = Lists.newArrayList();
        List<EntityDataSourceColumnParam> existDS = Lists.newArrayList();

        //新数据源
        var newDatasources = propertyCreateParam.getNewAssociateDataSources();
        if (CollectionUtils.isNotEmpty(newDatasources)) {
            //校验数据源
            var dataSources = newDatasources.stream().map(ds -> ds.getColumnParamList().get(0).getDatasourceId()).collect(Collectors.toList());
            var otherDS = meta.getOtherDatasourceId();
            List<String> dsList = Lists.newArrayList(meta.getBackingDatasourceId());
            if (StringUtils.isNotEmpty(otherDS)) {
                dsList.addAll(Arrays.stream(otherDS.split(",")).collect(Collectors.toList()));
            }
            dsList.addAll(dataSources);
            PreconditionUtils.checkArgument(dsList.stream().collect(Collectors.toSet()).size() == dsList.size(), "datasourceId存在冲突");
            //校验关联健，不考虑title key
            var otherProps = buildOtherDatasourceProperty(newDatasources, meta.getUniqueIdentifier(), properties);
            properties.addAll(otherProps);
            //校验property apiName是否有冲突
            PreconditionUtils.checkArgument(properties.stream().map(v -> StringUtils.lowerCase(v.getApiName())).collect(Collectors.toSet()).size() == properties.size(), "apiName存在冲突");
            //更新meta其他数据源信息
            dsList.remove(meta.getBackingDatasourceId());
            meta.setOtherDatasourceId(String.join(",", dsList));
            metaMapper.updateById(meta);
            //批量插入属性
            saveBatch(otherProps);
            //build new ds
            newDS = newDatasources.stream().map(ds -> DataConverter.convert(ds, meta.getApiName() + "_" + ds.getColumnParamList().get(0).getDatasourceId()))
                    .collect(Collectors.toList());
        }
        //旧数据源属性
        var existDatasources = propertyCreateParam.getExistDataSources();
        if (CollectionUtils.isNotEmpty(existDatasources)) {
            //校验数据源
            var dataSources = existDatasources.stream().map(ds -> ds.getDatasourceId()).collect(Collectors.toSet());
            var dsSet = Sets.newHashSet(meta.getBackingDatasourceId());
            if (StringUtils.isNotEmpty(meta.getOtherDatasourceId())) {
                dsSet.addAll(Arrays.stream(meta.getOtherDatasourceId().split(",")).collect(Collectors.toList()));
            }
            PreconditionUtils.checkArgument(dsSet.containsAll(dataSources), "数据源不存在");
            //校验property apiName和columnName是否有冲突
            var otherProps = existDatasources.stream().map(param -> DataConverter.convert(param).setOntologyUniqueIdentifier(meta.getUniqueIdentifier())).collect(Collectors.toList());
            properties.addAll(otherProps);
            PreconditionUtils.checkArgument(properties.stream().map(v -> StringUtils.lowerCase(v.getApiName())).collect(Collectors.toSet()).size() == properties.size(), "apiName存在冲突");
            //PreconditionUtils.checkArgument(properties.stream().map(v -> StringUtils.lowerCase(v.getDatasourceColumnName())).collect(Collectors.toSet()).size() == properties.size(), "datasourceColumnName存在冲突");
            //批量插入属性
            saveBatch(otherProps);
            //build exist ds
            existDS = existDatasources.stream().map(v -> DataConverter.convertEntityDataSource(v)).collect(Collectors.toList());
        }
        //更新实体层数据
        entityClient.createColumns(EntityColumnCreateParam.builder()
                .existDatasourceColumns(existDS)
                .newDatasource(newDS)
                .primaryTableName(meta.getApiName())
                .build());
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateProperty(OntologyPropertyUpdateParam propertyUpdateParam) {

    }

    @Override
    @Transactional(value = "mainTransactionManager")
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
            var otherProps = buildOtherDatasourceProperty(associateDataSources, meta.getUniqueIdentifier(), properties);
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

    private List<OntologyProperty> buildOtherDatasourceProperty(List<OntologyDatasourceParam> associateDataSources,
                                                                String ontologyUniqueIdentifier,
                                                                List<OntologyProperty> properties) {
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
                                .setOntologyUniqueIdentifier(ontologyUniqueIdentifier)
                                .setCategory(ds.getCategory().getValue())
                )).collect(Collectors.toList());
        return otherProps;
    }


    @Override
    public OntologyDatasourcePropertyVO getPropertyDetailByOntologyId(String ontologyUniqueIdentifier) {
        var tableMap = tableMetadataMapper.listTables().stream().collect(Collectors.toMap(v -> v.getTableName(), v -> v.getDescription() != null ? v.getDescription() : ""));
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier));
        var primaryDS = meta.getBackingDatasourceId();
        var primaryProps = list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getDatasourceId, primaryDS));

        var res = OntologyDatasourcePropertyVO.builder()
                .primaryDataSource(primaryProps.stream().map(v -> DataConverter.convert(v).setDatasourceDescription(tableMap.get(v.getDatasourceId())))
                        .collect(Collectors.toList()))
                .build();

        if (StringUtils.isNotEmpty(meta.getOtherDatasourceId())) {
            var otherProps = list(new LambdaQueryWrapper<OntologyProperty>()
                    .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                    .in(OntologyProperty::getDatasourceId, meta.getOtherDatasourceId().split(",")));
            var otherPropsMap = otherProps.stream().collect(Collectors.groupingBy(v -> v.getDatasourceId()));
            var otherPropDetails = otherPropsMap.entrySet().stream()
                    .map(v -> v.getValue().stream().map(p -> DataConverter.convert(p).setDatasourceDescription(tableMap.get(p.getDatasourceId())))
                            .collect(Collectors.toList()))
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

}
