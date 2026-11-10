package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyCreateDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyMetaDataDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyRelationDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyImportResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaBriefVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaStatisticVO;
import com.aircas.ptr.foundry.ontology.model.view.OntologyStatisticsCountView;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
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
    private OntologyLinkCategoryService linkCategoryService;

    @Resource
    private PropertyMetadataSchemaService propertyMetadataSchemaService;

    @Resource
    private OntologyPropertyMapper propertyMapper;

    @Resource
    private OntologyActionMapper actionMapper;

    @Resource
    private OntologyLinkGroupMapper linkGroupMapper;

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
        PreconditionUtils.checkIsNull(duplicateDisplayName, "对象显示名称 '" + ontologyCreateParam.getDisplayName() + "' 已存在", HttpStatus.BAD_REQUEST);
        var duplicateApiName = getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, ontologyCreateParam.getSpaceId())
                .eq(OntologyMeta::getApiName, ontologyCreateParam.getApiName())
        );
        PreconditionUtils.checkIsNull(duplicateApiName, "对象api名称 '" + ontologyCreateParam.getApiName() + "' 已存在", HttpStatus.BAD_REQUEST);
        var groupIds = ontologyCreateParam.getGroupIds();
        if (CollectionUtils.isNotEmpty(groupIds)) {
            List<OntologyGroup> list = groupService.list(new LambdaQueryWrapper<OntologyGroup>().in(OntologyGroup::getGroupId, groupIds));
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(list) && list.size() == groupIds.size(), "无效的对象分组ID", HttpStatus.BAD_REQUEST);
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
                    .name("全部")
                    .ontologyIdentifier(meta.getUniqueIdentifier())
                    .build();
            ontologyPropertyService.createCategory(propertyCategoryParam);
        }//继承创建
        else {
            createOntologyByInherit(ontologyCreateParam, meta);
        }
        return meta.getUniqueIdentifier();
    }


    @Override
    @Transactional(value = "chainedTransactionManager", rollbackFor = Exception.class)
    public String createOntologyWithProperties(OntologyAssetBuildParam param) {
        // 对象分类兜底：不传则挂到本空间对象分类根节点
        if (param.getCategoryId() == null) {
            param.setCategoryId(getOntologyCategoryRootId(param.getSpaceId()));
        }
        var uniqueIdentifier = createOntology(param);

        // 确保 properties 不为 null
        var properties = param.getProperties();
        if (properties == null) {
            properties = new ArrayList<>();
            param.setProperties(properties);
        }

        // 无主键时自动生成 id 主键属性
        boolean hasPrimaryKey = properties.stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getIsPrimaryKey()));
        if (!hasPrimaryKey) {
            var idProperty = OntologyPropertyCreateParam.builder()
                    .apiName("id")
                    .displayName("ID")
                    .dataType(OntologyDataTypeEnum.Int)
                    .isPrimaryKey(true)
                    .isTitleKey(false)
                    .storageGroup("main")
                    .description("自动生成的主键")
                    .build();
            properties.add(0, idProperty);
        }

        // 从属性 metadata 推导并创建元数据 schema
        createMetadataSchemaFromProperties(uniqueIdentifier, properties);

        // 属性分类兜底 + 逐个创建属性
        var defaultPropertyCategoryId = getPropertyCategoryRootId(uniqueIdentifier);
        for (var propertyParam : properties) {
            propertyParam.setOntologyIdentifier(uniqueIdentifier);
            if (propertyParam.getCategoryId() == null) {
                propertyParam.setCategoryId(defaultPropertyCategoryId);
            }
            ontologyPropertyService.createProperty(propertyParam);
        }
        return uniqueIdentifier;
    }

    /**
     * 从所有属性的 metadata 推导元数据 schema 并落库。
     *
     * <p>算法：递归遍历每个属性的 metadata JsonNode，收集所有路径（如 "密级/来源"），
     * 然后按 BFS 顺序批量插入 property_metadata_schema 表（顶层 parentId=0，子层 parentId=父行 id）。
     * 枚举值不推断（schema 叶子 enumValues 为 null），metadata 校验只检查键是否存在、不检查值内容。</p>
     *
     * <p>冲突检测：若同一字段在不同属性的 metadata 里既是叶子又是分支（如一个属性是 {"密级":"中"}，
     * 另一个是 {"密级":{"来源":"雷达"}}），报错回滚。</p>
     */
    private void createMetadataSchemaFromProperties(String ontologyUniqueIdentifier,
                                                     List<OntologyPropertyCreateParam> properties) {
        Map<String, Set<String>> childrenByParent = new HashMap<>();
        Set<String> leafPaths = new HashSet<>();
        Set<String> branchPaths = new HashSet<>();

        for (var propertyParam : properties) {
            if (propertyParam.getMetadata() == null) continue;
            collectPaths(propertyParam.getMetadata(), "", childrenByParent, leafPaths, branchPaths);
        }

        if (childrenByParent.isEmpty()) return;

        // 冲突检测：同一字段不能既是叶子又是分支
        List<String> conflicts = new ArrayList<>();
        for (String path : leafPaths) {
            if (branchPaths.contains(path)) {
                conflicts.add(path);
            }
        }
        PreconditionUtils.checkArgument(conflicts.isEmpty(),
                "属性 metadata 结构冲突，同一字段既是叶子又是分支：" + conflicts, HttpStatus.BAD_REQUEST);

        // BFS 插入 schema 行
        Map<String, Integer> idByPath = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        // 顶层（parentId=0）
        Set<String> topLevelNames = childrenByParent.getOrDefault("", Collections.emptySet());
        List<PropertyMetadataSchema> topLevelRows = new ArrayList<>();
        for (String name : topLevelNames) {
            var row = PropertyMetadataSchema.builder()
                    .ontologyUniqueIdentifier(ontologyUniqueIdentifier)
                    .name(name)
                    .parentId(0)
                    .path(name)
                    .build();
            topLevelRows.add(row);
            queue.add(name);
        }
        if (!topLevelRows.isEmpty()) {
            propertyMetadataSchemaService.saveBatch(topLevelRows);
            for (var row : topLevelRows) {
                idByPath.put(row.getPath(), row.getId());
            }
        }

        // 子层（parentId=父行 id）
        while (!queue.isEmpty()) {
            String parentPath = queue.poll();
            Integer parentId = idByPath.get(parentPath);
            Set<String> childNames = childrenByParent.getOrDefault(parentPath, Collections.emptySet());
            List<PropertyMetadataSchema> childRows = new ArrayList<>();
            for (String childName : childNames) {
                String childPath = parentPath + "/" + childName;
                var row = PropertyMetadataSchema.builder()
                        .ontologyUniqueIdentifier(ontologyUniqueIdentifier)
                        .name(childName)
                        .parentId(parentId)
                        .path(childPath)
                        .build();
                childRows.add(row);
                queue.add(childPath);
            }
            if (!childRows.isEmpty()) {
                propertyMetadataSchemaService.saveBatch(childRows);
                for (var row : childRows) {
                    idByPath.put(row.getPath(), row.getId());
                }
            }
        }
    }

    /**
     * 递归收集 metadata JsonNode 的所有路径。
     *
     * @param node              当前节点
     * @param parentPath        父路径（根为 ""）
     * @param childrenByParent  父路径 → 子字段名集合
     * @param leafPaths         叶子路径集合
     * @param branchPaths       分支路径集合（对象节点）
     */
    private void collectPaths(com.fasterxml.jackson.databind.JsonNode node, String parentPath,
                              Map<String, Set<String>> childrenByParent,
                              Set<String> leafPaths, Set<String> branchPaths) {
        if (node.isObject()) {
            branchPaths.add(parentPath);
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                String childPath = parentPath.isEmpty() ? fieldName : parentPath + "/" + fieldName;
                childrenByParent.computeIfAbsent(parentPath, k -> new HashSet<>()).add(fieldName);
                collectPaths(node.get(fieldName), childPath, childrenByParent, leafPaths, branchPaths);
            }
        } else {
            leafPaths.add(parentPath);
        }
    }

    /**
     * 查询空间的对象分类树根节点 id，口径同画布落库 getOntologyCategoryRoot。
     */
    private Integer getOntologyCategoryRootId(Integer spaceId) {
        var root = categoryService.getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .eq(OntologyCategory::getParentId, 0));
        PreconditionUtils.checkNotNull(root, "对象分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }

    /**
     * 查询对象的属性分类树根节点 id，口径同画布落库 getPropertyCategoryRoot。
     */
    private Integer getPropertyCategoryRootId(String ontologyUniqueIdentifier) {
        var root = propertyCategoryService.getOne(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(PropertyCategory::getParentId, 0));
        PreconditionUtils.checkNotNull(root, "属性分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }


    //继承创建：继承目标对象属性、关系、行为；不继承实体数据源、行为调度
    private void createOntologyByInherit(OntologyMetaCreateParam ontologyCreateParam, OntologyMeta meta) {
        var childIdentifier = meta.getUniqueIdentifier();
        var parentIdentifier = ontologyCreateParam.getParentOntologyUniqueIdentifier();
        //校验父对象
        var parentOntology = getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, parentIdentifier).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkArgument(parentOntology != null, "父对象不存在", ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
        //创建子对象元数据
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
        var childBindResult = ontologyPropertyService.autoBindDatasource(childIdentifier);
        if (childBindResult != null && CollectionUtils.isNotEmpty(childBindResult.getBlockedItems())) {
            log.warn("子对象{}属性自动关联数据源部分跳过：{}", childIdentifier, childBindResult.getMessage());
        }
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
                        // 父关系 status 为历史 NULL 时不能继续往下传，否则子对象的关系同样查不到
                        .status(v.getStatus() == null ? Status.ENABLE.getValue() : v.getStatus())
                        .uniqueIdentifier(childLinkId)
                        .ontologySpaceId(v.getOntologySpaceId())
                        .build();
                return link;
            }).collect(Collectors.toList());
            linkService.saveBatch(childLinks);
        }
        // 创建行为（不创建行为调度）
        List<OntologyActionMappingIn> mappingIns = Lists.newArrayList();
        List<OntologyActionLink> actionLinks = Lists.newArrayList();
        var actionEntity = actionService.list(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, parentIdentifier));

        actionEntity.forEach(action -> {
            var ontologyAction = OntologyAction.builder().api(ontologyCreateParam.getApiName() + "_" + action.getApi())
                    .description(action.getDescription())
                    .displayName(action.getDisplayName())
                    .functionApi(action.getFunctionApi())
                    .status(action.getStatus())
                    .ontologyUniqueIdentifier(childIdentifier)
                    .icon(action.getIcon())
                    .build();
            actionService.save(ontologyAction);
            var ontologyActionId = ontologyAction.getId();

            var actionLink = actionLinkService.getOne(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
            if (actionLink != null) {
                actionLinks.add(OntologyActionLink.builder()
                        .ontologyActionId(ontologyActionId)
                        .ontologyLinkUniqueIdentifier(parentChildLinkMap.get(actionLink.getOntologyLinkUniqueIdentifier()))
                        .ontologyLinkParamExpression(actionLink.getOntologyLinkParamExpression())
                        .targetStatus(actionLink.getTargetStatus())
                        .timeWindows(actionLink.getTimeWindows())
                        .build());
            }

            var mapping = actionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getOntologyActionId, action.getId()));
            mappingIns.addAll(mapping.stream().map(v -> {
                return OntologyActionMappingIn.builder()
                        .propertyUniqueIdentifier(findChildOntologyProperty(parentProperties, childProps, v.getPropertyUniqueIdentifier()))
                        .ontologyActionId(ontologyActionId)
                        .functionParamId(v.getFunctionParamId())
                        .functionParamExpression(v.getFunctionParamExpression())
                        .build();
            }).collect(Collectors.toList()));
        });
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
        PreconditionUtils.checkArgument(meta != null, "对象不存在:" + ontologyIdentifier, ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
        //删除对象元数据
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
    public void batchDeleteOntologies(OntologyBatchDeleteParam param) {
        var spaceId = param.getSpaceId();
        var identifiers = param.getOntologyIdentifiers();

        // 校验所有对象是否存在且属于该空间
        var metaList = list(new LambdaQueryWrapper<OntologyMeta>()
                .in(OntologyMeta::getUniqueIdentifier, identifiers)
                .eq(OntologyMeta::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(metaList.size() == identifiers.size(),
                "部分对象不存在或不属于该空间", ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);

        // 批量删除属性分类
        propertyCategoryService.remove(new LambdaQueryWrapper<PropertyCategory>()
                .in(PropertyCategory::getOntologyUniqueIdentifier, identifiers));

        // 批量删除属性元数据 schema
        propertyMetadataSchemaService.remove(new LambdaQueryWrapper<PropertyMetadataSchema>()
                .in(PropertyMetadataSchema::getOntologyUniqueIdentifier, identifiers));

        // 批量删除属性
        ontologyPropertyService.remove(new LambdaQueryWrapper<OntologyProperty>()
                .in(OntologyProperty::getOntologyUniqueIdentifier, identifiers));

        // 批量删除关系（作为源或目标）
        linkService.remove(new LambdaQueryWrapper<OntologyLinkGroup>()
                .in(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, identifiers).or()
                .in(OntologyLinkGroup::getOntologyUniqueIdentifierTo, identifiers));

        // 批量删除行为、参数、规则、任务
        for (var identifier : identifiers) {
            actionService.removeByOntologyIdentifier(identifier);
        }

        // 批量删除实体节点和边
        for (var meta : metaList) {
            entityService.deleteNodesAndRelationsByOntologyId(meta.getApiName());
        }

        // 批量删除百科信息
        lemmaService.remove(new LambdaQueryWrapper<OntologyLemma>()
                .in(OntologyLemma::getOntologyUniqueIdentifier, identifiers));

        // 批量删除对象元数据
        this.remove(new LambdaQueryWrapper<OntologyMeta>()
                .in(OntologyMeta::getUniqueIdentifier, identifiers));
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
    public OntologyMetaBriefVO getMetaById(Long id) {
        var meta = getById(id);
        PreconditionUtils.checkNotNull(meta, "对象不存在:" + id, HttpStatus.BAD_REQUEST);
        String spaceName = null;
        if (meta.getOntologySpaceId() != null) {
            var space = spaceService.getById(meta.getOntologySpaceId());
            spaceName = space != null ? space.getDisplayName() : null;
        }
        return OntologyMetaBriefVO.builder()
                .id(meta.getId())
                .displayName(meta.getDisplayName())
                .spaceName(spaceName)
                .uniqueIdentifier(meta.getUniqueIdentifier())
                .build();
    }

    @Override
    public OntologyMetaStatisticVO getStatistic(String uniqueIdentifier) {
        var meta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, uniqueIdentifier)
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkNotNull(meta, "对象不存在:" + uniqueIdentifier, HttpStatus.BAD_REQUEST);
        return buildStatistic(uniqueIdentifier, meta.getOntologySpaceId());
    }

    /**
     * 统计对象关联的核心资源数量：实例、属性、关系、行为
     *
     * @param ontologyIdentifier 对象唯一标识
     * @param spaceId            对象所属空间 id；关系计数按该空间裁剪，与详情页图谱、分类树口径一致
     */
    private OntologyMetaStatisticVO buildStatistic(String ontologyIdentifier, Integer spaceId) {
        var propCnt = ontologyPropertyService.count(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var actionCnt = actionService.count(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, ontologyIdentifier));
        // 关系口径：有效（status=1）+ 本空间 + 该对象在任意一端（源或目标）
        var linkQuery = new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue())
                .and(w -> w.eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyIdentifier)
                        .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyIdentifier));
        if (spaceId != null) {
            linkQuery.eq(OntologyLinkGroup::getOntologySpaceId, spaceId);
        }
        var linkCnt = linkService.count(linkQuery);

        //实例数量取自对象主键属性绑定的数据源表
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
        var statistic = buildStatistic(metaInfoVO.getUniqueIdentifier(), metaInfoVO.getSpaceId());
        metaInfoVO.setEntityCount(statistic.getEntityCount())
                .setPropertyCount(statistic.getPropertyCount())
                .setRelationCount(statistic.getRelationCount())
                .setActionCount(statistic.getActionCount());
    }

    /**
     * 批量统计多个对象关联的核心资源数量：属性、关系、行为。
     *
     * <p>分类树一类接口需要对整个空间的对象出数，逐对象走 {@link #buildStatistic} 会产生 4N 次往返，
     * 几百个对象时接口被拖到秒级。这里合并为属性计数、行为计数、关系分组计数共 3 次取数。</p>
     *
     * <p>不含实例数：树接口前端不展示实例数，而逐对象取实例数要对每张绑定表 count(*)，
     * 是这条路径上唯一跟表大小挂钩的抖动来源。详情页仍走 {@link #buildStatistic} 拿全四项。</p>
     *
     * @param spaceId             对象所属空间 id；关系计数按该空间裁剪，与 {@link #buildStatistic} 口径一致
     * @param ontologyIdentifiers 待统计的对象唯一标识
     * @return key 为对象唯一标识，入参中的每个对象都有一条结果，无关联数据时为 0；entityCount 不填
     */
    public Map<String, OntologyMetaStatisticVO> batchBuildStatistic(Integer spaceId, List<String> ontologyIdentifiers) {
        if (CollectionUtils.isEmpty(ontologyIdentifiers)) {
            return Collections.emptyMap();
        }

        var propertyCntMap = toCountMap(propertyMapper.countGroupByOntology());
        var actionCntMap = toCountMap(actionMapper.countGroupByOntology());
        var linkCntMap = toCountMap(linkGroupMapper.countGroupByOntologyInSpace(spaceId, Status.ENABLE.getValue()));

        var res = new HashMap<String, OntologyMetaStatisticVO>(ontologyIdentifiers.size());
        for (var identifier : ontologyIdentifiers) {
            res.put(identifier, OntologyMetaStatisticVO.builder()
                    .uniqueIdentifier(identifier)
                    .propertyCount(propertyCntMap.getOrDefault(identifier, 0))
                    .relationCount(linkCntMap.getOrDefault(identifier, 0))
                    .actionCount(actionCntMap.getOrDefault(identifier, 0))
                    .build());
        }
        return res;
    }

    private Map<String, Integer> toCountMap(List<OntologyStatisticsCountView> counts) {
        var res = new HashMap<String, Integer>(counts.size());
        counts.forEach(count -> {
            if (count.getOntologyUniqueIdentifier() != null && count.getCnt() != null) {
                res.put(count.getOntologyUniqueIdentifier(), count.getCnt());
            }
        });
        return res;
    }


    @Override
    public List<OntologyMetaInfoVO> searchByKeyword(String keyword, Integer spaceId) {
        var searchKeyword = StringUtils.isEmpty(keyword) ? "" : keyword;
        var queryWrapper = new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .eq(spaceId != null, OntologyMeta::getOntologySpaceId, spaceId)
                .and(wrapper -> wrapper
                        .or().like(OntologyMeta::getDescription, searchKeyword)
                        .or().like(OntologyMeta::getApiName, searchKeyword)
                        .or().like(OntologyMeta::getDisplayName, searchKeyword)
                );
        var metaList = list(queryWrapper)
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
        queryWrapper.orderByAsc(OntologyMeta::getId);
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
    public List<OntologyMetaInfoVO> queryMetaBySpaceAndName(String spaceName, String objectName) {
        if (StringUtils.isEmpty(spaceName) || StringUtils.isEmpty(objectName)) {
            return Lists.newArrayList();
        }
        var spaces = spaceService.list(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getApiName, spaceName)
                .or().eq(OntologySpace::getDisplayName, spaceName));
        if (CollectionUtils.isEmpty(spaces)) {
            return Lists.newArrayList();
        }
        var spaceIds = spaces.stream().map(OntologySpace::getId).collect(Collectors.toList());
        var metaList = list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .in(OntologyMeta::getOntologySpaceId, spaceIds)
                .and(w -> w.eq(OntologyMeta::getApiName, objectName)
                        .or().eq(OntologyMeta::getDisplayName, objectName)));
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

    @Override
    public List<OntologyMetaNodeVO> getInheritanceTree(Integer spaceId) {
        var metaList = list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId)
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .orderByAsc(OntologyMeta::getId));
        if (CollectionUtils.isEmpty(metaList)) {
            return Collections.emptyList();
        }
        var metaMap = metaList.stream().collect(Collectors.toMap(
                OntologyMeta::getUniqueIdentifier,
                v -> OntologyMetaNodeVO.builder()
                        .parentUniqueIdentifier(v.getParentUniqueIdentifier())
                        .uniqueIdentifier(v.getUniqueIdentifier())
                        .displayName(v.getDisplayName())
                        .childNodes(new ArrayList<>())
                        .build(),
                (a, b) -> a));
        return metaMap.values().stream()
                .filter(v -> StringUtils.isEmpty(v.getParentUniqueIdentifier()))
                .map(parent -> {
                    buildInheritanceTree(parent, metaMap);
                    return parent;
                })
                .collect(Collectors.toList());
    }

    private void buildInheritanceTree(OntologyMetaNodeVO parent, Map<String, OntologyMetaNodeVO> metaMap) {
        metaMap.values().forEach(child -> {
            if (StringUtils.equals(child.getParentUniqueIdentifier(), parent.getUniqueIdentifier())) {
                parent.getChildNodes().add(child);
                buildInheritanceTree(child, metaMap);
            }
        });
    }

    @SneakyThrows
    @Override
    public OntologyImportResultVO importOntologies(MultipartFile file, Integer targetSpaceId) {
        InputStream inputStream = file.getInputStream();
        List<OntologyCreateDTO> ontologyList = objectMapper.readValue(inputStream, new TypeReference<List<OntologyCreateDTO>>() {
        });
        List<String> failedOntology = Lists.newArrayList();
        List<OntologyImportResultVO.RenamedItem> renamed = Lists.newArrayList();
        //本 JSON 已建对象：空间 id -> (displayName -> 新对象 uid)，第二遍建关系时端点优先取它
        Map<Integer, Map<String, String>> uidByJsonName = new HashMap<>();
        //空间内已占用的名字，含本批次前序对象，逐个增量登记
        Map<Integer, Set<String>> usedApiNames = new HashMap<>();
        Map<Integer, Set<String>> usedDisplayNames = new HashMap<>();
        //关系延后创建：端点可能指向排在后面的 JSON 对象，当场建解析不到
        Map<Integer, List<OntologyRelationDTO>> pendingRelations = new LinkedHashMap<>();

        for (OntologyCreateDTO dto : ontologyList) {
            var metaData = dto.getMetadata();
            if (metaData == null) {
                continue;
            }
            var jsonApiName = metaData.getApiName();
            var jsonDisplayName = metaData.getDisplayName();
            try {
                var spaceId = resolveImportSpace(metaData, targetSpaceId).getId();
                //重名直接落库会撞 ontology_meta 的 (space_id, api_name) 与 (space_id, display_name) 两条唯一索引
                var apiName = nextFreeName(jsonApiName,
                        usedApiNames.computeIfAbsent(spaceId, id -> listUsedNames(id, OntologyMeta::getApiName)));
                var displayName = nextFreeName(jsonDisplayName,
                        usedDisplayNames.computeIfAbsent(spaceId, id -> listUsedNames(id, OntologyMeta::getDisplayName)));
                metaData.setApiName(apiName).setDisplayName(displayName);

                var relations = dto.getRelations();
                //摘掉 relations，避免 importOntology 内按空间 displayName 当场建关系，挂到空间里的同名老对象上
                dto.setRelations(null);
                if (CollectionUtils.isNotEmpty(relations)) {
                    pendingRelations.computeIfAbsent(spaceId, id -> Lists.newArrayList()).addAll(relations);
                }

                proxyService.importOntology(dto, spaceId);

                var created = getOne(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, spaceId)
                        .eq(OntologyMeta::getDisplayName, displayName));
                if (created != null) {
                    var names = uidByJsonName.computeIfAbsent(spaceId, id -> new HashMap<>());
                    names.put(displayName, created.getUniqueIdentifier());
                    //关系端点写的是 JSON 里的原名，改名后原名与新名都要能解析到这个新对象
                    names.putIfAbsent(jsonDisplayName, created.getUniqueIdentifier());
                }
                if (!StringUtils.equals(apiName, jsonApiName) || !StringUtils.equals(displayName, jsonDisplayName)) {
                    log.info("对象重名，已改名：{}/{} -> {}/{}", jsonApiName, jsonDisplayName, apiName, displayName);
                    renamed.add(OntologyImportResultVO.RenamedItem.builder()
                            .kind("ONTOLOGY")
                            .displayNameFrom(jsonDisplayName)
                            .displayNameTo(displayName)
                            .apiNameFrom(jsonApiName)
                            .apiNameTo(apiName)
                            .build());
                }
            } catch (Exception e) {
                log.error("对象 {} 导入失败", jsonDisplayName, e);
                failedOntology.add(jsonDisplayName);
            }
        }

        pendingRelations.forEach((spaceId, relations) ->
                importPendingRelations(spaceId, relations, uidByJsonName.getOrDefault(spaceId, Collections.emptyMap()), renamed));

        return OntologyImportResultVO.builder()
                .failedOntologies(failedOntology)
                .renamed(renamed)
                .build();
    }

    /**
     * 解析导入目标空间：显式指定空间 id 优先，否则沿用 JSON 里的 ontologySpaceName。
     */
    private OntologySpace resolveImportSpace(OntologyMetaDataDTO metaData, Integer targetSpaceId) {
        var space = targetSpaceId != null ? spaceService.getById(targetSpaceId)
                : spaceService.getOne(new LambdaQueryWrapper<OntologySpace>()
                        .eq(OntologySpace::getDisplayName, metaData.getOntologySpaceName()));
        PreconditionUtils.checkNotNull(space, targetSpaceId != null ? "invalid ontology space id:" + targetSpaceId
                : "invalid ontology space name:" + metaData.getOntologySpaceName());
        return space;
    }

    /**
     * 取一个未被占用的名字：原名被占用时依次追加 _1、_2，并登记进 used 供同批次后续对象使用。
     */
    private String nextFreeName(String base, Set<String> used) {
        if (!used.contains(base)) {
            used.add(base);
            return base;
        }
        for (int seq = 1; ; seq++) {
            var candidate = base + "_" + seq;
            if (!used.contains(candidate)) {
                used.add(candidate);
                return candidate;
            }
        }
    }

    /**
     * 空间内对象已占用的名字。唯一索引不含 status，停用对象同样占名，这里不按 status 过滤。
     */
    private Set<String> listUsedNames(Integer spaceId, Function<OntologyMeta, String> nameGetter) {
        return list(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, spaceId))
                .stream().map(nameGetter).collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 导入的第二遍：全部对象已落库后创建关系。
     *
     * <p>端点先按本 JSON 内的对象解析，解析不到再回目标空间按 displayName 查找，仍找不到则跳过该关系。</p>
     */
    private void importPendingRelations(Integer spaceId, List<OntologyRelationDTO> relations,
                                        Map<String, String> uidByJsonName,
                                        List<OntologyImportResultVO.RenamedItem> renamed) {
        var uidBySpaceName = list(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, spaceId)
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream().collect(Collectors.toMap(OntologyMeta::getDisplayName, OntologyMeta::getUniqueIdentifier, (a, b) -> a));
        var links = linkService.list(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologySpaceId, spaceId)
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue()));
        //关系表无唯一约束，重名不会报错，这里只避免一次导入堆出同名关系
        var usedNames = links.stream().map(OntologyLinkGroup::getName).collect(Collectors.toCollection(HashSet::new));
        var usedApiNames = links.stream().map(OntologyLinkGroup::getApiName).collect(Collectors.toCollection(HashSet::new));
        var linkRootCategoryId = getLinkCategoryRoot(spaceId);
        for (var relation : relations) {
            var fromName = relation.getOntologyNameFrom();
            var toName = relation.getOntologyNameTo();
            var fromUid = resolveRelationEndpoint(fromName, uidByJsonName, uidBySpaceName);
            var toUid = resolveRelationEndpoint(toName, uidByJsonName, uidBySpaceName);
            if (fromUid == null || toUid == null) {
                log.warn("关系 {} 导入跳过：端点对象不存在，from={}, to={}", relation.getName(), fromName, toName);
                continue;
            }
            var name = relation.getName();
            var apiName = relation.getApiName();
            //name 与 apiName 任一重名，两者一起加后缀，保持这一对名字同步
            for (int seq = 1; usedNames.contains(name)
                    || (StringUtils.isNotEmpty(apiName) && usedApiNames.contains(apiName)); seq++) {
                name = relation.getName() + "_" + seq;
                apiName = StringUtils.isEmpty(relation.getApiName()) ? null : relation.getApiName() + "_" + seq;
            }
            usedNames.add(name);
            if (StringUtils.isNotEmpty(apiName)) {
                usedApiNames.add(apiName);
            }
            if (!StringUtils.equals(name, relation.getName()) || !StringUtils.equals(apiName, relation.getApiName())) {
                log.info("关系重名，已改名：{}/{} -> {}/{}", relation.getApiName(), relation.getName(), apiName, name);
                renamed.add(OntologyImportResultVO.RenamedItem.builder()
                        .kind("RELATION")
                        .displayNameFrom(relation.getName())
                        .displayNameTo(name)
                        .apiNameFrom(relation.getApiName())
                        .apiNameTo(apiName)
                        .build());
            }
            //走 createLink 而非直接落库：与画布创建关系同一条路径，关系索引等副作用一并触发；实例关系边不在这里生成
            linkService.createLink(new OntologyLinkCreateParam()
                    .setName(name)
                    .setApiName(StringUtils.defaultIfBlank(apiName, "relation_" + IdGenerator.generateUUID()))
                    .setDescription(relation.getDescription())
                    .setType(relation.getType() == null ? OntologyLinkTypeEnum.OTHER : relation.getType())
                    .setOntologyUniqueIdentifierFrom(fromUid)
                    .setOntologyUniqueIdentifierTo(toUid)
                    .setCategoryId(linkRootCategoryId)
                    .setSpaceId(spaceId));
        }
    }

    /**
     * 关系端点解析：本 JSON 内的新建对象优先，避免挂到目标空间里的同名老对象上。
     */
    private String resolveRelationEndpoint(String endpointName, Map<String, String> uidByJsonName,
                                           Map<String, String> uidBySpaceName) {
        if (StringUtils.isEmpty(endpointName)) {
            return null;
        }
        var uid = uidByJsonName.get(endpointName);
        return uid != null ? uid : uidBySpaceName.get(endpointName);
    }

    @SneakyThrows
    @Override
    public List<String> importOntologiesToSpace(Integer targetSpaceId, MultipartFile file) {
        InputStream inputStream = file.getInputStream();
        
        // 尝试读取为完整空间结构，如果失败则按对象列表处理
        List<OntologyCreateDTO> ontologyList;
        try {
            var spaceData = objectMapper.readValue(inputStream, new TypeReference<com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceCreateDTO>() {});
            ontologyList = spaceData.getOntologies();
            if (ontologyList == null) {
                ontologyList = Lists.newArrayList();
            }
        } catch (Exception e) {
            // 如果不是完整空间结构，重新读取为对象列表
            inputStream = file.getInputStream();
            ontologyList = objectMapper.readValue(inputStream, new TypeReference<List<OntologyCreateDTO>>() {});
        }
        
        List<String> failedOntology = Lists.newArrayList();
        // 记录导入结果：displayName -> uniqueIdentifier，用于后续处理继承关系
        Map<String, String> displayNameToUid = new HashMap<>();
        // 记录有父对象的对象：displayName -> parentDisplayName
        Map<String, String> parentRelations = new HashMap<>();

        // 第一遍：删除冲突对象并导入
        for (OntologyCreateDTO dto : ontologyList) {
            try {
                var metaData = dto.getMetadata();
                if (metaData == null) {
                    continue;
                }
                // 检查并删除冲突对象（按 displayName 或 apiName 匹配）
                deleteConflictingOntology(targetSpaceId, metaData.getDisplayName(), metaData.getApiName());

                var result = proxyService.importOntology(dto, targetSpaceId);
                if (result != null) {
                    // 查询新创建的对象 uniqueIdentifier
                    var meta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                            .eq(OntologyMeta::getOntologySpaceId, targetSpaceId)
                            .eq(OntologyMeta::getDisplayName, metaData.getDisplayName())
                            .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                            .orderByDesc(OntologyMeta::getId)
                            .last("LIMIT 1"));
                    if (meta != null) {
                        displayNameToUid.put(metaData.getDisplayName(), meta.getUniqueIdentifier());
                    }
                    // 记录继承关系
                    if (StringUtils.isNotEmpty(metaData.getParentOntologyDisplayName())) {
                        parentRelations.put(metaData.getDisplayName(), metaData.getParentOntologyDisplayName());
                    }
                }
            } catch (Exception e) {
                log.error("对象 {} 导入失败", dto.getMetadata().getDisplayName(), e);
                failedOntology.add(dto.getMetadata().getDisplayName());
            }
        }

        // 第二遍：处理继承关系
        for (var entry : parentRelations.entrySet()) {
            var childDisplayName = entry.getKey();
            var parentDisplayName = entry.getValue();
            var childUid = displayNameToUid.get(childDisplayName);
            var parentUid = displayNameToUid.get(parentDisplayName);
            if (childUid == null) {
                log.warn("继承关系跳过：子对象 {} 不存在", childDisplayName);
                continue;
            }
            if (parentUid == null) {
                log.warn("继承关系跳过：父对象 {} 不存在", parentDisplayName);
                continue;
            }
            // 更新 parentUniqueIdentifier
            update(new LambdaUpdateWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, childUid)
                    .set(OntologyMeta::getParentUniqueIdentifier, parentUid));
            log.info("继承关系设置成功：{} -> {}", childDisplayName, parentDisplayName);
        }

        return failedOntology;
    }

    @SneakyThrows
    @Override
    public List<String> importOntologiesFromJson(String jsonContent) {
        List<OntologyCreateDTO> ontologyList = objectMapper.readValue(jsonContent, new TypeReference<List<OntologyCreateDTO>>() {
        });
        List<String> failedOntology = Lists.newArrayList();
        ontologyList.forEach(dto -> {
            try {
                proxyService.importOntology(dto);
            } catch (Exception e) {
                log.error("对象 {} 导入失败", dto.getMetadata().getDisplayName(), e);
                failedOntology.add(dto.getMetadata().getDisplayName());
            }
        });
        return failedOntology;
    }

    @SneakyThrows
    @Override
    public List<String> importOntologiesToSpaceFromJson(Integer targetSpaceId, String jsonContent) {
        // 尝试读取为完整空间结构，如果失败则按对象列表处理
        List<OntologyCreateDTO> ontologyList;
        try {
            var spaceData = objectMapper.readValue(jsonContent, new TypeReference<com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceCreateDTO>() {});
            ontologyList = spaceData.getOntologies();
            if (ontologyList == null) {
                ontologyList = Lists.newArrayList();
            }
        } catch (Exception e) {
            // 如果不是完整空间结构，按对象列表处理
            ontologyList = objectMapper.readValue(jsonContent, new TypeReference<List<OntologyCreateDTO>>() {});
        }

        List<String> failedOntology = Lists.newArrayList();
        // 记录导入结果：displayName -> uniqueIdentifier，用于后续处理继承关系
        Map<String, String> displayNameToUid = new HashMap<>();
        // 记录有父对象的对象：displayName -> parentDisplayName
        Map<String, String> parentRelations = new HashMap<>();

        // 第一遍：删除冲突对象并导入
        for (OntologyCreateDTO dto : ontologyList) {
            try {
                var metaData = dto.getMetadata();
                if (metaData == null) {
                    continue;
                }
                // 检查并删除冲突对象（按 displayName 或 apiName 匹配）
                deleteConflictingOntology(targetSpaceId, metaData.getDisplayName(), metaData.getApiName());

                var result = proxyService.importOntology(dto, targetSpaceId);
                if (result != null) {
                    // 查询新创建的对象 uniqueIdentifier
                    var meta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                            .eq(OntologyMeta::getOntologySpaceId, targetSpaceId)
                            .eq(OntologyMeta::getDisplayName, metaData.getDisplayName())
                            .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                            .orderByDesc(OntologyMeta::getId)
                            .last("LIMIT 1"));
                    if (meta != null) {
                        displayNameToUid.put(metaData.getDisplayName(), meta.getUniqueIdentifier());
                    }
                    // 记录继承关系
                    if (StringUtils.isNotEmpty(metaData.getParentOntologyDisplayName())) {
                        parentRelations.put(metaData.getDisplayName(), metaData.getParentOntologyDisplayName());
                    }
                }
            } catch (Exception e) {
                log.error("对象 {} 导入失败", dto.getMetadata().getDisplayName(), e);
                failedOntology.add(dto.getMetadata().getDisplayName());
            }
        }

        // 第二遍：处理继承关系
        for (var entry : parentRelations.entrySet()) {
            var childDisplayName = entry.getKey();
            var parentDisplayName = entry.getValue();
            var childUid = displayNameToUid.get(childDisplayName);
            var parentUid = displayNameToUid.get(parentDisplayName);
            if (childUid == null) {
                log.warn("继承关系跳过：子对象 {} 不存在", childDisplayName);
                continue;
            }
            if (parentUid == null) {
                log.warn("继承关系跳过：父对象 {} 不存在", parentDisplayName);
                continue;
            }
            // 更新 parentUniqueIdentifier
            update(new LambdaUpdateWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, childUid)
                    .set(OntologyMeta::getParentUniqueIdentifier, parentUid));
            log.info("继承关系设置成功：{} -> {}", childDisplayName, parentDisplayName);
        }

        return failedOntology;
    }

    /**
     * 删除目标空间中冲突的对象（按 displayName 或 apiName 匹配）
     */
    private void deleteConflictingOntology(Integer targetSpaceId, String displayName, String apiName) {
        // 按 displayName 查找冲突对象
        var conflictByDisplayName = getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, targetSpaceId)
                .eq(OntologyMeta::getDisplayName, displayName));
        if (conflictByDisplayName != null) {
            log.info("删除冲突对象（displayName匹配）：{} ({})", displayName, conflictByDisplayName.getUniqueIdentifier());
            deleteOntology(conflictByDisplayName.getUniqueIdentifier());
            return;
        }
        // 按 apiName 查找冲突对象
        var conflictByApiName = getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, targetSpaceId)
                .eq(OntologyMeta::getApiName, apiName));
        if (conflictByApiName != null) {
            log.info("删除冲突对象（apiName匹配）：{} ({})", apiName, conflictByApiName.getUniqueIdentifier());
            deleteOntology(conflictByApiName.getUniqueIdentifier());
        }
    }


    @Transactional(value = "mainTransactionManager")
    public Map<String, Object> importOntology(OntologyCreateDTO dto) {
        return importOntology(dto, null);
    }

    /**
     * 导入单个对象到指定空间。
     *
     * @param targetSpaceId 目标空间 id，为 null 时按 {@code metadata.ontologySpaceName} 解析空间；
     *                      空间导入已 createSpace 拿到新 id，必须直接落进该空间，否则文件里的空间名与
     *                      既有空间重名时对象会写回旧空间并撞 ontology_meta 空间内重名唯一键
     * @return 实例数据导出主键到新主键的映射，无实例数据时为空 map
     */
    @Transactional(value = "mainTransactionManager")
    public Map<String, Object> importOntology(OntologyCreateDTO dto, Integer targetSpaceId) {

        var metaData = dto.getMetadata();
        PreconditionUtils.checkNotNull(metaData, "ontology meta data is null");
        //保存基本信息
        var space = targetSpaceId != null ? spaceService.getById(targetSpaceId)
                : spaceService.getOne(new LambdaQueryWrapper<OntologySpace>()
                        .eq(OntologySpace::getDisplayName, metaData.getOntologySpaceName()));
        PreconditionUtils.checkNotNull(space, targetSpaceId != null ? "invalid ontology space id:" + targetSpaceId
                : "invalid ontology space name:" + dto.getMetadata().getOntologySpaceName());

        //空间分组
        var groups = groupService.list(new LambdaQueryWrapper<OntologyGroup>()
                        .eq(OntologyGroup::getOntologySpaceId, space.getId()))
                .stream().filter(g -> metaData.getGroupNames().contains(g.getGroupName())).collect(Collectors.toList());

        var metaGroups = "";
        if (CollectionUtils.isNotEmpty(groups)) {
            metaGroups = groups.stream().map(OntologyGroup::getGroupId).collect(Collectors.joining(","));
        }

        //获取对象分类体系
        var ontologyCategories = categoryService.list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, space.getId()));
        var ontologyCategoryMap = ontologyCategories.stream()
                .collect(Collectors.toMap(OntologyCategory::getPath, OntologyCategory::getId, (a, b) -> a));
        //取根分类"全部"的id，路径找不到时兜底挂到根下
        Integer rootCategoryId = ontologyCategories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() == 0)
                .map(OntologyCategory::getId)
                .findFirst().orElse(null);

        Integer ontologyCategoryId = null;
        if (StringUtils.isNotEmpty(metaData.getCategoryPath())) {
            ontologyCategoryId = ontologyCategoryMap.get(metaData.getCategoryPath());
            if (ontologyCategoryId == null) {
                log.warn("对象 {} 导入：分类路径 {} 在目标空间不存在，兜底挂到根分类下",
                        metaData.getDisplayName(), metaData.getCategoryPath());
                ontologyCategoryId = rootCategoryId;
            }
        }

        var meta = OntologyMeta.builder()
                .apiName(metaData.getApiName())
                .description(metaData.getDescription())
                .status(Status.ENABLE.getValue())
                .displayName(metaData.getDisplayName())
                .metaGroupId(metaGroups)
                .uniqueIdentifier(IdGenerator.generateUUID())
                .ontologySpaceId(space.getId())
                .ontologyCategoryId(ontologyCategoryId)
                .build();
        save(meta);
        //保存属性分类：JSON 未携带分类树时，与正常创建对象一致，默认建根节点"全部"
        var propertyCategoryCreateParam = dto.getPropertyCategory();
        if (propertyCategoryCreateParam != null) {
            propertyCategoryCreateParam.setParentId(0)
                    .setOntologyIdentifier(meta.getUniqueIdentifier());
            ontologyPropertyService.createCategory(propertyCategoryCreateParam);
        } else {
            ontologyPropertyService.createCategory(PropertyCategoryCreateParam.builder()
                    .parentId(0)
                    .name("全部")
                    .ontologyIdentifier(meta.getUniqueIdentifier())
                    .build());
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
            var propertyCategories = propertyCategoryService.list(new LambdaQueryWrapper<PropertyCategory>()
                    .eq(PropertyCategory::getOntologyUniqueIdentifier, meta.getUniqueIdentifier()));
            var categoryMap = propertyCategories.stream()
                    .collect(Collectors.toMap(PropertyCategory::getPath, PropertyCategory::getId, (a, b) -> a));
            //取属性根分类的id，路径找不到时兜底挂到根下
            Integer propertyRootCategoryId = propertyCategories.stream()
                    .filter(c -> c.getParentId() != null && c.getParentId() == 0)
                    .map(PropertyCategory::getId)
                    .findFirst().orElse(null);
            var ontologyPropertyCreateParams = props.stream().<OntologyPropertyCreateParam>map(p -> {
                Integer propCategoryId = null;
                if (StringUtils.isNotEmpty(p.getCategoryPath())) {
                    propCategoryId = categoryMap.get(p.getCategoryPath());
                    if (propCategoryId == null) {
                        log.warn("对象 {} 属性 {} 导入：分类路径 {} 不存在，兜底挂到根分类下",
                                metaData.getDisplayName(), p.getDisplayName(), p.getCategoryPath());
                        propCategoryId = propertyRootCategoryId;
                    }
                }
                return OntologyPropertyCreateParam.builder()
                        .apiName(p.getApiName())
                        .description(p.getDescription())
                        .displayName(p.getDisplayName())
                        .dataType(p.getDataType())
                        .isPrimaryKey(p.getIsPrimaryKey())
                        .isTitleKey(p.getIsTitleKey())
                        .ontologyIdentifier(meta.getUniqueIdentifier())
                        .defaultValue(p.getDefaultValue())
                        .storageGroup(p.getStorageGroup())
                        .categoryId(propCategoryId)
                        .metadata(p.getMetadata())
                        .build();
            }).collect(Collectors.toList());
            ontologyPropertyService.batchCreateProperties(ontologyPropertyCreateParams);
        }
        //自动建实体表以及关联属性数据源：仅当导入了实例数据时才执行，无实例数据则跳过（避免无谓建表/绑定数据源及主键校验失败）
        var instances = dto.getInstances();
        if (instances != null && CollectionUtils.isNotEmpty(instances.getNodes())) {
            var importBindResult = ontologyPropertyService.autoBindDatasource(meta.getUniqueIdentifier());
            if (importBindResult != null && CollectionUtils.isNotEmpty(importBindResult.getBlockedItems())) {
                log.warn("导入对象{}属性自动关联数据源部分跳过，未绑定属性的实例值不会写入实体表：{}",
                        meta.getUniqueIdentifier(), importBindResult.getMessage());
            }
        }
        //保存关系：端点以显示名称解析，目标空间缺少另一端对象时跳过该关系；关系统一挂到关系分类树根节点
        var relations = dto.getRelations();
        Map<Integer, String> relationMap = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(relations)) {
            var uidByDisplayName = list(new LambdaQueryWrapper<OntologyMeta>()
                            .eq(OntologyMeta::getOntologySpaceId, space.getId())
                            .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                    .stream().collect(Collectors.toMap(OntologyMeta::getDisplayName, OntologyMeta::getUniqueIdentifier, (a, b) -> a));
            var linkRootCategoryId = getLinkCategoryRoot(space.getId());
            for (int i = 0; i < relations.size(); i++) {
                var r = relations.get(i);
                var fromUid = uidByDisplayName.get(r.getOntologyNameFrom());
                var toUid = uidByDisplayName.get(r.getOntologyNameTo());
                if (fromUid == null || toUid == null) {
                    log.warn("关系 {} 导入跳过：端点对象不存在，from={}, to={}", r.getName(), r.getOntologyNameFrom(), r.getOntologyNameTo());
                    continue;
                }
                //走 createLink 而非直接落库：与画布创建关系同一条路径，关系索引等副作用一并触发；实例关系边不在这里生成
                relationMap.put(i, linkService.createLink(new OntologyLinkCreateParam()
                        .setName(r.getName())
                        .setApiName(StringUtils.defaultIfBlank(r.getApiName(), "relation_" + IdGenerator.generateUUID()))
                        .setDescription(r.getDescription())
                        .setType(r.getType() == null ? OntologyLinkTypeEnum.OTHER : r.getType())
                        .setOntologyUniqueIdentifierFrom(fromUid)
                        .setOntologyUniqueIdentifierTo(toUid)
                        .setCategoryId(linkRootCategoryId)
                        .setSpaceId(space.getId())));
            }
        }
        //保存函数（跳过已存在的函数，不验证唯一性）
        var funcs = dto.getFunctions();
        if (CollectionUtils.isNotEmpty(funcs)) {
            funcs.stream().forEach(f -> {
                try {
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
                } catch (Exception e) {
                    // 函数已存在或其他错误，跳过并记录警告
                    log.warn("函数 {} 导入跳过：{}", f.getFunctionApi(), e.getMessage());
                }
            });
        }
        //保存行为（跳过已存在的行为，不验证唯一性）
        var actions = dto.getActions();
        if (CollectionUtils.isNotEmpty(actions)) {
            actions.forEach(action -> {
                try {
                    val relationIndex = action.getRelationIndex();
                    var linkUniqIdentifier = relationIndex == null ? null : relationMap.get(relationIndex);
                    ActionLinkMappingParam linkMapping = null;
                    //关系因端点对象缺失被跳过时不挂 linkMapping：行为本身仍应导入，且 createAction 会因 link 不存在而整条报错
                    if (linkUniqIdentifier != null) {
                        linkMapping = ActionLinkMappingParam.builder()
                                .ontologyLinkUniqIdentifier(linkUniqIdentifier)
                                .build();
                    }
                    List<ActionParamMappingCreateParam> mappingIns = null;
                    var mapping = action.getMappingIns();
                    if (CollectionUtils.isNotEmpty(mapping)) {
                        var detail = functionService.getFunctionDetailByApi(space.getId(), action.getFunctionApi(), null);
                        var paramMap = detail.getParams().stream().collect(Collectors.toMap(v -> v.getParamName(), v -> v));

                        mappingIns = mapping.stream().map(m -> {
                            var ontologyMeta = getOne(new LambdaQueryWrapper<OntologyMeta>()
                                    .eq(OntologyMeta::getOntologySpaceId, space.getId())
                                    .eq(OntologyMeta::getDisplayName, m.getOntologyName()));
                            if (ontologyMeta == null) {
                                log.warn("行为参数映射跳过：对象 {} 不存在", m.getOntologyName());
                                return null;
                            }
                            var property = ontologyPropertyService.getOne(new LambdaQueryWrapper<OntologyProperty>()
                                    .eq(OntologyProperty::getDisplayName, m.getPropertyName())
                                    .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyMeta.getUniqueIdentifier()));
                            if (property == null) {
                                log.warn("行为参数映射跳过：属性 {} 不存在", m.getPropertyName());
                                return null;
                            }

                            return ActionParamMappingCreateParam.builder()
                                    .ontologyUniqueIdentifier(ontologyMeta.getUniqueIdentifier())
                                    .functionParamId(paramMap.get(m.getFunctionParamName()).getParamId())
                                    .propertyUniqueIdentifier(property.getUniqueIdentifier())
                                    .build();

                        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());

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
                } catch (Exception e) {
                    // 行为已存在或其他错误，跳过并记录警告
                    log.warn("行为 {} 导入跳过：{}", action.getActionApi(), e.getMessage());
                }

            });
        }
        //导入实例数据（若传递了 instances）：写数据湖物理表，id 由数据库重新生成；对象未绑定数据源或无实例时静默跳过
        return entityService.importInstances(meta.getUniqueIdentifier(), instances);
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
    public List<OntologyCreateDTO> exportOntology(String uniqueIdentifiers, OntologyExportTypeEnum exportType) {
        var uids = Arrays.stream(StringUtils.split(StringUtils.defaultString(uniqueIdentifiers), ",，"))
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(uids), "无效的对象uniqueIdentifier:" + uniqueIdentifiers);

        var metaMap = list(new LambdaQueryWrapper<OntologyMeta>()
                        .in(OntologyMeta::getUniqueIdentifier, uids)
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream().collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, meta -> meta, (a, b) -> a));
        var missing = uids.stream().filter(uid -> !metaMap.containsKey(uid)).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(missing)) {
            log.warn("导出对象跳过不存在或已停用的对象: {}", missing);
        }
        //按入参顺序输出
        var metas = new ArrayList<OntologyMeta>();
        uids.forEach(uid -> {
            var meta = metaMap.get(uid);
            if (meta != null) {
                metas.add(meta);
            }
        });
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(metas), "对象不存在或已停用:" + uniqueIdentifiers);
        PreconditionUtils.checkArgument(metas.stream().map(OntologyMeta::getOntologySpaceId).distinct().count() == 1,
                "不支持导出分属不同空间的对象，请按空间分别导出");

        var space = getSpaceOrThrow(metas.get(0).getOntologySpaceId());
        //关系端点可能指向同空间其它对象，displayName 解析需覆盖空间内全部启用对象
        var scopeMetas = listEnabledMetas(space);
        //withRelations=true：关系随对象导出；空间导出的关系由空间级 links 承载，不在此重复
        return buildOntologies(space, metas, scopeMetas, exportType, true);
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
        //空间导出的关系放在 links 段（含实例关系边），对象级 relations 不重复导出，否则导入会建出两份关系
        return buildOntologies(space, metas, metas, exportType, false);
    }

    private OntologySpace getSpaceOrThrow(Integer spaceId) {
        var space = spaceService.getById(spaceId);
        PreconditionUtils.checkNotNull(space, "对象空间不存在:" + spaceId);
        return space;
    }

    private List<OntologyMeta> listEnabledMetas(OntologySpace space) {
        return list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, space.getId())
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
    }

    /**
     * 关系分类树根节点（parentId=0，创建空间时默认生成）id。
     *
     * <p>取列表首条而非 getOne：历史数据可能存在多个 parentId=0 节点，getOne 会抛 TooManyResults。
     * 空间无根节点时返回 null，关系不挂分类，导入不应因此失败。</p>
     */
    private Integer getLinkCategoryRoot(Integer spaceId) {
        return linkCategoryService.list(new LambdaQueryWrapper<OntologyLinkCategory>()
                        .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                        .eq(OntologyLinkCategory::getParentId, 0))
                .stream().map(OntologyLinkCategory::getId).findFirst().orElse(null);
    }

    private List<OntologyCreateDTO> buildOntologies(OntologySpace space, List<OntologyMeta> metasToExport,
                                                    List<OntologyMeta> displayNameScope,
                                                    OntologyExportTypeEnum exportType,
                                                    boolean withRelations) {
        //uid -> displayName（解析范围取 displayNameScope：单对象导出时关系端点可能指向同空间其它对象，需覆盖全空间启用对象）
        var uidToDisplayName = displayNameScope.stream()
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, OntologyMeta::getDisplayName, (a, b) -> a));
        //对象分类 id -> path
        var ontologyCategoryPathMap = categoryService.list(new LambdaQueryWrapper<OntologyCategory>()
                        .eq(OntologyCategory::getOntologySpaceId, space.getId()))
                .stream().collect(Collectors.toMap(OntologyCategory::getId, OntologyCategory::getPath, (a, b) -> a));
        //分组 groupId -> groupName
        var groupNameMap = groupService.list(new LambdaQueryWrapper<OntologyGroup>()
                        .eq(OntologyGroup::getOntologySpaceId, space.getId()))
                .stream().collect(Collectors.toMap(OntologyGroup::getGroupId, OntologyGroup::getGroupName, (a, b) -> a));

        //一次导出多个对象时，两端都在列表内的关系会被两个对象各自带出，共享该集合使每条关系只输出一次，否则导入建出重复关系
        var exportedLinkIds = new HashSet<Long>();
        return metasToExport.stream()
                .map(meta -> buildExportDTO(meta, space, ontologyCategoryPathMap, groupNameMap, uidToDisplayName,
                        exportType, withRelations, exportedLinkIds))
                .collect(Collectors.toList());
    }

    private OntologyCreateDTO buildExportDTO(OntologyMeta meta, OntologySpace space,
                                             Map<Integer, String> ontologyCategoryPathMap,
                                             Map<String, String> groupNameMap,
                                             Map<String, String> uidToDisplayName,
                                             OntologyExportTypeEnum exportType,
                                             boolean withRelations,
                                             Set<Long> exportedLinkIds) {
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
                .parentOntologyDisplayName(StringUtils.isNotEmpty(meta.getParentUniqueIdentifier())
                        ? uidToDisplayName.get(meta.getParentUniqueIdentifier()) : null)
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
                .relations(withRelations ? exportOntologyRelations(uid, space.getId(), uidToDisplayName, exportedLinkIds) : null)
                .instances(instances)
                .build();
    }

    /**
     * 导出该对象关联的关系（含以它为起点或终点的），端点以对象显示名称表达，导入时据此解析对象。
     *
     * <p>端点对象已停用/已删除时 displayName 解析不到，导入了也无法还原，不导出。</p>
     *
     * <p>同一次导出可能包含一条关系的两端，据 exportedLinkIds 只输出一次。</p>
     */
    private List<OntologyRelationDTO> exportOntologyRelations(String ontologyUniqueIdentifier, Integer spaceId,
                                                              Map<String, String> uidToDisplayName,
                                                              Set<Long> exportedLinkIds) {
        var links = linkService.list(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologySpaceId, spaceId)
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue())
                .and(w -> w.eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier)
                        .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifier)));
        if (CollectionUtils.isEmpty(links)) {
            return Lists.newArrayList();
        }
        return links.stream()
                .filter(link -> uidToDisplayName.containsKey(link.getOntologyUniqueIdentifierFrom())
                        && uidToDisplayName.containsKey(link.getOntologyUniqueIdentifierTo()))
                .filter(link -> exportedLinkIds.add(link.getId()))
                .map(link -> OntologyRelationDTO.builder()
                        .name(link.getName())
                        .apiName(link.getApiName())
                        .description(link.getDescription())
                        .type(link.getType())
                        .ontologyNameFrom(uidToDisplayName.get(link.getOntologyUniqueIdentifierFrom()))
                        .ontologyNameTo(uidToDisplayName.get(link.getOntologyUniqueIdentifierTo()))
                        .build())
                .collect(Collectors.toList());
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