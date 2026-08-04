package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyCreateDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
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
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class OntologyMetaServiceImpl extends ServiceImpl<OntologyMetaMapper, OntologyMeta> implements OntologyMetaService {

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
    private PropertyMetadataSchemaService propertyMetadataSchemaService;

    @Resource
    private OntologyMetaServiceImpl proxyService;


    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    @Transactional(value = "mainTransactionManager")
    public String createOntology(OntologyMetaCreateParam ontologyCreateParam) {
        var meta = OntologyMeta.builder()
                .uniqueIdentifier(IdGenerator.generateUUID())
                .apiName(ontologyCreateParam.getApiName())
                .description(ontologyCreateParam.getDescription())
                .displayName(ontologyCreateParam.getDisplayName())
                .status(Status.ENABLE.getValue())
                .icon(ontologyCreateParam.getIconUrl())
                .build();
        //自主创建
        if (StringUtils.isEmpty(ontologyCreateParam.getParentOntologyUniqueIdentifier())) {
            meta.setMetaGroupId(String.join(",", ontologyCreateParam.getGroupIds()));
            this.save(meta);
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
        var parentOntology = this.getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, parentIdentifier).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkArgument(parentOntology != null, "父本体不存在", ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
        //创建子本体元数据
        meta.setMetaGroupId(parentOntology.getMetaGroupId())
                .setParentUniqueIdentifier(parentIdentifier)
                .setStatus(parentOntology.getStatus());
        this.save(meta);
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
                        .tag(v.getTag())
                        .primaryCategory(v.getPrimaryCategory())
                        .secondaryCategory(v.getSecondaryCategory())
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

    private PropertyCategoryCreateParam.CategoryNode buildCategoryTree(PropertyCategory category, Map<Integer, List<PropertyCategory>> categoryByParentId) {
        var children = categoryByParentId.get(category.getId());
        List<PropertyCategoryCreateParam.CategoryNode> childNodes = null;
        if (CollectionUtils.isNotEmpty(children)) {
            childNodes = children.stream()
                    .map(child -> buildCategoryTree(child, categoryByParentId))
                    .collect(Collectors.toList());
        }
        return PropertyCategoryCreateParam.CategoryNode.builder()
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
        PreconditionUtils.checkArgument(meta != null, "ontology not exist:" + ontologyIdentifier, ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
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
                .set(OntologyMeta::getUpdateTime, new Date());
        update(null, updateWrapper);
    }


    @Override
    public OntologyMetaInfoVO getMetaByUniqueIdentifier(String uniqueIdentifier) {
        OntologyMeta ontologyMeta = getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, uniqueIdentifier).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        updateById(ontologyMeta.setLatestQueryTime(new Date()));
        return DataConverter.convert(ontologyMeta);
    }


    @Override
    public List<OntologyMetaInfoVO> searchByKeyword(String keyword) {
        var searchKeyword = StringUtils.isEmpty(keyword) ? "" : keyword;
        return list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .and(wrapper -> wrapper
                        .or().like(OntologyMeta::getDescription, searchKeyword)
                        .or().like(OntologyMeta::getApiName, searchKeyword)
                        .or().like(OntologyMeta::getDisplayName, searchKeyword)
                ))
                .stream().map(DataConverter::convert).collect(Collectors.toList());
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
        var groups = groupService.list().stream().filter(g -> metaData.getGroupNames().contains(g.getGroupName())).collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(groups), "invalid group names:" + metaData.getGroupNames());
        var metaGroups = String.join(",", groups.stream().map(v -> v.getGroupId()).collect(Collectors.toList()));

        //保存基本信息
        var meta = OntologyMeta.builder()
                .apiName(metaData.getApiName())
                .description(metaData.getDescription())
                .status(Status.ENABLE.getValue())
                .displayName(metaData.getDisplayName())
                .metaGroupId(metaGroups)
                .uniqueIdentifier(IdGenerator.generateUUID())
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
                            .tag(p.getTag())
                            .description(p.getDescription())
                            .displayName(p.getDisplayName())
                            .dataType(p.getDataType())
                            .isPrimaryKey(p.getIsPrimaryKey())
                            .isTitleKey(p.getIsTitleKey())
                            .ontologyIdentifier(meta.getUniqueIdentifier())
                            .primaryCategory(p.getPrimaryCategory())
                            .secondaryCategory(p.getSecondaryCategory())
                            .defaultValue(p.getDefaultValue())
                            .storageGroup(p.getStorageGroup())
                            .categoryId(StringUtils.isEmpty(p.getCategoryPath()) ? null : categoryMap.get(p.getCategoryPath()))
                            .metadata(p.getMetadata())
                            .build())
                    .collect(Collectors.toList());
            ontologyPropertyService.batchCreateProperties(ontologyPropertyCreateParams);
        }
        //自动建实体表以及关联属性数据源
        ontologyPropertyService.autoBindDatasource(meta.getUniqueIdentifier());
        //保存关系
        var relations = dto.getRelations();
        Map<Integer, String> relationMap = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(relations)) {
            List<OntologyLinkGroup> links = Lists.newArrayList();
            for (int i = 0; i < relations.size(); i++) {
                var r = relations.get(i);
                var fromName = r.getOntologyUniqueIdentifierFrom();
                var toName = r.getOntologyUniqueIdentifierTo();
                var fromMeta = getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getDisplayName, fromName));
                var toMeta = getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getDisplayName, toName));

                var linkId = IdGenerator.generateUUID();
                relationMap.put(i, linkId);
                links.add(OntologyLinkGroup.builder()
                        .uniqueIdentifier(linkId)
                        .name(r.getName())
                        .type(r.getType())
                        .ontologyUniqueIdentifierFrom(fromMeta.getUniqueIdentifier())
                        .ontologyUniqueIdentifierTo(toMeta.getUniqueIdentifier())
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
                        var ontologyMeta = getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getDisplayName, m.getOntologyName()));
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
}