package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyCreateDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyMetaDataDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaStatisticVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@Slf4j
public class OntologyMetaServiceImpl extends ServiceImpl<OntologyMetaMapper, OntologyMeta> implements OntologyMetaService {

    @Lazy
    @Resource
    private OntologySpaceService spaceService;

    @Resource
    private OntologyActionLinkService actionLinkService;

    @Resource
    private OntologyPropertyService ontologyPropertyService;

    @Resource
    private OntologyActionMappingInService actionMappingInService;

    @Resource
    private OntologyLinkGroupService linkService;

    @Resource
    private FunctionService functionService;

    @Resource
    private OntologyActionService actionService;

    @Resource
    private OntologyGroupService groupService;

    @Resource
    private EntityService entityService;

    @Resource
    private OntologyLemmaService lemmaService;

    @Resource
    private PropertyCategoryService propertyCategoryService;

    @Resource
    private OntologyCategoryService categoryService;

    @Resource
    private PropertyMetadataSchemaService propertyMetadataSchemaService;

    @Lazy
    @Resource
    private OntologyMetaServiceImpl proxyService;


    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    @Transactional(value = "mainTransactionManager")
    public String createOntology(OntologyMetaCreateParam ontologyCreateParam) {
        //参数校验:displayName,apiName,groupIds
        var duplicateDisplayName = getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, ontologyCreateParam.getSpaceId())
                .eq(OntologyMeta::getDisplayName, ontologyCreateParam.getDisplayName())
        );
        PreconditionUtils.checkIsNull(duplicateDisplayName, "本体显示名称 '" + ontologyCreateParam.getDisplayName() + "' 已存在", HttpStatus.BAD_REQUEST);
        var duplicateApiName = getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, ontologyCreateParam.getSpaceId())
                .eq(OntologyMeta::getApiName, ontologyCreateParam.getApiName())
        );
        PreconditionUtils.checkIsNull(duplicateApiName, "本体api名称 '" + ontologyCreateParam.getApiName() + "' 已存在", HttpStatus.BAD_REQUEST);
        var groupIds = ontologyCreateParam.getGroupIds();
        if (CollectionUtils.isNotEmpty(groupIds)) {
            List<OntologyGroup> list = groupService.list(new LambdaQueryWrapper<OntologyGroup>().in(OntologyGroup::getGroupId, groupIds));
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(list) && list.size() == groupIds.size(), "无效的本体分组ID", HttpStatus.BAD_REQUEST);
        }
        var meta = OntologyMeta.builder()
                .uniqueIdentifier(IdGenerator.generateUUID())
                .apiName(ontologyCreateParam.getApiName())
                .description(ontologyCreateParam.getDescription())
                .displayName(ontologyCreateParam.getDisplayName())
                .status(Status.ENABLE.getValue())
                .icon(ontologyCreateParam.getIconUrl())
                .ontologySpaceId(ontologyCreateParam.getSpaceId())
                .build();
        //自主创建
        if (StringUtils.isEmpty(ontologyCreateParam.getParentOntologyUniqueIdentifier())) {
            meta.setMetaGroupId(CollectionUtils.isNotEmpty(groupIds) ? String.join(",", groupIds) : null)
                    .setOntologyCategoryId(ontologyCreateParam.getCategoryId());
            save(meta);
            // 默认创建属性分类树根节点
            var propertyCategoryParam = PropertyCategoryCreateParam.builder()
                    .parentId(0)
                    .name("根节点")
                    .ontologyIdentifier(meta.getUniqueIdentifier())
                    .build();
            ontologyPropertyService.createCategory(propertyCategoryParam);
        }//继承创建
        else {
            createOntologyByInherit(ontologyCreateParam, meta);
        }
        return meta.getUniqueIdentifier();
    }


    //继承创建：继承目标本体属性、关系、行为；不继承实体数据源、行为调度
    private void createOntologyByInherit(OntologyMetaCreateParam ontologyCreateParam, OntologyMeta meta) {
        var childIdentifier = meta.getUniqueIdentifier();
        var parentIdentifier = ontologyCreateParam.getParentOntologyUniqueIdentifier();
        //校验父本体
        var parentOntology = getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, parentIdentifier).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkArgument(parentOntology != null, "父本体不存在", ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
        //创建子本体元数据
        meta.setMetaGroupId(parentOntology.getMetaGroupId())
                .setParentUniqueIdentifier(parentIdentifier)
                .setStatus(parentOntology.getStatus())
                .setDescription(parentOntology.getDescription())
                .setOntologyCategoryId(parentOntology.getOntologyCategoryId());
        save(meta);
        //创建属性分类
        var parentCategories = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, parentIdentifier));
        Map<Integer, Integer> oldToNewCategoryIdMap = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(parentCategories)) {
            var parentCategoryByParentId = parentCategories.stream().collect(Collectors.groupingBy(PropertyCategory::getParentId));
            var rootCategory = parentCategoryByParentId.get(0).get(0);
            var rootNode = buildCategoryTree(rootCategory, parentCategoryByParentId);
            var createParam = PropertyCategoryCreateParam.builder()
                    .parentId(0)
                    .name(rootNode.getName())
                    .children(rootNode.getChildren())
                    .ontologyIdentifier(childIdentifier)
                    .build();
            ontologyPropertyService.createCategory(createParam);
            var childCategories = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>()
                    .eq(PropertyCategory::getOntologyUniqueIdentifier, childIdentifier));
            var childCategoryByPath = childCategories.stream().collect(Collectors.toMap(PropertyCategory::getPath, PropertyCategory::getId));
            for (var parentCategory : parentCategories) {
                var newId = childCategoryByPath.get(parentCategory.getPath());
                if (newId != null) {
                    oldToNewCategoryIdMap.put(parentCategory.getId(), newId);
                }
            }
        }
        //创建属性元数据
        var parentMetadataSchemas = propertyMetadataSchemaService.list(new LambdaQueryWrapper<PropertyMetadataSchema>()
                .eq(PropertyMetadataSchema::getOntologyUniqueIdentifier, parentIdentifier));
        if (CollectionUtils.isNotEmpty(parentMetadataSchemas)) {
            var parentSchemaByParentId = parentMetadataSchemas.stream().collect(Collectors.groupingBy(PropertyMetadataSchema::getParentId));
            var rootSchema = parentSchemaByParentId.get(0).get(0);
            var rootNode = buildMetadataSchemaTree(rootSchema, parentSchemaByParentId);
            var createSchemaParam = PropertyMetadataSchemaCreateParam.builder()
                    .parentId(0)
                    .name(rootNode.getName())
                    .enumValues(rootNode.getEnumValues())
                    .children(rootNode.getChildren())
                    .ontologyIdentifier(childIdentifier)
                    .build();
            ontologyPropertyService.createMetadataSchema(createSchemaParam);
        }
        // 创建属性
        var parentProperties = ontologyPropertyService.list(new LambdaUpdateWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyCreateParam.getParentOntologyUniqueIdentifier()));
        var childProps = parentProperties.stream().map(v -> OntologyProperty.builder()
                        .ontologyUniqueIdentifier(childIdentifier)
                        .apiName(v.getApiName())
                        .description(v.getDescription())
                        .displayName(v.getDisplayName())
                        .isPrimaryKey(v.getIsPrimaryKey())
                        .isTitleKey(v.getIsTitleKey())
                        .propertyType(v.getPropertyType())
                        .status(v.getStatus())
                        .uniqueIdentifier(IdGenerator.generateUUID())
                        .defaultValue(v.getDefaultValue())
                        .storageGroup("main".equals(v.getStorageGroup()) ? "main" : meta.getApiName() + "_" + v.getStorageGroup())
                        .propertyCategoryId(v.getPropertyCategoryId() != null ? oldToNewCategoryIdMap.get(v.getPropertyCategoryId()) : null)
                        .metadata(v.getMetadata())
                        .build())
                .collect(Collectors.toList());
        ontologyPropertyService.saveBatch(childProps);
        // 属性自动关联数据源
        ontologyPropertyService.autoBindDatasource(childIdentifier);
        // 创建关系
        Map<String, String> parentChildLinkMap = Maps.newHashMap();
        var parentLinks = linkService.list(new LambdaUpdateWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, parentIdentifier)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, parentIdentifier));
        List<OntologyLinkGroup> childLinks = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(parentLinks)) {
            childLinks = parentLinks.stream().map(v -> {
                var childLinkId = IdGenerator.generateUUID();
                parentChildLinkMap.put(v.getUniqueIdentifier(), childLinkId);
                var link = OntologyLinkGroup.builder()
                        .name(v.getName())
                        .ontologyUniqueIdentifierFrom(v.getOntologyUniqueIdentifierFrom().equals(parentIdentifier) ? childIdentifier : v.getOntologyUniqueIdentifierFrom())
                        .ontologyUniqueIdentifierTo(v.getOntologyUniqueIdentifierTo().equals(parentIdentifier) ? childIdentifier : v.getOntologyUniqueIdentifierTo())
                        .status(v.getStatus())
                        .uniqueIdentifier(childLinkId)
                        .ontologySpaceId(v.getOntologySpaceId())
                        .build();
                return link;
            }).collect(Collectors.toList());
            linkService.saveBatch(childLinks);
        }
        // 创建行为（不创建行为调度）
        List<OntologyAction> actions = Lists.newArrayList();
        List<OntologyActionMappingIn> mappingIns = Lists.newArrayList();
        List<OntologyActionLink> actionLinks = Lists.newArrayList();
        var actionEntity = actionService.list(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, parentIdentifier));

        actionEntity.forEach(action -> {
            var actionId = SnowflakeIdUtil.get();
            actions.add(OntologyAction.builder().api(ontologyCreateParam.getApiName() + "_" + action.getApi())
                    .description(action.getDescription())
                    .displayName(action.getDisplayName())
                    .functionApi(action.getFunctionApi())
                    .status(action.getStatus())
                    .id(actionId)
                    .ontologyUniqueIdentifier(childIdentifier)
                    .icon(action.getIcon())
                    .build());

            var actionLink = actionLinkService.getOne(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
            if (actionLink != null) {
                actionLinks.add(OntologyActionLink.builder()
                        .ontologyActionId(actionLink.getOntologyActionId())
                        .ontologyLinkUniqueIdentifier(parentChildLinkMap.get(actionLink.getOntologyLinkUniqueIdentifier()))
                        .ontologyLinkParamExpression(actionLink.getOntologyLinkParamExpression())
                        .build());
            }

            var mapping = actionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getOntologyActionId, action.getId()));
            mappingIns.addAll(mapping.stream().map(v -> {
                return OntologyActionMappingIn.builder()
                        .propertyUniqueIdentifier(findChildOntologyProperty(parentProperties, childProps, v.getPropertyUniqueIdentifier()))
                        .ontologyActionId(actionId)
                        .functionParamId(v.getFunctionParamId())
                        .functionParamExpression(v.getFunctionParamExpression())
                        .build();
            }).collect(Collectors.toList()));
        });
        actionService.saveBatch(actions);
        actionMappingInService.saveBatch(mappingIns);
        actionLinkService.saveBatch(actionLinks);

    }


    private String findChildOntologyProperty(List<OntologyProperty> parentProperties, List<OntologyProperty> childProps, String targetUniqId) {
        var property = parentProperties.stream()
                .filter(p -> p.getUniqueIdentifier().equals(targetUniqId))
                .findFirst();

        if (property.isPresent()) {
            return childProps.stream().filter(p -> p.getApiName().equals(property.get().getApiName()))
                    .findFirst().get().getUniqueIdentifier();
        } else {
            return targetUniqId;
        }
    }

    private CategoryNode buildCategoryTree(PropertyCategory category, Map<Integer, List<PropertyCategory>> categoryByParentId) {
        var children = categoryByParentId.get(category.getId());
        List<CategoryNode> childNodes = null;
        if (CollectionUtils.isNotEmpty(children)) {
            childNodes = children.stream()
                    .map(child -> buildCategoryTree(child, categoryByParentId))
                    .collect(Collectors.toList());
        }
        return CategoryNode.builder()
                .name(category.getName())
                .children(childNodes)
                .build();
    }

    private PropertyMetadataSchemaCreateParam.MetadataSchemaNode buildMetadataSchemaTree(PropertyMetadataSchema schema, Map<Integer, List<PropertyMetadataSchema>> schemaByParentId) {
        var children = schemaByParentId.get(schema.getId());
        List<PropertyMetadataSchemaCreateParam.MetadataSchemaNode> childNodes = null;
        if (CollectionUtils.isNotEmpty(children)) {
            childNodes = children.stream()
                    .map(child -> buildMetadataSchemaTree(child, schemaByParentId))
                    .collect(Collectors.toList());
        }
        return PropertyMetadataSchemaCreateParam.MetadataSchemaNode.builder()
                .name(schema.getName())
                .children(childNodes)
                .enumValues(StringUtils.isNotEmpty(schema.getEnumValues()) ?
                        Lists.newArrayList(schema.getEnumValues().split(",")) : null)
                .build();
    }


    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteOntology(String ontologyIdentifier) {
        var meta = getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyIdentifier));
        PreconditionUtils.checkArgument(meta != null, "本体不存在:" + ontologyIdentifier, ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
        //删除本体元数据
        this.remove(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyIdentifier));
        //删除属性分类
        propertyCategoryService.remove(new LambdaQueryWrapper<PropertyCategory>().eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyIdentifier));
        //删除属性元数据
        propertyMetadataSchemaService.remove(new LambdaQueryWrapper<PropertyMetadataSchema>().eq(PropertyMetadataSchema::getOntologyUniqueIdentifier, ontologyIdentifier));
        //删除属性
        ontologyPropertyService.remove(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        //删除关系
        linkService.remove(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyIdentifier).or()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyIdentifier));
        //删除行为，参数，规则，任务
        actionService.removeByOntologyIdentifier(ontologyIdentifier);
        //删除所有实体节点和边, 保留实体表
        entityService.deleteNodesAndRelationsByOntologyId(meta.getApiName());
        //删除百科信息
        lemmaService.remove(new LambdaQueryWrapper<OntologyLemma>().eq(OntologyLemma::getOntologyUniqueIdentifier, ontologyIdentifier));
    }


    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateMeta(OntologyUpdateParam updateParam) {
        var updateWrapper = new LambdaUpdateWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, updateParam.getOntologyIdentifier())
                .set(OntologyMeta::getMetaGroupId, String.join(",", updateParam.getGroupIds()))
                .set(OntologyMeta::getIcon, updateParam.getIcon())
                .set(OntologyMeta::getDescription, updateParam.getDescription())
                .set(OntologyMeta::getDisplayName, updateParam.getDisplayName())
                .set(OntologyMeta::getLatestQueryTime, new Date())
                .set(OntologyMeta::getOntologyCategoryId, updateParam.getCategoryId());
        update(null, updateWrapper);
    }


    @Override
    public OntologyMetaInfoVO getMetaByUniqueIdentifier(String uniqueIdentifier) {
        OntologyMeta ontologyMeta = getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, uniqueIdentifier).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        updateById(ontologyMeta.setLatestQueryTime(new Date()));
        var metaInfoVO = DataConverter.convert(ontologyMeta);
        if (StringUtils.isNotEmpty(metaInfoVO.getParentOntologyUniqueIdentifier())) {
            var parentMeta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, metaInfoVO.getParentOntologyUniqueIdentifier()));
            metaInfoVO.setParentOntologyDisplayName(parentMeta != null ? parentMeta.getDisplayName() : null);
        }
        buildMetaInfoStatistic(metaInfoVO);
        return metaInfoVO;
    }

    @Override
    public OntologyMetaStatisticVO getStatistic(String uniqueIdentifier) {
        var meta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, uniqueIdentifier)
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkNotNull(meta, "本体不存在:" + uniqueIdentifier, HttpStatus.BAD_REQUEST);
        return buildStatistic(uniqueIdentifier);
    }

    /**
     * 统计本体对象关联的核心资源数量：实例、属性、关系、行为
     */
    private OntologyMetaStatisticVO buildStatistic(String ontologyIdentifier) {
        var propCnt = ontologyPropertyService.count(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var actionCnt = actionService.count(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, ontologyIdentifier));
        var linkCnt = linkService.count(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyIdentifier)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyIdentifier));

        //实例数量取自本体主键属性绑定的数据源表
        var pk = ontologyPropertyService.getOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getIsPrimaryKey, 1)
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));

        var entityCnt = 0;
        if (pk != null
                && StringUtils.isNotEmpty(pk.getDatasourceId())
                && StringUtils.isNotEmpty(pk.getDatasourceSchema())) {

            entityCnt = entityService.countEntity(pk.getDatasourceSchema(), pk.getDatasourceId());
        }

        return OntologyMetaStatisticVO.builder()
                .uniqueIdentifier(ontologyIdentifier)
                .entityCount(Math.toIntExact(entityCnt))
                .propertyCount(Math.toIntExact(propCnt))
                .relationCount(Math.toIntExact(linkCnt))
                .actionCount(Math.toIntExact(actionCnt))
                .build();
    }

    public void buildMetaInfoStatistic(OntologyMetaInfoVO metaInfoVO) {
        var statistic = buildStatistic(metaInfoVO.getUniqueIdentifier());
        metaInfoVO.setEntityCount(statistic.getEntityCount())
                .setPropertyCount(statistic.getPropertyCount())
                .setRelationCount(statistic.getRelationCount())
                .setActionCount(statistic.getActionCount());
    }


    @Override
    public List<OntologyMetaInfoVO> searchByKeyword(String keyword) {
        var searchKeyword = StringUtils.isEmpty(keyword) ? "" : keyword;
        var metaList = list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .and(wrapper -> wrapper
                        .or().like(OntologyMeta::getDescription, searchKeyword)
                        .or().like(OntologyMeta::getApiName, searchKeyword)
                        .or().like(OntologyMeta::getDisplayName, searchKeyword)
                ))
                .stream().map(DataConverter::convert).collect(Collectors.toList());

        if (metaList.isEmpty()) {
            return Lists.newArrayList();
        }

        var parentOntologyIds = metaList.stream().filter(v -> StringUtils.isNotEmpty(v.getParentOntologyUniqueIdentifier()))
                .map(OntologyMetaInfoVO::getParentOntologyUniqueIdentifier)
                .collect(Collectors.toList());

        if (parentOntologyIds.isEmpty()) {
            return metaList;
        }

        var parentMetaMap = list(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, parentOntologyIds))
                .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v.getDisplayName()));

        metaList.forEach(ontologyMeta ->
                ontologyMeta.setParentOntologyDisplayName(parentMetaMap.get(ontologyMeta.getParentOntologyUniqueIdentifier())));

        return metaList;
    }

    @Override
    public List<OntologyMetaInfoVO> getByCategoryId(Integer categoryId) {
        log.info("[getByCategoryId] received categoryId={}", categoryId);
        var queryWrapper = new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue());
        if (categoryId != null) {
            queryWrapper.eq(OntologyMeta::getOntologyCategoryId, categoryId);
        }
        var metaList = list(queryWrapper);
        log.info("[getByCategoryId] categoryId={}, matched size={}", categoryId, metaList.size());
        return metaList.stream()
                .map(DataConverter::convert)
                .collect(Collectors.toList());
    }

    @Override
    public List<OntologyMetaInfoVO> listBySpaceId(Integer spaceId) {
        if (spaceId == null) {
            return Lists.newArrayList();
        }
        var metaList = list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .eq(OntologyMeta::getOntologySpaceId, spaceId));
        return metaList.stream()
                .map(DataConverter::convert)
                .collect(Collectors.toList());
    }

    @Override
    public List<OntologyMetaNodeVO> getOntologyTreeByByGroupId(String groupId) {
        LambdaQueryWrapper<OntologyMeta> queryWrapper;
        if (StringUtils.isEmpty(groupId)) {
            queryWrapper = new LambdaQueryWrapper<>();
        } else {
            queryWrapper = new LambdaQueryWrapper<OntologyMeta>().like(OntologyMeta::getMetaGroupId, groupId).eq(OntologyMeta::getStatus, Status.ENABLE.getValue());
        }
        var metaMap = list(queryWrapper)
                .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> OntologyMetaNodeVO.builder()
                        .parentUniqueIdentifier(v.getParentUniqueIdentifier())
                        .uniqueIdentifier(v.getUniqueIdentifier())
                        .displayName(v.getDisplayName())
                        .childNodes(new ArrayList<>())
                        .build()));
        return metaMap.values().stream().filter(v -> StringUtils.isEmpty(v.getParentUniqueIdentifier())).map(parent ->
                {
                    buildTree(parent, metaMap);
                    return parent;
                }
        ).collect(Collectors.toList());
    }

    @SneakyThrows
    @Override
    public List<String> importOntologies(MultipartFile file) {
        InputStream inputStream = file.getInputStream();
        List<OntologyCreateDTO> ontologyList = objectMapper.readValue(inputStream, new TypeReference<List<OntologyCreateDTO>>() {
        });
        List<String> failedOntology = Lists.newArrayList();
        ontologyList.forEach(dto -> {
            try {
                proxyService.importOntology(dto);
            } catch (Exception e) {
                log.error("本体 {} 导入失败", dto.getMetadata().getDisplayName(), e);
                failedOntology.add(dto.getMetadata().getDisplayName());
            }
        });
        return failedOntology;
    }


    @Transactional(value = "mainTransactionManager")
    public void importOntology(OntologyCreateDTO dto) {

        var metaData = dto.getMetadata();
        PreconditionUtils.checkNotNull(metaData, "ontology meta data is null");
        //保存基本信息
        var space = spaceService.getOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getDisplayName, metaData.getOntologySpaceName()));
        PreconditionUtils.checkNotNull(space, "invalid ontology space name:" + dto.getMetadata().getOntologySpaceName());

        //空间分组
        var groups = groupService.list(new LambdaQueryWrapper<OntologyGroup>()
                        .eq(OntologyGroup::getOntologySpaceId, space.getId()))
                .stream().filter(g -> metaData.getGroupNames().contains(g.getGroupName())).collect(Collectors.toList());

        var metaGroups = "";
        if (CollectionUtils.isNotEmpty(groups)) {
            metaGroups = groups.stream().map(OntologyGroup::getGroupId).collect(Collectors.joining(","));
        }

        //获取本体分类体系
        var ontologyCategoryMap = categoryService.list(new LambdaQueryWrapper<OntologyCategory>()
                        .eq(OntologyCategory::getOntologySpaceId, space.getId()))
                .stream().collect(Collectors.toMap(OntologyCategory::getPath, OntologyCategory::getId));

        var meta = OntologyMeta.builder()
                .apiName(metaData.getApiName())
                .description(metaData.getDescription())
                .status(Status.ENABLE.getValue())
                .displayName(metaData.getDisplayName())
                .metaGroupId(metaGroups)
                .uniqueIdentifier(IdGenerator.generateUUID())
                .ontologySpaceId(space.getId())
                .ontologyCategoryId(StringUtils.isEmpty(metaData.getCategoryPath()) ?
                        null : ontologyCategoryMap.get(metaData.getCategoryPath()))
                .build();
        save(meta);
        //保存属性分类
        var propertyCategoryCreateParam = dto.getPropertyCategory();
        if (propertyCategoryCreateParam != null) {
            propertyCategoryCreateParam.setParentId(0)
                    .setOntologyIdentifier(meta.getUniqueIdentifier());
            ontologyPropertyService.createCategory(propertyCategoryCreateParam);
        }
        //保存属性元数据
        var propertySchemaCreateParam = dto.getPropertySchema();
        if (propertySchemaCreateParam != null) {
            propertySchemaCreateParam.setParentId(0)
                    .setOntologyIdentifier(meta.getUniqueIdentifier());
            ontologyPropertyService.createMetadataSchema(propertySchemaCreateParam);
        }
        //保存属性
        var props = dto.getProperties();
        if (CollectionUtils.isNotEmpty(props)) {
            var categoryMap = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>()
                            .eq(PropertyCategory::getOntologyUniqueIdentifier, meta.getUniqueIdentifier()))
                    .stream().collect(Collectors.toMap(PropertyCategory::getPath, PropertyCategory::getId));
            var ontologyPropertyCreateParams = props.stream().<OntologyPropertyCreateParam>map(p -> OntologyPropertyCreateParam.builder()
                            .apiName(p.getApiName())
                            .description(p.getDescription())
                            .displayName(p.getDisplayName())
                            .dataType(p.getDataType())
                            .isPrimaryKey(p.getIsPrimaryKey())
                            .isTitleKey(p.getIsTitleKey())
                            .ontologyIdentifier(meta.getUniqueIdentifier())
                            .defaultValue(p.getDefaultValue())
                            .storageGroup(p.getStorageGroup())
                            .categoryId(StringUtils.isEmpty(p.getCategoryPath()) ? null : categoryMap.get(p.getCategoryPath()))
                            .metadata(p.getMetadata())
                            .build())
                    .collect(Collectors.toList());
            ontologyPropertyService.batchCreateProperties(ontologyPropertyCreateParams);
        }
        //自动建实体表以及关联属性数据源：仅当导入了实例数据时才执行，无实例数据则跳过（避免无谓建表/绑定数据源及主键校验失败）
        var instances = dto.getInstances();
        if (instances != null && CollectionUtils.isNotEmpty(instances.getNodes())) {
            ontologyPropertyService.autoBindDatasource(meta.getUniqueIdentifier());
        }
        //保存关系
        var relations = dto.getRelations();
        Map<Integer, String> relationMap = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(relations)) {
            List<OntologyLinkGroup> links = Lists.newArrayList();
            for (int i = 0; i < relations.size(); i++) {
                var r = relations.get(i);
                var fromName = r.getOntologyUniqueIdentifierFrom();
                var toName = r.getOntologyUniqueIdentifierTo();
                var fromMeta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, space.getId())
                        .eq(OntologyMeta::getDisplayName, fromName));
                var toMeta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, space.getId())
                        .eq(OntologyMeta::getDisplayName, toName));

                var linkId = IdGenerator.generateUUID();
                relationMap.put(i, linkId);
                links.add(OntologyLinkGroup.builder()
                        .uniqueIdentifier(linkId)
                        .name(r.getName())
                        .type(r.getType())
                        .ontologyUniqueIdentifierFrom(fromMeta.getUniqueIdentifier())
                        .ontologyUniqueIdentifierTo(toMeta.getUniqueIdentifier())
                        .ontologySpaceId(space.getId())
                        .build());
            }
            linkService.saveBatch(links);
        }
        //保存函数
        var funcs = dto.getFunctions();
        if (CollectionUtils.isNotEmpty(funcs)) {
            funcs.stream().forEach(f -> {
                var input = f.getInputParams();
                String inputParamString = "";
                if (CollectionUtils.isNotEmpty(input)) {
                    List<String> paramList = Lists.newArrayList();
                    String paramFormat = "@FuncParam(name = \"%s\") %s %s";
                    input.forEach(p -> paramList.add(String.format(paramFormat, p.getParamName(),
                            FunctionParamTypeEnum.valueOf(p.getParamType().toUpperCase()).getVale(),
                            p.getParamName())));
                    inputParamString = String.join(",\n", paramList);
                }
                String codeTemplate = "import com.aircas.ptr.foundry.ontology.aspect.FuncParam\n" +
                        "import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO\n" +
                        "import groovy.util.logging.Slf4j\n" +
                        "\n" +
                        "@Slf4j\n" +
                        "class %s {\n\n" +
                        "    FunctionResultVO handle(%s) {\n" +
                        "        return FunctionResultVO.builder()\n" +
                        "                .build()\n" +
                        "    }\n" +
                        "}";
                String code = String.format(codeTemplate, f.getFunctionApi(), inputParamString);

                functionService.createFunction(FunctionCreateParam.builder()
                        .code(code)
                        .description(f.getDescription())
                        .functionApi(f.getFunctionApi())
                        .displayName(f.getDisplayName())
                        .model(f.getModel())
                        .type(f.getType())
                        .build());
            });
        }
        //保存行为
        var actions = dto.getActions();
        if (CollectionUtils.isNotEmpty(actions)) {
            actions.forEach(action -> {
                val relationIndex = action.getRelationIndex();
                ActionLinkMappingParam linkMapping = null;
                if (relationIndex != null) {
                    linkMapping = ActionLinkMappingParam.builder()
                            .ontologyLinkUniqIdentifier(relationMap.get(relationIndex))
                            .build();
                }
                List<ActionParamMappingCreateParam> mappingIns = null;
                var mapping = action.getMappingIns();
                if (CollectionUtils.isNotEmpty(mapping)) {
                    var detail = functionService.getFunctionDetailByApi(action.getFunctionApi());
                    var paramMap = detail.getParams().stream().collect(Collectors.toMap(v -> v.getParamName(), v -> v));

                    mappingIns = mapping.stream().map(m -> {
                        var ontologyMeta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                                .eq(OntologyMeta::getOntologySpaceId, space.getId())
                                .eq(OntologyMeta::getDisplayName, m.getOntologyName()));
                        var property = ontologyPropertyService.getOne(new LambdaQueryWrapper<OntologyProperty>()
                                .eq(OntologyProperty::getDisplayName, m.getPropertyName())
                                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyMeta.getUniqueIdentifier()));

                        return ActionParamMappingCreateParam.builder()
                                .ontologyUniqueIdentifier(ontologyMeta.getUniqueIdentifier())
                                .functionParamId(paramMap.get(m.getFunctionParamName()).getParamId())
                                .propertyUniqueIdentifier(property.getUniqueIdentifier())
                                .build();

                    }).collect(Collectors.toList());

                }

                actionService.createAction(ActionCreateOrUpdateParam.builder()
                        .ontologyIdentifier(meta.getUniqueIdentifier())
                        .actionApi(action.getActionApi())
                        .description(action.getDescription())
                        .displayName(action.getDisplayName())
                        .functionApi(action.getFunctionApi())
                        .linkMapping(linkMapping)
                        .mappingIns(mappingIns)
                        .build());

            });
        }
        //导入实例数据（若传递了 instances）：写数据湖物理表，id 由数据库重新生成；本体未绑定数据源或无实例时静默跳过
        entityService.importInstances(meta.getUniqueIdentifier(), instances);
    }


    @Override
    public List<OntologyGroupMetaVO> getByGroupId(String groupId, OntologyOrderByEnum orderBy, QuerySortEnum sort) {
        List<OntologyGroup> groups = Lists.newArrayList();
        if (StringUtils.isEmpty(groupId)) {
            groups.addAll(groupService.list());
        } else {
            groups.add(groupService.getOne(new LambdaQueryWrapper<OntologyGroup>().eq(OntologyGroup::getGroupId, groupId)));
        }

        var queryWrapper = new QueryWrapper<OntologyMeta>().eq("status", Status.ENABLE.getValue());
        if (orderBy != null && sort != null) {
            queryWrapper.orderBy(orderBy != null, sort.equals(QuerySortEnum.ASC), orderBy.getValue()).last("NULLS LAST");
        } else {
            queryWrapper.orderByDesc("id");
        }


        var metaList = list(queryWrapper).stream().map(meta -> DataConverter.convert(meta)).collect(Collectors.toList());
        return groups.stream().map(group -> {
            var metaInfoVOList = metaList.stream().filter(meta -> meta.getMetaGroupId().contains(group.getGroupId())).collect(Collectors.toList());
            return OntologyGroupMetaVO.builder()
                    .groupId(group.getGroupId())
                    .groupName(group.getGroupName())
                    .metaVOS(metaInfoVOList)
                    .build();
        }).collect(Collectors.toList());
    }

    private void buildTree(OntologyMetaNodeVO parent, Map<String, OntologyMetaNodeVO> metaMap) {
        metaMap.values().forEach(child -> {
            if (StringUtils.equals(child.getParentUniqueIdentifier(), parent.getUniqueIdentifier())) {
                parent.getChildNodes().add(child);
                buildTree(child, metaMap);
            }
        });
    }

    @Override
    public List<OntologyCreateDTO> exportOntology(String uniqueIdentifier, OntologyExportTypeEnum exportType) {
        var meta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, uniqueIdentifier)
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkNotNull(meta, "本体不存在或已停用:" + uniqueIdentifier);
        var space = getSpaceOrThrow(meta.getOntologySpaceId());
        //关系端点可能指向同空间其它本体，displayName 解析需覆盖空间内全部启用本体
        var scopeMetas = listEnabledMetas(space);
        return buildOntologies(space, Lists.newArrayList(meta), scopeMetas, exportType);
    }

    @Override
    public List<OntologyCreateDTO> exportOntologies(Integer spaceId, OntologyExportTypeEnum exportType) {
        return exportOntologies(getSpaceOrThrow(spaceId), exportType);
    }

    /**
     * 空间已加载时的重载：供空间导出复用已查出的 space，避免重复 getById。
     */
    public List<OntologyCreateDTO> exportOntologies(OntologySpace space, OntologyExportTypeEnum exportType) {
        var metas = listEnabledMetas(space);
        if (CollectionUtils.isEmpty(metas)) {
            return Lists.newArrayList();
        }
        return buildOntologies(space, metas, metas, exportType);
    }

    private OntologySpace getSpaceOrThrow(Integer spaceId) {
        var space = spaceService.getById(spaceId);
        PreconditionUtils.checkNotNull(space, "本体空间不存在:" + spaceId);
        return space;
    }

    private List<OntologyMeta> listEnabledMetas(OntologySpace space) {
        return list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, space.getId())
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
    }

    private List<OntologyCreateDTO> buildOntologies(OntologySpace space, List<OntologyMeta> metasToExport,
                                                    List<OntologyMeta> displayNameScope,
                                                    OntologyExportTypeEnum exportType) {
        //uid -> displayName（解析范围取 displayNameScope：单本体导出时关系端点可能指向同空间其它本体，需覆盖全空间启用本体）
        var uidToDisplayName = displayNameScope.stream()
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, OntologyMeta::getDisplayName, (a, b) -> a));
        //本体分类 id -> path
        var ontologyCategoryPathMap = categoryService.list(new LambdaQueryWrapper<OntologyCategory>()
                        .eq(OntologyCategory::getOntologySpaceId, space.getId()))
                .stream().collect(Collectors.toMap(OntologyCategory::getId, OntologyCategory::getPath, (a, b) -> a));
        //分组 groupId -> groupName
        var groupNameMap = groupService.list(new LambdaQueryWrapper<OntologyGroup>()
                        .eq(OntologyGroup::getOntologySpaceId, space.getId()))
                .stream().collect(Collectors.toMap(OntologyGroup::getGroupId, OntologyGroup::getGroupName, (a, b) -> a));

        return metasToExport.stream()
                .map(meta -> buildExportDTO(meta, space, ontologyCategoryPathMap, groupNameMap, uidToDisplayName, exportType))
                .collect(Collectors.toList());
    }

    private OntologyCreateDTO buildExportDTO(OntologyMeta meta, OntologySpace space,
                                             Map<Integer, String> ontologyCategoryPathMap,
                                             Map<String, String> groupNameMap,
                                             Map<String, String> uidToDisplayName,
                                             OntologyExportTypeEnum exportType) {
        var uid = meta.getUniqueIdentifier();

        //metadata
        Set<String> groupNames = new LinkedHashSet<>();
        if (StringUtils.isNotEmpty(meta.getMetaGroupId())) {
            for (String gid : meta.getMetaGroupId().split(",")) {
                if (StringUtils.isEmpty(gid)) {
                    continue;
                }
                var gname = groupNameMap.get(gid.trim());
                if (StringUtils.isNotEmpty(gname)) {
                    groupNames.add(gname);
                }
            }
        }
        var metadata = OntologyMetaDataDTO.builder()
                .apiName(meta.getApiName())
                .displayName(meta.getDisplayName())
                .description(meta.getDescription())
                .ontologySpaceName(space.getDisplayName())
                .groupNames(groupNames)
                .categoryPath(meta.getOntologyCategoryId() == null ? null : ontologyCategoryPathMap.get(meta.getOntologyCategoryId()))
                .build();

        //属性分类树
        var propertyCategories = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, uid));
        var propCategoryPathMap = propertyCategories.stream()
                .collect(Collectors.toMap(PropertyCategory::getId, PropertyCategory::getPath, (a, b) -> a));
        var propertyCategory = buildPropertyCategoryTree(propertyCategories);

        //属性
        var properties = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, uid)
                        .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()))
                .stream().map(p -> OntologyPropertyDTO.builder()
                        .apiName(p.getApiName())
                        .dataType(p.getPropertyType())
                        .displayName(p.getDisplayName())
                        .description(p.getDescription())
                        .isPrimaryKey(p.getIsPrimaryKey() != null && p.getIsPrimaryKey() == 1)
                        .isTitleKey(p.getIsTitleKey() != null && p.getIsTitleKey() == 1)
                        .defaultValue(p.getDefaultValue())
                        .storageGroup(p.getStorageGroup())
                        .categoryPath(p.getPropertyCategoryId() == null ? null : propCategoryPathMap.get(p.getPropertyCategoryId()))
                        .build())
                .collect(Collectors.toList());

        //实例（仅 INSTANCE 模式导出；SCHEMA 模式不含实例数据）
        var instances = exportType == OntologyExportTypeEnum.INSTANCE
                ? entityService.exportInstances(uid)
                : null;

        return OntologyCreateDTO.builder()
                .metadata(metadata)
                .propertyCategory(propertyCategory)
                .properties(properties)
                .instances(instances)
                .build();
    }

    private PropertyCategoryCreateParam buildPropertyCategoryTree(List<PropertyCategory> categories) {
        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }
        var root = categories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() == 0)
                .findFirst().orElse(null);
        if (root == null) {
            return null;
        }
        return PropertyCategoryCreateParam.builder()
                .parentId(root.getParentId())
                .name(root.getName())
                .children(buildPropertyCategoryChildren(root.getId(), categories))
                .build();
    }

    private List<CategoryNode> buildPropertyCategoryChildren(Integer parentId, List<PropertyCategory> categories) {
        return categories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId().equals(parentId))
                .map(c -> CategoryNode.builder()
                        .name(c.getName())
                        .children(buildPropertyCategoryChildren(c.getId(), categories))
                        .build())
                .collect(Collectors.toList());
    }

    private PropertyMetadataSchemaCreateParam buildMetadataSchemaTree(List<PropertyMetadataSchema> schemas) {
        if (CollectionUtils.isEmpty(schemas)) {
            return null;
        }
        var root = schemas.stream()
                .filter(s -> s.getParentId() != null && s.getParentId() == 0)
                .findFirst().orElse(null);
        if (root == null) {
            return null;
        }
        return PropertyMetadataSchemaCreateParam.builder()
                .parentId(root.getParentId())
                .name(root.getName())
                .enumValues(splitEnumValues(root.getEnumValues()))
                .children(buildMetadataSchemaChildren(root.getId(), schemas))
                .build();
    }

    private List<PropertyMetadataSchemaCreateParam.MetadataSchemaNode> buildMetadataSchemaChildren(Integer parentId, List<PropertyMetadataSchema> schemas) {
        return schemas.stream()
                .filter(s -> s.getParentId() != null && s.getParentId().equals(parentId))
                .map(s -> PropertyMetadataSchemaCreateParam.MetadataSchemaNode.builder()
                        .name(s.getName())
                        .enumValues(splitEnumValues(s.getEnumValues()))
                        .children(buildMetadataSchemaChildren(s.getId(), schemas))
                        .build())
                .collect(Collectors.toList());
    }

    private List<String> splitEnumValues(String enumValues) {
        if (StringUtils.isEmpty(enumValues)) {
            return null;
        }
        return Arrays.stream(enumValues.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList());
    }


}