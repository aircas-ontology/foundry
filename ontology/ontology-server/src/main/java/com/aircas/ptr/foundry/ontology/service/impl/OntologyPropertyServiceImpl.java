package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyVisibilityUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVisibilityVO;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableFieldMappingMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMappingInMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyPropertyServiceImpl extends ServiceImpl<OntologyPropertyMapper, OntologyProperty> implements OntologyPropertyService {

    private final TableMetadataMapper tableMetadataMapper;

    private final TableFieldMappingMapper tableFieldMappingMapper;

    private final EntityService entityService;

    private final OntologyLinkGroupMapper linkMapper;

    private final OntologyActionMappingInMapper mappingInMapper;

    private final OntologyMetaMapper metaMapper;


    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateProperty(OntologyPropertyUpdateParam param) {
        //check property existence
        var uniqIdentifier = param.getUniqueIdentifier();
        var originalProperty = getOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getUniqueIdentifier, uniqIdentifier));
        PreconditionUtils.checkArgument(originalProperty != null, "属性不存在", HttpStatus.BAD_REQUEST);

        var otherProps = list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, originalProperty.getOntologyUniqueIdentifier()))
                .stream().filter(v -> !v.getUniqueIdentifier().equals(param.getUniqueIdentifier())).collect(Collectors.toList());
        //check columnName
        checkDatasourceColumnName(otherProps, param.getDatasource());
        //check titleKey
        if (param.getIsTitleKey()) {
            checkTitleKey(otherProps);
        }
        //check primaryKey
        if (param.getIsPrimaryKey()) {
            checkPrimaryKey(otherProps, param.getDatasource());
        }
        //update property
        updateById(originalProperty.setDatasourceColumnName(param.getDatasource() != null ? param.getDatasource().getDatasourceColumnName() : null)
                .setDatasourceId(param.getDatasource() != null ? param.getDatasource().getDatasourceId() : null)
                .setDescription(param.getDescription())
                .setDisplayName(param.getDisplayName())
                .setPrimaryCategory(param.getPrimaryCategory())
                .setSecondaryCategory(param.getSecondaryCategory())
                .setTag(param.getTag())
                .setPropertyType(param.getDataType())
                .setIsTitleKey(param.getIsTitleKey() ? 1 : 0)
                .setIsPrimaryKey(param.getIsPrimaryKey() ? 1 : 0)
                .setDefaultValue(param.getDefaultValue()));
        //update arangodb node
        buildEntityNodes(originalProperty.getOntologyUniqueIdentifier());
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void batchUpdateProperties(List<OntologyPropertyUpdateParam> params) {
        if (CollectionUtils.isEmpty(params)) {
            return;
        }
        //参数校验
        //属性id是否有效
        var updateUniqIds = params.stream().map(v -> v.getUniqueIdentifier()).collect(Collectors.toList());
        var updateProperties = list(new LambdaQueryWrapper<OntologyProperty>().in(OntologyProperty::getUniqueIdentifier, updateUniqIds));
        PreconditionUtils.checkArgument(updateUniqIds.size() == updateProperties.size(), "属性不存在", HttpStatus.BAD_REQUEST);
        ///主键，标题健，数据源校验
        var ontologyId = updateProperties.get(0).getOntologyUniqueIdentifier();
        var otherProps = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyId).notIn(OntologyProperty::getUniqueIdentifier, updateUniqIds));
        var datasourceSet = Sets.newHashSet();
        var hasTitleKey = false;
        var hasPrimaryKey = false;
        var updatePropMap = updateProperties.stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v));

        for (OntologyPropertyUpdateParam p : params) {
            //check datasource columnName conflict
            if (p.getDatasource() != null) {
                var datasource = p.getDatasource();
                if (datasourceSet.contains(datasource.getDatasourceId() + datasource.getDatasourceColumnName())) {
                    throw new BusinessException("属性数据源冲突：" + datasource.getDatasourceId() + ":" + datasource.getDatasourceColumnName(), HttpStatus.BAD_REQUEST);
                }
            }
            checkDatasourceColumnName(otherProps, p.getDatasource());
            //check titleKey
            if (p.getIsTitleKey()) {
                PreconditionUtils.checkArgument(!hasTitleKey, "属性存在多个名称健", HttpStatus.BAD_REQUEST);
                hasTitleKey = true;
                checkTitleKey(otherProps);
            }
            //check primaryKey
            if (p.getIsPrimaryKey()) {
                PreconditionUtils.checkArgument(!hasPrimaryKey, "属性存在多个主键", HttpStatus.BAD_REQUEST);
                hasPrimaryKey = true;
                checkPrimaryKey(otherProps, p.getDatasource());
            }
            var prop = updatePropMap.get(p.getUniqueIdentifier());
            prop.setPropertyType(p.getDataType())
                    .setPrimaryCategory(p.getPrimaryCategory())
                    .setSecondaryCategory(p.getSecondaryCategory())
                    .setDefaultValue(p.getDefaultValue())
                    .setIsTitleKey(p.getIsTitleKey() ? 1 : 0)
                    .setIsPrimaryKey(p.getIsPrimaryKey() ? 1 : 0)
                    .setTag(p.getTag())
                    .setDescription(p.getDescription())
                    .setDisplayName(p.getDisplayName())
                    .setDatasourceId(p.getDatasource() != null ? p.getDatasource().getDatasourceId() : "")
                    .setDatasourceColumnName(p.getDatasource() != null ? p.getDatasource().getDatasourceColumnName() : "");
        }
        //batch update
        updateBatchById(updateProperties);
        //update arangodb node
        buildEntityNodes(ontologyId);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void batchCreateProperties(List<OntologyPropertyCreateParam> params) {
        if (CollectionUtils.isEmpty(params)) {
            return;
        }
        //参数校验
        var ontologyId = params.get(0).getOntologyIdentifier();
        var properties = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyId));

        var apiNameSet = Sets.newHashSet();
        var datasourceSet = Sets.newHashSet();
        var hasTitleKey = false;
        var hasPrimaryKey = false;
        List<OntologyProperty> propertyList = Lists.newArrayList();

        for (var p : params) {
            //check api name conflict
            if (apiNameSet.contains(p.getApiName())) {
                throw new BusinessException("属性apiName冲突：" + p.getApiName(), HttpStatus.BAD_REQUEST);
            } else {
                apiNameSet.add(p.getApiName());
            }
            checkApiName(properties, p.getApiName());
            //check datasource conflict
            if (p.getDatasource() != null) {
                var datasource = p.getDatasource();
                if (datasourceSet.contains(datasource.getDatasourceId() + datasource.getDatasourceColumnName())) {
                    throw new BusinessException("属性数据源冲突：" + datasource.getDatasourceId() + ":" + datasource.getDatasourceColumnName(), HttpStatus.BAD_REQUEST);
                } else {
                    apiNameSet.add(datasource.getDatasourceId() + datasource.getDatasourceColumnName());
                }
            }
            checkDatasourceColumnName(properties, p.getDatasource());
            //check titleKey
            if (p.getIsTitleKey()) {
                PreconditionUtils.checkArgument(!hasTitleKey, "属性存在多个名称健", HttpStatus.BAD_REQUEST);
                hasTitleKey = true;
                checkTitleKey(properties);
            }
            //check primaryKey
            if (p.getIsPrimaryKey()) {
                PreconditionUtils.checkArgument(!hasPrimaryKey, "属性存在多个主键", HttpStatus.BAD_REQUEST);
                hasPrimaryKey = true;
                checkPrimaryKey(properties, p.getDatasource());
            }
            propertyList.add(DataConverter.convert(p));
        }
        //更新数据
        saveBatch(propertyList);
        //更新节点
        buildEntityNodes(ontologyId);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteProperty(String propertyUniqueIdentifier) {
        var property = getOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getUniqueIdentifier, propertyUniqueIdentifier));
        PreconditionUtils.checkArgument(property != null, "无效的属性id", HttpStatus.BAD_REQUEST);
        //主键+标题健不能删除
        PreconditionUtils.checkArgument(property.getIsPrimaryKey() == 0 && property.getIsTitleKey() == 0, "主键和标题健不能删除", HttpStatus.FORBIDDEN);
        //被行为使用到的属性不能删除
        var mappingList = mappingInMapper.selectList(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getPropertyUniqueIdentifier, propertyUniqueIdentifier));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(mappingList), "该属性被本体行为用到", HttpStatus.FORBIDDEN);
        //delete
        remove(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getUniqueIdentifier, propertyUniqueIdentifier));
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void createProperty(OntologyPropertyCreateParam param) {
        //参数校验
        var ontologyId = param.getOntologyIdentifier();
        var properties = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyId));
        //check apiName
        checkApiName(properties, param.getApiName());
        //check columnName
        checkDatasourceColumnName(properties, param.getDatasource());
        //check titleKey
        if (param.getIsTitleKey()) {
            checkTitleKey(properties);
        }
        //check primaryKey
        if (param.getIsPrimaryKey()) {
            checkPrimaryKey(properties, param.getDatasource());
        }
        //create property
        save(DataConverter.convert(param));
        //create arango node by primary key
        buildEntityNodes(param.getOntologyIdentifier());
    }


    @Override
    public List<OntologyPropertyDetailVO> getPropertyDetailByOntologyId(String ontologyUniqueIdentifier) {
        var props = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        var tableMap = tableMetadataMapper.listTables().stream().collect(Collectors.toMap(v -> v.getTableName(), v -> v.getDescription() != null ? v.getDescription() : ""));

        return props.stream().map(v -> DataConverter.convert(v).setDatasourceDescription(tableMap.get(v.getDatasourceId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<OntologyPropertyInfoVO> getPropertyInfoByOntologyId(String ontologyUniqueIdentifier) {
        var props = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        return props.stream().map(v -> DataConverter.convertToPropertyInfoVO(v)).collect(Collectors.toList());
    }


    @Override
    public OntologyPropertyDetailVO getPropertyDetailById(String uniqueIdentifier) {
        var prop = getOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getUniqueIdentifier, uniqueIdentifier));
        PreconditionUtils.checkArgument(prop != null, "属性不存在：" + uniqueIdentifier, HttpStatus.BAD_REQUEST);
        return DataConverter.convert(prop);
    }

    @Override
    public List<OntologyPropertyDetailVO> getPropertiesDetailById(List<String> uniqueIdentifiers) {
        var prop = list(new LambdaQueryWrapper<OntologyProperty>().in(OntologyProperty::getUniqueIdentifier, uniqueIdentifiers));
        return prop.stream().map(v -> DataConverter.convert(v)).collect(Collectors.toList());
    }

    @Override
    public List<OntologyPropertyVisibilityVO> getPropertyVisibility(String ontologyUniqueIdentifier) {
        var properties = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        var pk = properties.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        if (pk.isPresent() && StringUtils.isNotEmpty(pk.get().getDatasourceColumnName())) {
            return properties.stream().filter(v -> StringUtils.equals(v.getDatasourceId(), pk.get().getDatasourceId()))
                    .map(v -> OntologyPropertyVisibilityVO.builder()
                            .visibility(v.getVisibility())
                            .propertyApiName(v.getApiName())
                            .propertyDisplayName(v.getDisplayName())
                            .build())
                    .collect(Collectors.toList());
        }
        return Lists.newArrayList();
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void updatePropertyVisibility(OntologyPropertyVisibilityUpdateParam param) {
        var propMap = param.getPropertyVisibility().stream().collect(Collectors.toMap(v -> v.getPropertyApiName(), v -> v.getVisibility()));
        var properties = list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .in(OntologyProperty::getApiName, propMap.keySet()));

        properties.stream().forEach(v -> {
            var visibility = propMap.get(v.getApiName());
            //主键和标题键不能设置为不可见
            if (visibility == 0 && (v.getIsPrimaryKey() == 1 || v.getIsTitleKey() == 1)) {
                throw new BusinessException("主键和标题键不能设置为不可见");
            }
            v.setVisibility(visibility);
        });
        updateBatchById(properties);
    }

    //todo 发送mq消息给数据组织层构建管道
    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public void autoBindDatasource(String ontologyIdentifier) {
        var ontology = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyIdentifier));
        // 校验本体属性是否存在
        var props = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        if (CollectionUtils.isEmpty(props)) {
            log.warn("本体{}没有属性", ontologyIdentifier);
            return;
        }
        String pkColumnName = "id";
        // 校验本体主键id是否存在
        var pk = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        if (!pk.isPresent() || !StringUtils.equals(pk.get().getApiName(), pkColumnName)) {
            throw new BusinessException("本体" + ontologyIdentifier + "没有id主键属性");
        }
        // 检查属性数据源
        var notBindProps = props.stream()
                .filter(p -> StringUtils.isEmpty(p.getDatasourceId()) && StringUtils.isEmpty(p.getDatasourceColumnName()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(notBindProps)) {
            log.warn("本体{}所有属性已关联数据源", ontologyIdentifier);
            return;
        }
        // 处理未绑定数据源的属性
        var notBindPropsMap = notBindProps.stream().collect(Collectors.groupingBy(v -> v.getStorageGroup()));
        var mainDS = ontology.getApiName();
        notBindPropsMap.forEach((ds, list) -> {
            //主属性表
            if (ds.equals("main")) {
                //检查表名是否存在：不存在创建新表，存在添加列
                var exist = tableMetadataMapper.isTableExist(mainDS);
                var columns = list.stream().map(p -> TableColumnDesc.builder()
                        .columnName(p.getApiName())
                        .type(OntologyDataTypeEnum.transfer2Pg(p.getPropertyType()))
                        .description(p.getDisplayName())
                        .build()).collect(Collectors.toList());
                if (!exist) {
                    //创建新表
                    var table = TableDesc.builder()
                            .description(ontology.getDisplayName())
                            .tableName(mainDS)
                            .build();
                    tableMetadataMapper.createTable(table, columns);
                } else {
                    //创建列
                    tableMetadataMapper.addColumns(mainDS, columns);
                }
                //更新数据源属性
                list.forEach(p -> p.setDatasourceId(mainDS).setDatasourceColumnName(p.getApiName()));
            }
            // 其他属性关联表
            else {
                //检查表名是否存在：不存在创建新表，存在添加列
                var exist = tableMetadataMapper.isTableExist(ds);
                var columns = list.stream().map(p -> TableColumnDesc.builder()
                        .columnName(p.getApiName())
                        .type(OntologyDataTypeEnum.transfer2Pg(p.getPropertyType()))
                        .description(p.getDisplayName())
                        .build()).collect(Collectors.toList());
                if (!exist) {
                    //增加和主属性关联列
                    var relatedColumn = mainDS + "_" + pkColumnName;
                    columns.add(TableColumnDesc.builder()
                            .columnName(relatedColumn)
                            .type("int4")
                            .description(ontology.getDisplayName() + "主键id")
                            .build());
                    //创建新表
                    var table = TableDesc.builder()
                            .description(ontology.getDisplayName() + list.get(0).getSecondaryCategory())
                            .tableName(ds)
                            .build();
                    tableMetadataMapper.createTable(table, columns);
                    //插入属性表关联关系
                    tableFieldMappingMapper.insert(TableFieldMapping.builder()
                            .sourceTableName(mainDS)
                            .sourceColumnName(pkColumnName)
                            .targetTableName(ds)
                            .targetColumnName(relatedColumn)

                            .build());
                } else {
                    //创建列
                    tableMetadataMapper.addColumns(ds, columns);
                }
                //更新数据源属性
                list.forEach(p -> p.setDatasourceId(ds).setDatasourceColumnName(p.getApiName()));
            }
        });
        //批量更新本体属性数据源
        var bindDsProps = notBindPropsMap.values().stream().flatMap(List::stream).collect(Collectors.toList());
        updateBatchById(bindDsProps);
    }


    private void checkPrimaryKey(List<OntologyProperty> properties, PropertyDatasourceParam datasource) {
        var existPrimaryKey = properties.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(!existPrimaryKey.isPresent(), "属性主键已存在", HttpStatus.BAD_REQUEST);
        if (datasource != null) {
            var ds = datasource.getDatasourceId();
            var pk = datasource.getDatasourceColumnName();
            var pkCol = tableMetadataMapper.queryPrimaryKeyColumnName(ds);
            PreconditionUtils.checkArgument(pkCol.equals(pk), "属性主键不是数据源主键", HttpStatus.BAD_REQUEST);
        }
    }

    private void checkTitleKey(List<OntologyProperty> properties) {
        var existTitleKey = properties.stream().filter(v -> v.getIsTitleKey() == 1).findFirst();
        PreconditionUtils.checkArgument(!existTitleKey.isPresent(), "属性标题键已存在", HttpStatus.BAD_REQUEST);
    }

    private void checkDatasourceColumnName(List<OntologyProperty> properties, PropertyDatasourceParam datasource) {
        if (datasource != null) {
            var sameDsProp = properties.stream().filter(v -> StringUtils.equals(v.getDatasourceId(), datasource.getDatasourceId())
                    && StringUtils.equals(v.getDatasourceColumnName(), datasource.getDatasourceColumnName())).findFirst();
            PreconditionUtils.checkArgument(!sameDsProp.isPresent(), "数据源和列已关联到已有属性上", HttpStatus.BAD_REQUEST);
        }
    }

    private void checkApiName(List<OntologyProperty> properties, String apiName) {
        var apiNameProp = properties.stream().filter(v -> v.getApiName().equals(apiName)).findFirst();
        PreconditionUtils.checkArgument(!apiNameProp.isPresent(), "属性apiName已存在:" + apiName, HttpStatus.BAD_REQUEST);
    }


    private void buildEntityNodes(String ontologyIdentifier) {
        var primaryProperty = getOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));

        var tiltleProperty = getOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                .eq(OntologyProperty::getIsTitleKey, 1));

        var titleColumn = "";
        if (tiltleProperty != null
                && primaryProperty != null
                && StringUtils.equals(tiltleProperty.getDatasourceId(), primaryProperty.getDatasourceId())) {
            titleColumn = tiltleProperty.getDatasourceColumnName();
        }

        var anyNode = entityService.findOneByOntologyUniqIdentifier(ontologyIdentifier);

        if (primaryProperty == null && anyNode == null) {
            return;
        }
        //新增主键数据源
        else if (primaryProperty != null && anyNode == null) {
            if (StringUtils.isNotEmpty(primaryProperty.getDatasourceId())) {
                entityService.syncNodes(ontologyIdentifier, primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
                createRelationsByLink(ontologyIdentifier);
            }
        }
        //主键字段设置为false
        else if (primaryProperty == null && anyNode != null) {
            entityService.deleteNodesAndRelationsByOntologyId(ontologyIdentifier);
        } else if (primaryProperty != null && anyNode != null) {
            if (StringUtils.isNotEmpty(primaryProperty.getDatasourceId())) {
                //主键数据源发生了变更
                if (!primaryProperty.getDatasourceId().equals(anyNode.getTableName())) {
                    entityService.deleteNodesAndRelationsByOntologyId(ontologyIdentifier);
                    entityService.syncNodes(ontologyIdentifier, primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
                    createRelationsByLink(ontologyIdentifier);
                }
                //更新titleKey
                else {
                    entityService.syncNodes(ontologyIdentifier, primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
                }
            }
            //主键数据源取消设置
            else {
                entityService.deleteNodesAndRelationsByOntologyId(ontologyIdentifier);
            }
        }
    }

    private void createRelationsByLink(String ontologyIdentifier) {
        var links = linkMapper.selectList(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyIdentifier)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyIdentifier));
        links.stream().forEach(link -> entityService.createEntityRelations(link.getUniqueIdentifier()));
    }


}
