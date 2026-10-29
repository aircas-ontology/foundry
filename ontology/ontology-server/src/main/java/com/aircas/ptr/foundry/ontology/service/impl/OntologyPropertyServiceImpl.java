package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.dto.EntityDatasourceColumnDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityDatasourceDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityDatasourceSchemaChangeEventDTO;
import com.aircas.ptr.foundry.ontology.model.enums.DatasourceEventTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.mq.producer.RabbitMQProducer;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableFieldMappingMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.service.PropertyCategoryService;
import com.aircas.ptr.foundry.ontology.service.PropertyMetadataSchemaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jsonldjava.shaded.com.google.common.collect.Sets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyPropertyServiceImpl extends ServiceImpl<OntologyPropertyMapper, OntologyProperty> implements OntologyPropertyService {

    private final TableMetadataMapper tableMetadataMapper;

    private final TableFieldMappingMapper tableFieldMappingMapper;

    private final EntityService entityService;

    private final OntologyActionMappingInMapper mappingInMapper;

    private final OntologyMetaMapper metaMapper;

    private final OntologySpaceMapper spaceMapper;

    private final PropertyCategoryService propertyCategoryService;

    private final PropertyMetadataSchemaService propertyMetadataSchemaService;

    private final RabbitMQProducer producer;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    private ObjectMapper jsonMapper = new ObjectMapper();


    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateProperty(OntologyPropertyUpdateParam param) {
        //check property existence
        var uniqIdentifier = param.getUniqueIdentifier();
        var originalProperty = getOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getUniqueIdentifier, uniqIdentifier));
        PreconditionUtils.checkArgument(originalProperty != null, "属性不存在", HttpStatus.BAD_REQUEST);
        //get ontology apiName
        var apiName = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, originalProperty.getOntologyUniqueIdentifier())).getApiName();
        PreconditionUtils.checkArgument(!StringUtils.equals(apiName, param.getStorageGroup()), "属性存储分组名称不能和本体apiName相同", HttpStatus.BAD_REQUEST);

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
        //check categoryId
        if (param.getCategoryId() != null) {
            var category = propertyCategoryService.getOne(new LambdaQueryWrapper<PropertyCategory>()
                    .eq(PropertyCategory::getId, param.getCategoryId())
                    .eq(PropertyCategory::getOntologyUniqueIdentifier, originalProperty.getOntologyUniqueIdentifier()));
            PreconditionUtils.checkNotNull(category, "无效的属性分类id", HttpStatus.BAD_REQUEST);
        }
        //check metadata schema 和元数据schema格式是否一致
        var schema = getMetadataSchema(originalProperty.getOntologyUniqueIdentifier());
        var metadata = param.getMetadata();
        if (metadata != null) {
            PreconditionUtils.checkNotNull(schema, "当前本体未定义元数据schema，不能设置元数据", HttpStatus.BAD_REQUEST);
            validateMetadataAgainstSchema(metadata, schema, "");
        }
        var titleKeyChanged = isTitleKeyChanged(originalProperty, param);
        //update property
        updateById(originalProperty.setDatasourceColumnName(param.getDatasource() != null ? param.getDatasource().getDatasourceColumnName() : null)
                .setDatasourceId(param.getDatasource() != null ? param.getDatasource().getDatasourceId() : null)
                .setDescription(param.getDescription())
                .setDisplayName(param.getDisplayName())
                .setPropertyType(param.getDataType())
                .setIsTitleKey(param.getIsTitleKey() ? 1 : 0)
                .setIsPrimaryKey(param.getIsPrimaryKey() ? 1 : 0)
                .setDefaultValue(param.getDefaultValue())
                .setStorageGroup(param.getStorageGroup())
                .setPropertyCategoryId(param.getCategoryId())
                .setMetadata(param.getMetadata()));
        //update arangodb node
        buildEntityNodes(originalProperty.getOntologyUniqueIdentifier(), titleKeyChanged);
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
        //get ontology apiName
        var apiName = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyId)).getApiName();
        var otherProps = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyId).notIn(OntologyProperty::getUniqueIdentifier, updateUniqIds));
        var datasourceSet = Sets.newHashSet();
        var hasTitleKey = false;
        var hasPrimaryKey = false;
        var updatePropMap = updateProperties.stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v));
        //获取属性元数据schema
        var schema = getMetadataSchema(ontologyId);
        //get all categoryIds
        var categoryIds = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>()
                        .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyId))
                .stream().map(PropertyCategory::getId).collect(Collectors.toSet());
        var titleKeyChanged = false;

        for (OntologyPropertyUpdateParam p : params) {
            //check storage group
            PreconditionUtils.checkArgument(!StringUtils.equals(apiName, p.getStorageGroup()), "属性存储分组名称不能和本体apiName相同", HttpStatus.BAD_REQUEST);
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
            //check categoryId
            if (p.getCategoryId() != null) {
                PreconditionUtils.checkArgument(categoryIds.contains(p.getCategoryId()), "无效的分类id", HttpStatus.BAD_REQUEST);
            }
            //check metadata schema 和元数据schema格式是否一致
            var metadata = p.getMetadata();
            if (metadata != null) {
                PreconditionUtils.checkNotNull(schema, "当前本体未定义元数据schema，不能设置元数据", HttpStatus.BAD_REQUEST);
                validateMetadataAgainstSchema(metadata, schema, "");
            }
            var prop = updatePropMap.get(p.getUniqueIdentifier());
            if (isTitleKeyChanged(prop, p)) {
                titleKeyChanged = true;
            }
            prop.setPropertyType(p.getDataType())
                    .setDefaultValue(p.getDefaultValue())
                    .setIsTitleKey(p.getIsTitleKey() ? 1 : 0)
                    .setIsPrimaryKey(p.getIsPrimaryKey() ? 1 : 0)
                    .setDescription(p.getDescription())
                    .setDisplayName(p.getDisplayName())
                    .setDatasourceId(p.getDatasource() != null ? p.getDatasource().getDatasourceId() : "")
                    .setDatasourceColumnName(p.getDatasource() != null ? p.getDatasource().getDatasourceColumnName() : "")
                    .setStorageGroup(p.getStorageGroup())
                    .setPropertyCategoryId(p.getCategoryId())
                    .setMetadata(p.getMetadata());
        }
        //batch update
        updateBatchById(updateProperties);
        //update arangodb node
        buildEntityNodes(ontologyId, titleKeyChanged);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void batchCreateProperties(List<OntologyPropertyCreateParam> params) {
        if (CollectionUtils.isEmpty(params)) {
            return;
        }
        //参数校验
        var ontologyId = params.get(0).getOntologyIdentifier();
        //get ontology apiName
        var apiName = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyId)).getApiName();

        var properties = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyId));

        var apiNameSet = Sets.newHashSet();
        var datasourceSet = Sets.newHashSet();
        var hasTitleKey = false;
        var hasPrimaryKey = false;
        List<OntologyProperty> propertyList = Lists.newArrayList();

        var schema = getMetadataSchema(ontologyId);

        //get all categoryIds
        var categoryIds = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>()
                        .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyId))
                .stream().map(PropertyCategory::getId).collect(Collectors.toSet());

        for (var p : params) {
            // check storage group
            PreconditionUtils.checkArgument(!StringUtils.equals(apiName, p.getStorageGroup()), "属性存储分组名称不能和本体apiName相同", HttpStatus.BAD_REQUEST);
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
            //check categoryId
            if (p.getCategoryId() != null) {
                PreconditionUtils.checkArgument(categoryIds.contains(p.getCategoryId()), "无效的分类id", HttpStatus.BAD_REQUEST);
            }
            //check metadata schema 和元数据schema格式是否一致
            var metadata = p.getMetadata();
            if (metadata != null) {
                PreconditionUtils.checkNotNull(schema, "当前本体未定义元数据schema，不能设置元数据", HttpStatus.BAD_REQUEST);
                validateMetadataAgainstSchema(metadata, schema, "");
            }
            propertyList.add(DataConverter.convert(p));
        }
        //更新数据
        saveBatch(propertyList);
        //更新节点
        buildEntityNodes(ontologyId, hasTitleKey);
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
        //get ontology apiName
        var apiName = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyId)).getApiName();
        // check storage group
        PreconditionUtils.checkArgument(!StringUtils.equals(apiName, param.getStorageGroup()), "属性存储分组名称不能和本体apiName相同", HttpStatus.BAD_REQUEST);
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
        //check categoryId
        if (param.getCategoryId() != null) {
            var category = propertyCategoryService.getOne(new LambdaQueryWrapper<PropertyCategory>()
                    .eq(PropertyCategory::getId, param.getCategoryId())
                    .eq(PropertyCategory::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));
            PreconditionUtils.checkNotNull(category, "无效的属性分类id", HttpStatus.BAD_REQUEST);
        }
        //check metadata schema 和元数据schema格式是否一致
        var schema = getMetadataSchema(param.getOntologyIdentifier());
        var metadata = param.getMetadata();
        if (metadata != null) {
            PreconditionUtils.checkNotNull(schema, "当前本体未定义元数据schema，不能设置元数据", HttpStatus.BAD_REQUEST);
            validateMetadataAgainstSchema(metadata, schema, "");
        }
        //create property
        save(DataConverter.convert(param));
        //create arangodb node by primary key
        buildEntityNodes(param.getOntologyIdentifier(), param.getIsTitleKey());
    }


    @Override
    public List<OntologyPropertyDetailVO> getPropertyDetailByOntologyId(String ontologyUniqueIdentifier) {
        //get schema name
        var schemaName = resolveSchemaName(ontologyUniqueIdentifier);
        var props = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        var tableMap = tableMetadataMapper.listTables(schemaName, null).stream().collect(Collectors.toMap(v -> v.getTableName(), v -> v.getDescription() != null ? v.getDescription() : ""));

        return props.stream().map(v -> DataConverter.convert(v).setDatasourceDescription(tableMap.get(v.getDatasourceId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<OntologyPropertyInfoVO> getPropertyInfoByOntologyId(String ontologyUniqueIdentifier) {
        var props = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        return props.stream().map(DataConverter::convertToPropertyInfoVO).collect(Collectors.toList());
    }

    @Override
    public List<OntologyPropertyInfoVO> getByCategoryId(Integer categoryId) {
        log.info("[getByCategoryId] received categoryId={}", categoryId);
        var queryWrapper = new LambdaQueryWrapper<OntologyProperty>();
        if (categoryId != null) {
            queryWrapper.eq(OntologyProperty::getPropertyCategoryId, categoryId);
        }
        var propList = list(queryWrapper);
        log.info("[getByCategoryId] categoryId={}, matched size={}", categoryId, propList.size());
        return propList.stream()
                .map(DataConverter::convertToPropertyInfoVO)
                .collect(Collectors.toList());
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
        return prop.stream().map(DataConverter::convert).collect(Collectors.toList());
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


    /**
     * 针对在途本体实体表数据通知数据组织层构建数据管道
     *
     * @param ontologyIdentifier
     */
    @Override
    public void notifyBuildPipeline(String ontologyIdentifier) {
        // 获取schemeName
        var schemaName = resolveSchemaName(ontologyIdentifier);
        // 校验本体属性是否存在
        var props = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        if (CollectionUtils.isEmpty(props)) {
            log.warn("本体{}没有属性", ontologyIdentifier);
            return;
        }
        String pkColumnName = "id";
        String mainStorageGroup = "main";
        // 校验本体主键id是否存在且属于主存储分组
        var pk = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        if (!pk.isPresent()
                || !StringUtils.equals(pk.get().getApiName(), pkColumnName)
                || !StringUtils.equals(pk.get().getStorageGroup(), mainStorageGroup)) {
            throw new BusinessException("本体" + ontologyIdentifier + "没有id主键属性或id不属于主存储分组");
        }
        // 检查属性数据源
        var alreadyBindProps = props.stream()
                .filter(p -> StringUtils.isNotEmpty(p.getDatasourceId()) && StringUtils.isNotEmpty(p.getDatasourceColumnName()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(alreadyBindProps)) {
            log.warn("本体{}所有属性都没有关联数据源", ontologyIdentifier);
            return;
        }
        // 处理已绑定数据源的属性
        var alreadyBindPropsMap = alreadyBindProps.stream().collect(Collectors.groupingBy(v -> v.getDatasourceId()));
        var pkDS = pk.get().getDatasourceId();

        alreadyBindPropsMap.forEach((ds, list) -> {
            //主属性表
            if (ds.equals(pkDS)) {
                var columns = list.stream().map(p -> TableColumnDesc.builder()
                        .columnName(p.getDatasourceColumnName())
                        .type(OntologyDataTypeEnum.transfer2Pg(p.getPropertyType()))
                        .description(p.getDisplayName())
                        .isPrimaryKey(p.getApiName().equals(pkColumnName))
                        .build()).collect(Collectors.toList());
                //发送mq消息给数据组织层构建管道
                notifyEntityTableSchemaChange(ontologyIdentifier, schemaName, pkDS, true, columns, null, null, DatasourceEventTypeEnum.CREATE_TABLE);
            }
            // 其他属性关联表
            else {
                var columns = list.stream().map(p -> TableColumnDesc.builder()
                        .columnName(p.getDatasourceColumnName())
                        .type(OntologyDataTypeEnum.transfer2Pg(p.getPropertyType()))
                        .description(p.getDisplayName())
                        .isPrimaryKey(p.getApiName().equals(pkColumnName))
                        .build()).collect(Collectors.toList());
                //增加和主属性关联列
                var mapping = tableFieldMappingMapper.selectBySourceAndTarget(schemaName, pkDS, ds);
                columns.add(TableColumnDesc.builder()
                        .columnName(mapping.getTargetColumnName())
                        .type("int4")
                        .isPrimaryKey(false)
                        .build());
                //发送mq消息给数据组织层构建管道
                notifyEntityTableSchemaChange(ontologyIdentifier, schemaName, ds, false, columns, mapping.getSourceColumnName(), mapping.getTargetColumnName(), DatasourceEventTypeEnum.CREATE_TABLE);
            }
        });


    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void createCategory(PropertyCategoryCreateParam param) {
        var existCategory = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>().eq(PropertyCategory::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));
        var existCategoryMap = existCategory.stream().collect(Collectors.toMap(PropertyCategory::getId, v -> v));

        //校验parentId
        var parentId = param.getParentId();
        if (!((parentId.equals(0) && MapUtils.isEmpty(existCategoryMap))
                || (!parentId.equals(0) && existCategoryMap.containsKey(parentId)))) {
            throw new BusinessException("无效的parentId：" + parentId, HttpStatus.BAD_REQUEST);
        }

        var parentPath = parentId.equals(0) ? "" : existCategoryMap.get(parentId).getPath() + "/";

        var rootNode = CategoryNode.builder()
                .parentId(parentId)
                .children(param.getChildren())
                .name(param.getName())
                .path(parentPath + param.getName())
                .build();

        // 按层级 BFS，每层批量插入
        List<CategoryNode> currentLevel = Lists.newArrayList(rootNode);

        while (CollectionUtils.isNotEmpty(currentLevel)) {
            List<PropertyCategory> batchList = Lists.newArrayList();
            List<CategoryNode> nextLevel = Lists.newArrayList();
            for (var node : currentLevel) {
                var category = PropertyCategory.builder()
                        .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                        .name(node.getName())
                        .parentId(node.getParentId())
                        .path(node.getPath())
                        .build();
                batchList.add(category);
            }
            // 当前层级批量插入
            propertyCategoryService.saveBatch(batchList);
            // 拿到自增 ID 后，构建下一层节点
            for (int i = 0; i < currentLevel.size(); i++) {
                var node = currentLevel.get(i);
                var generatedId = batchList.get(i).getId();
                if (CollectionUtils.isNotEmpty(node.getChildren())) {
                    for (var child : node.getChildren()) {
                        var childNode = CategoryNode.builder()
                                .parentId(generatedId)
                                .path(node.getPath() + "/" + child.getName())
                                .name(child.getName())
                                .children(child.getChildren())
                                .build();
                        nextLevel.add(childNode);
                    }
                }
            }
            currentLevel = nextLevel;
        }
    }

    @Override
    public PropertyCategoryVO getCategory(String ontologyUniqueIdentifier) {
        // 按 id 降序取出，分组后每层 children 即为 id 从大到小
        var categories = propertyCategoryService.list(
                new LambdaQueryWrapper<PropertyCategory>()
                        .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                        .orderByDesc(PropertyCategory::getId));
        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }
        var categoryMap = categories.stream().collect(Collectors.groupingBy(
                PropertyCategory::getParentId, LinkedHashMap::new, Collectors.toList()));
        var roots = categoryMap.get(0);
        if (CollectionUtils.isEmpty(roots)) {
            return null;
        }
        // query properties of this ontology, grouped by propertyCategoryId, so each category node carries its own properties
        var props = list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        var propMap = props.stream()
                .collect(Collectors.groupingBy(OntologyProperty::getPropertyCategoryId,
                        Collectors.mapping(DataConverter::convertToPropertyInfoVO, Collectors.toList())));
        return buildCategoryVO(roots.get(0), categoryMap, propMap);
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void deleteCategory(PropertyCategoryDeleteParam param) {
        var parentCategory = propertyCategoryService.getOne(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .eq(PropertyCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(parentCategory != null, "分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);

        // 查询所有关联节点（节点树）：精确匹配父节点自身，或以 "parentPath/" 为前缀的子孙节点
        var allCategories = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .and(w -> w.eq(PropertyCategory::getPath, parentCategory.getPath())
                        .or()
                        .likeRight(PropertyCategory::getPath, parentCategory.getPath() + "/")));
        if (CollectionUtils.isNotEmpty(allCategories)) {
            var categoryIds = allCategories.stream().map(PropertyCategory::getId).collect(Collectors.toList());
            var props = list(new LambdaQueryWrapper<OntologyProperty>()
                    .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                    .in(OntologyProperty::getPropertyCategoryId, categoryIds));
            PreconditionUtils.checkArgument(CollectionUtils.isEmpty(props), "该分类节点下有关联的属性，不能删除", HttpStatus.FORBIDDEN);
            propertyCategoryService.removeByIds(categoryIds);
        }

    }

    /**
     * 只修改节点名称
     *
     * @param param
     */
    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void updateCategory(PropertyCategoryUpdateParam param) {
        var parentCategory = propertyCategoryService.getOne(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .eq(PropertyCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(parentCategory != null, "分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);
        // 查询所有关联节点（节点树）：精确匹配父节点自身，或以 "parentPath/" 为前缀的子孙节点
        var allCategories = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .and(w -> w.eq(PropertyCategory::getPath, parentCategory.getPath())
                        .or()
                        .likeRight(PropertyCategory::getPath, parentCategory.getPath() + "/")));
        if (CollectionUtils.isNotEmpty(allCategories)) {
            var paths = parentCategory.getPath().split("/");
            paths[paths.length - 1] = param.getName();
            var newPath = String.join("/", paths);
            allCategories.forEach(category -> {
                if (category.getId().equals(param.getCategoryId())) {
                    category.setName(param.getName());
                    category.setPath(newPath);
                } else {
                    var updatedPath = newPath + category.getPath().substring(parentCategory.getPath().length());
                    category.setPath(updatedPath);
                }
            });
            propertyCategoryService.updateBatchById(allCategories);
        }
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void createMetadataSchema(PropertyMetadataSchemaCreateParam param) {
        //校验是否存在已赋值的属性
        var props = list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .isNotNull(OntologyProperty::getMetadata));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(props), "元数据schema下有已赋值的属性，不能创建", HttpStatus.FORBIDDEN);

        var existMetadata = propertyMetadataSchemaService.list(new LambdaQueryWrapper<PropertyMetadataSchema>()
                .eq(PropertyMetadataSchema::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));
        var existMetadataMap = existMetadata.stream().collect(Collectors.toMap(PropertyMetadataSchema::getId, v -> v));
        //校验parentId
        var parentId = param.getParentId();
        if (!((parentId.equals(0) && MapUtils.isEmpty(existMetadataMap))
                || (!parentId.equals(0) && existMetadataMap.containsKey(parentId)))) {
            throw new BusinessException("无效的parentId：" + parentId, HttpStatus.BAD_REQUEST);
        }
        var parentPath = parentId.equals(0) ? "" : existMetadataMap.get(parentId).getPath() + "/";
        var rootNode = PropertyMetadataSchemaCreateParam.MetadataSchemaNode.builder()
                .parentId(parentId)
                .children(param.getChildren())
                .name(param.getName())
                .path(parentPath + param.getName())
                .enumValues(param.getEnumValues())
                .build();

        // 按层级 BFS，每层批量插入
        List<PropertyMetadataSchemaCreateParam.MetadataSchemaNode> currentLevel = Lists.newArrayList(rootNode);

        while (CollectionUtils.isNotEmpty(currentLevel)) {
            List<PropertyMetadataSchema> batchList = Lists.newArrayList();
            List<PropertyMetadataSchemaCreateParam.MetadataSchemaNode> nextLevel = Lists.newArrayList();
            for (var node : currentLevel) {
                var schema = PropertyMetadataSchema.builder()
                        .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                        .name(node.getName())
                        .parentId(node.getParentId())
                        .path(node.getPath())
                        .enumValues(CollectionUtils.isNotEmpty(node.getEnumValues()) ?
                                String.join(",", node.getEnumValues()) : null)
                        .build();
                batchList.add(schema);
            }
            // 当前层级批量插入
            propertyMetadataSchemaService.saveBatch(batchList);
            // 拿到自增 ID 后，构建下一层节点
            for (int i = 0; i < currentLevel.size(); i++) {
                var node = currentLevel.get(i);
                var generatedId = batchList.get(i).getId();
                if (CollectionUtils.isNotEmpty(node.getChildren())) {
                    for (var child : node.getChildren()) {
                        var childNode = PropertyMetadataSchemaCreateParam.MetadataSchemaNode.builder()
                                .parentId(generatedId)
                                .path(node.getPath() + "/" + child.getName())
                                .name(child.getName())
                                .children(child.getChildren())
                                .enumValues(child.getEnumValues())
                                .build();
                        nextLevel.add(childNode);
                    }
                }
            }
            currentLevel = nextLevel;
        }
    }

    /**
     * 只修改名称和枚举值列表
     *
     * @param param
     */
    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void updateMetadataSchema(PropertyMetadataSchemaUpdateParam param) {
        //校验是否存在已赋值的属性
        var props = list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .isNotNull(OntologyProperty::getMetadata));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(props), "元数据schema下有已赋值的属性，不能修改", HttpStatus.FORBIDDEN);

        var parentSchema = propertyMetadataSchemaService.getOne(new LambdaQueryWrapper<PropertyMetadataSchema>()
                .eq(PropertyMetadataSchema::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .eq(PropertyMetadataSchema::getId, param.getMetadataSchemaId()));
        PreconditionUtils.checkArgument(parentSchema != null, "元数据节点" + param.getMetadataSchemaId() + "不存在", HttpStatus.BAD_REQUEST);
        // 查询所有关联元数据：精确匹配父节点自身，或以 "parentPath/" 为前缀的子孙节点
        var allSchemas = propertyMetadataSchemaService.list(new LambdaQueryWrapper<PropertyMetadataSchema>()
                .eq(PropertyMetadataSchema::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .and(w -> w.eq(PropertyMetadataSchema::getPath, parentSchema.getPath())
                        .or()
                        .likeRight(PropertyMetadataSchema::getPath, parentSchema.getPath() + "/")));
        if (CollectionUtils.isNotEmpty(allSchemas)) {
            var paths = parentSchema.getPath().split("/");
            paths[paths.length - 1] = param.getName();
            var newPath = String.join("/", paths);
            allSchemas.forEach(schema -> {
                if (schema.getId().equals(param.getMetadataSchemaId())) {
                    schema.setName(param.getName())
                            .setEnumValues(CollectionUtils.isNotEmpty(param.getEnumValues()) ? String.join(",", param.getEnumValues()) : null);
                    schema.setPath(newPath);
                } else {
                    var updatedPath = newPath + schema.getPath().substring(parentSchema.getPath().length());
                    schema.setPath(updatedPath);
                }
            });
            propertyMetadataSchemaService.updateBatchById(allSchemas);
        }
    }


    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void deleteMetadataSchema(PropertyMetadataSchemaDeleteParam param) {

        var parentMetadata = propertyMetadataSchemaService.getOne(new LambdaQueryWrapper<PropertyMetadataSchema>()
                .eq(PropertyMetadataSchema::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .eq(PropertyMetadataSchema::getId, param.getMetadataSchemaId()));
        PreconditionUtils.checkArgument(parentMetadata != null, "无效的metadata schema节点" + param.getMetadataSchemaId() + "不存在", HttpStatus.BAD_REQUEST);

        // 查询所有关联节点（节点树）：精确匹配父节点自身，或以 "parentPath/" 为前缀的子孙节点
        var allMetadata = propertyMetadataSchemaService.list(new LambdaQueryWrapper<PropertyMetadataSchema>()
                .eq(PropertyMetadataSchema::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .and(w -> w.eq(PropertyMetadataSchema::getPath, parentMetadata.getPath())
                        .or()
                        .likeRight(PropertyMetadataSchema::getPath, parentMetadata.getPath() + "/")));
        if (CollectionUtils.isNotEmpty(allMetadata)) {
            var ids = allMetadata.stream().map(PropertyMetadataSchema::getId).collect(Collectors.toList());
            var props = list(new LambdaQueryWrapper<OntologyProperty>()
                    .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                    .isNotNull(OntologyProperty::getMetadata));
            PreconditionUtils.checkArgument(CollectionUtils.isEmpty(props), "元数据schema下有已赋值的属性，不能删除", HttpStatus.FORBIDDEN);
            propertyMetadataSchemaService.removeByIds(ids);
        }
    }

    @Override
    public JsonNode getMetadataSchema(String ontologyUniqueIdentifier) {
        var schemas = propertyMetadataSchemaService.list(new LambdaQueryWrapper<PropertyMetadataSchema>()
                .eq(PropertyMetadataSchema::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));

        if (CollectionUtils.isEmpty(schemas)) {
            return null;
        }

        Set<String> allPaths = schemas.stream()
                .map(PropertyMetadataSchema::getPath)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toSet());

        ObjectNode root = jsonMapper.createObjectNode();

        for (var schema : schemas) {
            String path = schema.getPath();
            if (StringUtils.isEmpty(path)) {
                continue;
            }
            String[] parts = path.split("/");
            ObjectNode current = root;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLast = (i == parts.length - 1);
                boolean isLeaf = isLast && allPaths.stream().noneMatch(p -> p.startsWith(path + "/"));

                if (isLeaf) {
                    String enumValues = schema.getEnumValues();
                    current.put(part, StringUtils.isEmpty(enumValues) ? "" : enumValues);
                } else {
                    if (!current.has(part)) {
                        current.set(part, jsonMapper.createObjectNode());
                    }
                    current = (ObjectNode) current.get(part);
                }
            }
        }

        return root;
    }

    @Override
    public PropertyMetadataSchemaVO getMetadataSchemaTree(String ontologyUniqueIdentifier) {
        var schemas = propertyMetadataSchemaService.list(
                new LambdaQueryWrapper<PropertyMetadataSchema>()
                        .eq(PropertyMetadataSchema::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        if (CollectionUtils.isEmpty(schemas)) {
            return null;
        }
        var schemaMap = schemas.stream().collect(Collectors.groupingBy(PropertyMetadataSchema::getParentId));
        var roots = schemaMap.get(0);
        if (CollectionUtils.isEmpty(roots)) {
            return null;
        }
        return buildSchemaVO(roots.get(0), schemaMap);
    }


    /**
     * 1 属性自动关联数据源
     * 2 发送mq消息给数据层构建数据管道
     * <p>
     * 说明：1 只针对未关联数据源的属性
     * 2 实体表列的数据只做增量更新，删除属性不会同步删除实体表的列，需要人工确认后通过后门接口删除
     * 3 修改属性数据类型：先解绑属性已有数据源，再修改属性类型，最后重新绑定数据源
     * 4 重新绑定时若目标列已存在且列类型与属性映射类型冲突：实体表无数据则直接修改列类型后绑定；
     * 实体表已有实例数据则该属性不绑定、不改动该列，明细在返回结果的blockedItems中给出；主键id冲突时整单失败
     *
     * @param ontologyIdentifier
     */
    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public AutoBindDatasourceResultVO autoBindDatasource(String ontologyIdentifier) {
        var ontology = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyIdentifier));
        // 获取schemaName
        var schemaName = resolveSchemaName(ontologyIdentifier);
        // 校验本体属性是否存在
        var props = list(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        if (CollectionUtils.isEmpty(props)) {
            log.warn("本体{}没有属性", ontologyIdentifier);
            return autoBindResult(0, 0, Lists.newArrayList(), "本体" + ontologyIdentifier + "没有属性，无需关联数据源");
        }
        String pkColumnName = "id";
        String mainStorageGroup = "main";
        // 校验本体主键id是否存在且属于主存储分组
        var pk = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        if (!pk.isPresent()
                || !StringUtils.equals(pk.get().getApiName(), pkColumnName)
                || !StringUtils.equals(pk.get().getStorageGroup(), mainStorageGroup)) {
            throw new BusinessException("本体" + ontologyIdentifier + "没有id主键属性或id不属于主存储分组");
        }
        // 检查属性数据源
        var notBindProps = props.stream()
                .filter(p -> StringUtils.isEmpty(p.getDatasourceId()) && StringUtils.isEmpty(p.getDatasourceColumnName()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(notBindProps)) {
            var totalBoundCount = autoBindBoundTotal(props);
            log.warn("本体{}所有属性已关联数据源", ontologyIdentifier);
            return autoBindResult(0, totalBoundCount, Lists.newArrayList(), autoBindMessage(0, totalBoundCount, Lists.newArrayList()));
        }
        // 处理未绑定数据源的属性
        var notBindPropsMap = notBindProps.stream().collect(Collectors.groupingBy(v -> v.getStorageGroup()));
        var pkDS = pk.get().getDatasourceId();
        var mainDS = StringUtils.isEmpty(pkDS) ? ontology.getApiName() : pkDS;

        // 先判定再执行DDL：实体表已有实例数据时，类型冲突的列不绑定也不改动；空表则交由addColumns改列类型
        Map<String, List<OntologyProperty>> bindablePropsMap = Maps.newLinkedHashMap();
        Map<String, Boolean> tableExistMap = Maps.newHashMap();
        List<AutoBindDatasourceResultVO.BlockedItem> blockedItems = Lists.newArrayList();
        for (var entry : notBindPropsMap.entrySet()) {
            var storageGroup = entry.getKey();
            var tableName = mainStorageGroup.equals(storageGroup) ? mainDS : storageGroup;
            tableExistMap.computeIfAbsent(tableName, name -> Boolean.TRUE.equals(tableMetadataMapper.isTableExist(schemaName, name)));
            Map<String, String> columnTypes = Maps.newHashMap();
            tableMetadataMapper.queryColumnMetadata(schemaName, tableName)
                    .forEach(column -> columnTypes.put(column.getColumnName(), StringUtils.defaultString(column.getType())));
            List<OntologyProperty> bindableProps = Lists.newArrayList();
            Boolean tableHasData = null;
            for (var prop : entry.getValue()) {
                var propertyType = prop.getPropertyType();
                var expectedType = OntologyDataTypeEnum.transfer2Pg(propertyType);
                if (StringUtils.isEmpty(expectedType)) {
                    blockedItems.add(autoBindBlockedItem(prop, storageGroup, tableName, null, null,
                            "属性类型【" + (propertyType == null ? "空" : propertyType.getValue()) + "】暂不支持映射数据源列类型"));
                    continue;
                }
                var actualType = columnTypes.get(prop.getApiName());
                if (actualType == null || isColumnTypeEquivalent(actualType, expectedType)) {
                    bindableProps.add(prop);
                    continue;
                }
                if (tableHasData == null) {
                    tableHasData = Boolean.TRUE.equals(tableMetadataMapper.hasAnyRow(schemaName, tableName));
                }
                if (tableHasData) {
                    blockedItems.add(autoBindBlockedItem(prop, storageGroup, tableName, actualType, expectedType,
                            "实体表【" + tableName + "】已有实例数据，列【" + prop.getApiName() + "】当前类型【" + actualType
                                    + "】与属性映射类型【" + expectedType + "】冲突，跳过绑定以保留已有数据"));
                } else {
                    bindableProps.add(prop);
                }
            }
            if (CollectionUtils.isNotEmpty(bindableProps)) {
                bindablePropsMap.put(storageGroup, bindableProps);
            }
        }
        // 主键绑不上时实体节点同步与数据管道都无法建立，不做部分绑定
        var blockedPk = blockedItems.stream()
                .filter(v -> mainStorageGroup.equals(v.getStorageGroup()) && pkColumnName.equals(v.getPropertyApiName()))
                .findFirst();
        if (blockedPk.isPresent()) {
            throw new BusinessException("本体" + ontologyIdentifier + "主键属性" + pkColumnName + "无法关联数据源："
                    + blockedPk.get().getReason(), HttpStatus.BAD_REQUEST);
        }
        if (bindablePropsMap.isEmpty()) {
            var totalBoundCount = autoBindBoundTotal(props);
            var blockedMessage = autoBindMessage(0, totalBoundCount, blockedItems);
            log.warn("本体{}属性自动关联数据源未绑定任何属性：{}", ontologyIdentifier, blockedMessage);
            return autoBindResult(0, totalBoundCount, blockedItems, blockedMessage);
        }

        bindablePropsMap.forEach((ds, list) -> {
            //主属性表
            if (ds.equals(mainStorageGroup)) {
                //检查表名是否存在：不存在创建新表，存在添加列
                var exist = Boolean.TRUE.equals(tableExistMap.get(mainDS));
                var columns = list.stream().map(p -> TableColumnDesc.builder()
                        .columnName(p.getApiName())
                        .type(OntologyDataTypeEnum.transfer2Pg(p.getPropertyType()))
                        .description(p.getDisplayName())
                        .isPrimaryKey(p.getApiName().equals(pkColumnName))
                        .build()).collect(Collectors.toList());
                if (!exist) {
                    //创建新表
                    var table = TableDesc.builder()
                            .description(ontology.getDisplayName())
                            .tableName(mainDS)
                            .build();
                    tableMetadataMapper.createTable(schemaName, table, columns);
                    //发送mq消息给数据组织层构建管道
                    notifyEntityTableSchemaChange(ontologyIdentifier, schemaName, mainDS, true, columns, null, null, DatasourceEventTypeEnum.CREATE_TABLE);
                } else {
                    //创建列
                    tableMetadataMapper.addColumns(schemaName, mainDS, columns);
                    //发送mq消息给数据组织层构建管道
                    notifyEntityTableSchemaChange(ontologyIdentifier, schemaName, mainDS, true, columns, null, null, DatasourceEventTypeEnum.ADD_COLUMN);
                }
                //更新数据源属性
                list.forEach(p -> p.setDatasourceId(mainDS).setDatasourceColumnName(p.getApiName()).setDatasourceSchema(schemaName));
            }
            // 其他属性关联表
            else {
                //检查表名是否存在：不存在创建新表，存在添加列
                var exist = Boolean.TRUE.equals(tableExistMap.get(ds));
                var columns = list.stream().map(p -> TableColumnDesc.builder()
                        .columnName(p.getApiName())
                        .type(OntologyDataTypeEnum.transfer2Pg(p.getPropertyType()))
                        .description(p.getDisplayName())
                        .isPrimaryKey(p.getApiName().equals(pkColumnName))
                        .build()).collect(Collectors.toList());
                //增加和主属性关联列
                var relatedColumn = mainDS + "_" + pkColumnName;
                if (!exist) {
                    columns.add(TableColumnDesc.builder()
                            .columnName(relatedColumn)
                            .type("int4")
                            .description(ontology.getDisplayName() + "主键")
                            .isPrimaryKey(false)
                            .build());
                    //创建新表
                    var table = TableDesc.builder()
                            .description(ontology.getDisplayName() + "_" + ds)
                            .tableName(ds)
                            .build();
                    tableMetadataMapper.createTable(schemaName, table, columns);
                    //创建关联舰的索引
                    tableMetadataMapper.createIndex(schemaName, table.getTableName(), relatedColumn);
                    //插入属性表关联关系
                    tableFieldMappingMapper.insertMapping(schemaName, TableFieldMapping.builder()
                            .sourceTableName(mainDS)
                            .sourceColumnName(pkColumnName)
                            .targetTableName(ds)
                            .targetColumnName(relatedColumn)
                            .build());
                    //发送mq消息给数据组织层构建管道
                    notifyEntityTableSchemaChange(ontologyIdentifier, schemaName, ds, false, columns, pkColumnName, relatedColumn, DatasourceEventTypeEnum.CREATE_TABLE);
                } else {
                    //创建列
                    tableMetadataMapper.addColumns(schemaName, ds, columns);
                    // 检查并添加缺失的关联列
                    var columnTypes = Maps.<String, String>newHashMap();
                    tableMetadataMapper.queryColumnMetadata(schemaName, ds)
                            .forEach(column -> columnTypes.put(column.getColumnName(), StringUtils.defaultString(column.getType())));
                    if (!columnTypes.containsKey(relatedColumn)) {
                        tableMetadataMapper.addColumns(schemaName, ds, Lists.newArrayList(TableColumnDesc.builder()
                                .columnName(relatedColumn)
                                .type("int4")
                                .description(ontology.getDisplayName() + "主键")
                                .isPrimaryKey(false)
                                .build()));
                        tableMetadataMapper.createIndex(schemaName, ds, relatedColumn);
                        log.info("本体{}关联表{}已存在但关联列{}缺失，已补充创建", ontologyIdentifier, ds, relatedColumn);
                    }
                    //发送mq消息给数据组织层构建管道
                    notifyEntityTableSchemaChange(ontologyIdentifier, schemaName, ds, false, columns, pkColumnName, relatedColumn, DatasourceEventTypeEnum.ADD_COLUMN);
                    // 表已存在时，检查并创建缺失的映射关系
                    var existMapping = tableFieldMappingMapper.selectBySourceAndTarget(schemaName, mainDS, ds);
                    if (existMapping == null) {
                        tableFieldMappingMapper.insertMapping(schemaName, TableFieldMapping.builder()
                                .sourceTableName(mainDS)
                                .sourceColumnName(pkColumnName)
                                .targetTableName(ds)
                                .targetColumnName(relatedColumn)
                                .build());
                        log.info("本体{}关联表{}已存在但映射缺失，已补充创建映射", ontologyIdentifier, ds);
                    }
                }
                //更新数据源属性
                list.forEach(p -> p.setDatasourceId(ds).setDatasourceColumnName(p.getApiName()).setDatasourceSchema(schemaName));
            }
        });
        //批量更新本体属性数据源
        var bindDsProps = bindablePropsMap.values().stream().flatMap(List::stream).collect(Collectors.toList());
        updateBatchById(bindDsProps);
        // props与bindDsProps是同一批对象实例，DDL阶段已就地回填datasource字段，可直接统计累计已绑定数
        var totalBoundCount = autoBindBoundTotal(props);
        var message = autoBindMessage(bindDsProps.size(), totalBoundCount, blockedItems);
        if (CollectionUtils.isNotEmpty(blockedItems)) {
            log.warn("本体{}属性自动关联数据源部分跳过：{}", ontologyIdentifier, message);
            blockedItems.forEach(item -> log.warn("被阻止的属性: {} (存储分组={}, 原因={})", 
                    item.getPropertyApiName(), item.getStorageGroup(), item.getReason()));
        }
        return autoBindResult(bindDsProps.size(), totalBoundCount, blockedItems, message);
    }


    @Override
    public List<String> getStorageGroup(String ontologyUniqueIdentifier) {
        var props = list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));

        var groups = props.stream()
                .map(OntologyProperty::getStorageGroup)
                .collect(Collectors.toSet());

        var main = "main";
        List<String> result = Lists.newArrayList(main);
        groups.stream()
                .filter(group -> !main.equals(group))
                .sorted()
                .forEach(result::add);
        return result;
    }

    private PropertyMetadataSchemaVO buildSchemaVO(PropertyMetadataSchema schema, Map<Integer, List<PropertyMetadataSchema>> schemaMap) {
        var vo = new PropertyMetadataSchemaVO()
                .setEnumValues(StringUtils.isEmpty(schema.getEnumValues()) ?
                        null : Lists.newArrayList(StringUtils.split(schema.getEnumValues(), ",")))
                .setSchemaId(schema.getId())
                .setName(schema.getName());
        var children = schemaMap.get(schema.getId());
        if (CollectionUtils.isNotEmpty(children)) {
            vo.setChildren(children.stream()
                    .map(child -> buildSchemaVO(child, schemaMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }

    private PropertyCategoryVO buildCategoryVO(PropertyCategory category,
                                               Map<Integer, List<PropertyCategory>> categoryMap,
                                               Map<Integer, List<OntologyPropertyInfoVO>> propMap) {
        var vo = new PropertyCategoryVO()
                .setCategoryId(category.getId())
                .setName(category.getName());
        var propInfos = propMap.get(category.getId());
        if (CollectionUtils.isNotEmpty(propInfos)) {
            vo.setPropertyInfos(propInfos);
        }
        var children = categoryMap.get(category.getId());
        if (CollectionUtils.isNotEmpty(children)) {
            vo.setChildren(children.stream()
                    .map(child -> buildCategoryVO(child, categoryMap, propMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }


    /**
     * 先只考虑这两种case
     * CREATE_TABLE(1, "创建实体属性表（主表或关联表）"),
     * ADD_COLUMN(2, "添加实体表属性"),
     *
     * @param tableName
     * @param columns
     * @param eventType
     */
    @SneakyThrows
    private void notifyEntityTableSchemaChange(String ontologyUniqueIdentifier,
                                               String schemaName,
                                               String tableName,
                                               Boolean isMainTable,
                                               List<TableColumnDesc> columns,
                                               String associatedColumnName,
                                               String associateKeyColumnName,
                                               DatasourceEventTypeEnum eventType) {

        var columnDTOList = columns.stream().map(v -> EntityDatasourceColumnDTO.builder()
                        .isPrimaryKey(v.getIsPrimaryKey())
                        .description(v.getDescription())
                        .columnName(v.getColumnName())
                        .datasourceColumnType(v.getType())
                        .isAssociateKey(StringUtils.equals(v.getColumnName(), associateKeyColumnName))
                        .associateColumnName(StringUtils.equals(v.getColumnName(), associateKeyColumnName) ? associatedColumnName : null)
                        .build())
                .collect(Collectors.toList());

        if (DatasourceEventTypeEnum.CREATE_TABLE.equals(eventType) && !columnDTOList.stream().anyMatch(v -> v.getIsPrimaryKey())) {
            columnDTOList.add(EntityDatasourceColumnDTO.builder()
                    .isAssociateKey(false)
                    .isPrimaryKey(true)
                    .description("主键")
                    .columnName("id")
                    .datasourceColumnType("int4")
                    .build());
        }

        var datasourceDTO = EntityDatasourceDTO.builder()
                .tableName("entity_datasource." + schemaName + "." + tableName)
                .isMainDatasource(isMainTable)
                .columns(columnDTOList)
                .build();

        var changeEventDTO = EntityDatasourceSchemaChangeEventDTO.builder()
                .ontologyUniqueIdentifier(ontologyUniqueIdentifier)
                .type(eventType)
                .datasource(Lists.newArrayList(datasourceDTO))
                .build();

        producer.sendMessage(routingKey, jsonMapper.writeValueAsString(changeEventDTO));
    }


    private void checkPrimaryKey(List<OntologyProperty> properties, PropertyDatasourceParam datasource) {
        var existPrimaryKey = properties.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(!existPrimaryKey.isPresent(), "属性主键已存在", HttpStatus.BAD_REQUEST);
        if (datasource != null) {
            var schemaName = datasource.getSchemaName();
            var ds = datasource.getDatasourceId();
            var pk = datasource.getDatasourceColumnName();
            var pkCol = tableMetadataMapper.queryPrimaryKeyColumnName(schemaName, ds);
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


    private boolean isTitleKeyChanged(OntologyProperty original, OntologyPropertyUpdateParam param) {
        boolean wasTitleKey = original.getIsTitleKey() != null && original.getIsTitleKey() == 1;
        boolean nowTitleKey = param.getIsTitleKey();
        if (wasTitleKey != nowTitleKey) {
            return true;
        }
        if (!nowTitleKey) {
            return false;
        }
        var datasource = param.getDatasource();
        var newDatasourceId = datasource != null ? datasource.getDatasourceId() : null;
        var newColumnName = datasource != null ? datasource.getDatasourceColumnName() : null;
        return !StringUtils.equals(original.getDatasourceId(), newDatasourceId)
                        || !StringUtils.equals(original.getDatasourceColumnName(), newColumnName);
    }

    private void buildEntityNodes(String ontologyIdentifier, boolean titleKeyChanged) {
        var primaryProperty = getOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));

        var titleProperty = getOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                .eq(OntologyProperty::getIsTitleKey, 1));

        var titleColumn = "";
        if (titleProperty != null
                && primaryProperty != null
                && StringUtils.equals(titleProperty.getDatasourceSchema(), primaryProperty.getDatasourceSchema())
                && StringUtils.equals(titleProperty.getDatasourceId(), primaryProperty.getDatasourceId())) {
            titleColumn = titleProperty.getDatasourceColumnName();
        }

        var anyNode = entityService.findOneByOntologyUniqIdentifier(ontologyIdentifier);
        //主键未创建，不需要生成实体节点
        if (primaryProperty == null && anyNode == null) {
            return;
        }
        //新增主键数据源，且不存在实体节点
        else if (primaryProperty != null && anyNode == null) {
            if (StringUtils.isNotEmpty(primaryProperty.getDatasourceId())
                    && StringUtils.isNotEmpty(primaryProperty.getDatasourceSchema())) {
                entityService.syncNodes(ontologyIdentifier, primaryProperty.getDatasourceSchema(), primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
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
                    entityService.syncNodes(ontologyIdentifier, primaryProperty.getDatasourceSchema(), primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
                }
                //仅名称键变更时同步 displayName，避免普通属性更新触发全量 query
                else if (titleKeyChanged) {
                    entityService.syncNodes(ontologyIdentifier, primaryProperty.getDatasourceSchema(), primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
                }
            }
            //主键数据源取消设置
            else {
                entityService.deleteNodesAndRelationsByOntologyId(ontologyIdentifier);
            }
        }
    }

    private void validateMetadataAgainstSchema(JsonNode metadata, JsonNode schema, String path) {
        if (metadata.isObject()) {
            PreconditionUtils.checkArgument(schema.isObject(),
                    "元数据schema不匹配，期望对象类型，路径：" + path, HttpStatus.BAD_REQUEST);
            var metadataObj = (ObjectNode) metadata;
            var schemaObj = (ObjectNode) schema;
            var fieldNames = metadataObj.fieldNames();
            while (fieldNames.hasNext()) {
                var fieldName = fieldNames.next();
                var childPath = path.isEmpty() ? fieldName : path + "/" + fieldName;
                PreconditionUtils.checkArgument(schemaObj.has(fieldName),
                        "元数据字段不在schema中：" + childPath, HttpStatus.BAD_REQUEST);
                var childMetadata = metadataObj.get(fieldName);
                var childSchema = schemaObj.get(fieldName);
                if (childMetadata.isObject()) {
                    validateMetadataAgainstSchema(childMetadata, childSchema, childPath);
                }
            }
        }
    }

    private String resolveSchemaName(String ontologyUniqueIdentifier) {
        var meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier));
        PreconditionUtils.checkNotNull(meta, "本体不存在", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkNotNull(meta.getOntologySpaceId(), "本体未关联空间", HttpStatus.BAD_REQUEST);
        var space = spaceMapper.selectById(meta.getOntologySpaceId());
        PreconditionUtils.checkNotNull(space, "本体空间不存在", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(space.getApiName()),
                "本体空间apiName为空", HttpStatus.BAD_REQUEST);
        return space.getApiName();
    }

    private AutoBindDatasourceResultVO.BlockedItem autoBindBlockedItem(OntologyProperty prop, String storageGroup, String tableName,
                                                                      String actualType, String expectedType, String reason) {
        return AutoBindDatasourceResultVO.BlockedItem.builder()
                .propertyApiName(prop.getApiName())
                .propertyDisplayName(prop.getDisplayName())
                .storageGroup(storageGroup)
                .tableName(tableName)
                .columnName(prop.getApiName())
                .actualColumnType(actualType)
                .expectedColumnType(expectedType)
                .reason(reason)
                .build();
    }

    private AutoBindDatasourceResultVO autoBindResult(int boundCount, int totalBoundCount, List<AutoBindDatasourceResultVO.BlockedItem> blockedItems, String message) {
        return AutoBindDatasourceResultVO.builder()
                .boundCount(boundCount)
                .totalBoundCount(totalBoundCount)
                .blockedItems(blockedItems)
                .message(message)
                .build();
    }

    /**
     * 本体下已关联数据源的属性总数（含本次新关联），判定口径与未绑定属性筛选互补：
     * datasourceId与datasourceColumnName都非空才算已关联。
     */
    private int autoBindBoundTotal(List<OntologyProperty> props) {
        if (CollectionUtils.isEmpty(props)) {
            return 0;
        }
        return (int) props.stream()
                .filter(p -> StringUtils.isNotEmpty(p.getDatasourceId()) && StringUtils.isNotEmpty(p.getDatasourceColumnName()))
                .count();
    }

    private String autoBindMessage(int boundCount, int totalBoundCount, List<AutoBindDatasourceResultVO.BlockedItem> blockedItems) {
        var message = "本次新关联 " + boundCount + " 个属性，共 " + totalBoundCount + " 个属性已关联数据源";
        if (CollectionUtils.isEmpty(blockedItems)) {
            return message;
        }
        var names = blockedItems.stream()
                .map(AutoBindDatasourceResultVO.BlockedItem::getPropertyApiName)
                .collect(Collectors.joining("、"));
        return message + "，" + blockedItems.size() + " 个属性未关联数据源：" + names;
    }

    private boolean isColumnTypeEquivalent(String actualType, String expectedType) {
        var actualFamily = pgTypeFamily(actualType);
        return actualFamily != null && actualFamily.equals(pgTypeFamily(expectedType));
    }

    /**
     * PG列类型归族：忽略长度/精度修饰与写法别名（int4=integer、text=character varying、numeric=decimal）。
     * 识别不了的类型（jsonb、uuid、bytea、geometry、数组、自定义类型等）返回null表示不可比较，按类型冲突处理。
     */
    private String pgTypeFamily(String pgType) {
        if (StringUtils.isEmpty(pgType)) {
            return null;
        }
        return switch (StringUtils.substringBefore(pgType, "(").trim().toLowerCase()) {
            case "bool", "boolean" -> "bool";
            case "int2", "smallint", "int4", "integer", "serial" -> "int";
            case "int8", "bigint", "bigserial" -> "bigint";
            case "real", "float4" -> "float";
            case "float8", "double precision" -> "double";
            case "numeric", "decimal" -> "decimal";
            case "text", "varchar", "character varying", "character", "char", "bpchar", "name" -> "string";
            case "date" -> "date";
            case "timestamp", "timestamptz", "timestamp without time zone", "timestamp with time zone", "datetime" -> "timestamp";
            case "time", "timetz", "time without time zone", "time with time zone" -> "time";
            default -> null;
        };
    }

}