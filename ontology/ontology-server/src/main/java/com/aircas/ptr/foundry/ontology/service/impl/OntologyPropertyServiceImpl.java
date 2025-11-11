package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyActionMappingIn;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMappingInMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
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

    private final EntityService entityService;

    private final OntologyLinkGroupMapper linkMapper;

    private final OntologyActionMappingInMapper mappingInMapper;


    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateProperty(OntologyPropertyUpdateParam param) {
        //check property existence
        var uniqIdentifier = param.getUniqIdentifier();
        var originalProperty = getOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getUniqueIdentifier, uniqIdentifier));
        PreconditionUtils.checkArgument(originalProperty != null, "属性不存在", HttpStatus.BAD_REQUEST);

        var otherProps = list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, originalProperty.getOntologyUniqueIdentifier()))
                .stream().filter(v -> !v.getUniqueIdentifier().equals(param.getUniqIdentifier())).collect(Collectors.toList());
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
                .setTag(param.getTag())
                .setPropertyType(param.getDataType())
                .setIsTitleKey(param.getIsTitleKey() ? 1 : 0)
                .setIsPrimaryKey(param.getIsPrimaryKey() ? 1 : 0));
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
        var updateUniqIds = params.stream().map(v -> v.getUniqIdentifier()).collect(Collectors.toList());
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
            var prop = updatePropMap.get(p.getUniqIdentifier());
            prop.setPropertyType(p.getDataType())
                    .setIsTitleKey(p.getIsTitleKey() ? 1 : 0)
                    .setIsPrimaryKey(p.getIsPrimaryKey() ? 1 : 0)
                    .setTag(p.getTag())
                    .setDescription(p.getDescription())
                    .setDisplayName(p.getDisplayName())
                    .setDatasourceId(p.getDatasource() != null ? p.getDatasource().getDatasourceId() : null)
                    .setDatasourceColumnName(p.getDatasource() != null ? p.getDatasource().getDatasourceColumnName() : null);
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

        return props.stream().map(v -> OntologyPropertyDetailVO.builder()
                .description(v.getDescription())
                .displayName(v.getDisplayName())
                .isPrimaryKey(v.getIsPrimaryKey() == 1)
                .isTitleKey(v.getIsTitleKey() == 1)
                .tag(v.getTag())
                .uniqueIdentifier(v.getUniqueIdentifier())
                .apiName(v.getApiName())
                .propertyType(v.getPropertyType())
                .datasourceId(v.getDatasourceId())
                .datasourceColumnName(v.getDatasourceColumnName())
                .datasourceDescription(tableMap.get(v.getDatasourceId()))
                .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<OntologyPropertyInfoVO> getPropertyInfoByOntologyId(String ontologyUniqueIdentifier) {

        var props = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));

        return props.stream().map(v -> OntologyPropertyInfoVO.builder()
                .description(v.getDescription())
                .displayName(v.getDisplayName())
                .isPrimaryKey(v.getIsPrimaryKey() == 1)
                .isTitleKey(v.getIsTitleKey() == 1)
                .tag(v.getTag())
                .uniqueIdentifier(v.getUniqueIdentifier())
                .build())
                .collect(Collectors.toList());
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

        var nodes = entityService.getByByOntologyUniqIdentifier(ontologyIdentifier);

        if (primaryProperty == null && CollectionUtils.isEmpty(nodes)) {
            return;
        }
        //新增主键数据源
        else if (primaryProperty != null && CollectionUtils.isEmpty(nodes)) {
            if (StringUtils.isNotEmpty(primaryProperty.getDatasourceId())) {
                entityService.createNodes(ontologyIdentifier, primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
                createRelationsByLink(ontologyIdentifier);
            }
        }
        //主键字段设置为false
        else if (primaryProperty == null && !CollectionUtils.isEmpty(nodes)) {
            entityService.deleteNodesAndRelationsByOntologyId(ontologyIdentifier);
        } else if (primaryProperty != null && !CollectionUtils.isEmpty(nodes)) {
            //主键数据源发生了变更
            if (StringUtils.isNotEmpty(primaryProperty.getDatasourceId())) {
                if (!primaryProperty.getDatasourceId().equals(nodes.get(0).getTableName())) {
                    entityService.deleteNodesAndRelationsByOntologyId(ontologyIdentifier);
                    entityService.createNodes(ontologyIdentifier, primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
                    createRelationsByLink(ontologyIdentifier);
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
        links.stream().forEach(link -> entityService.createEntityRelations(link));
    }


}
