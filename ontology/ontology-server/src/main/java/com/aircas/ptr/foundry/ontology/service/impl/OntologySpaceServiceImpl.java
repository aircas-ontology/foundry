package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceCreateDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCanvasCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleTask;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.po.PropertyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.view.OntologyStatisticsCountView;
import com.aircas.ptr.foundry.ontology.model.view.SpaceStatisticsCountView;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceCanvasCreateVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceStatisticVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.service.PropertyCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyInstancesExportDTO;
import com.aircas.ptr.foundry.ontology.model.param.OntologySubspaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.SubspacePropertyFilter;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySubspaceCreateVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.SubspacePropertyFilterMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;


@Service
@RequiredArgsConstructor
@Slf4j
public class OntologySpaceServiceImpl extends ServiceImpl<OntologySpaceMapper, OntologySpace> implements OntologySpaceService {

    private final OntologyMetaMapper metaMapper;

    private final OntologyActionMapper actionMapper;

    private final OntologyPropertyMapper propertyMapper;

    private final OntologyLinkGroupMapper linkMapper;

    private final FunctionMapper functionMapper;

    private final ActionHandleRuleMapper ruleMapper;

    private final ActionHandleTaskMapper taskMapper;

    private final OntologySpaceMapper spaceMapper;

    private final OntologyCategoryMapper categoryMapper;

    private final OntologyCategoryService categoryService;

    private final TableMetadataService tableMetadataService;

    private final OntologyMetaServiceImpl ontologyMetaService;

    private final OntologyPropertyService ontologyPropertyService;

    private final OntologyLinkGroupService ontologyLinkGroupService;

    private final OntologyLinkCategoryMapper ontologyLinkCategoryMapper;

    private final PropertyCategoryService propertyCategoryService;

    private final OntologyLinkCategoryService ontologyLinkCategoryService;

    private final EntityService entityService;

    private final SubspacePropertyFilterMapper subspacePropertyFilterMapper;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Lazy
    @Resource
    private OntologySpaceServiceImpl proxy;


    @Transactional(transactionManager = "chainedTransactionManager")
    @Override
    public Integer createSpace(OntologySpaceCreateParam param) {
        //check param
        var space = spaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getDisplayName, param.getDisplayName())
                .or().eq(OntologySpace::getApiName, param.getApiName()));
        PreconditionUtils.checkArgument(space == null, "空间显示名称或api名称已存在", HttpStatus.BAD_REQUEST);
        //create space
        var ontologySpace = OntologySpace.builder()
                .description(param.getDescription())
                .icon(param.getIconUrl())
                .displayName(param.getDisplayName())
                .apiName(param.getApiName())
                .build();
        spaceMapper.insert(ontologySpace);
        //create new schema &  table_filed_mapping table
        tableMetadataService.initSpaceSchema(param.getApiName());
        // 默认创建本体（本地对象）分类树根节点
        createDefaultOntologyCategoryRoot(ontologySpace.getId());
        // 默认创建关系分类树根节点
        createDefaultLinkCategoryRoot(ontologySpace.getId());
        return ontologySpace.getId();
    }

    @Transactional(transactionManager = "chainedTransactionManager", rollbackFor = Exception.class)
    @Override
    public OntologySpaceCanvasCreateVO createSpaceWithCanvasContent(OntologySpaceCanvasCreateParam param) {
        Integer spaceId = param.getSpaceId();

        var space = spaceMapper.selectById(spaceId);
        PreconditionUtils.checkNotNull(space, "空间id不存在", HttpStatus.BAD_REQUEST);

        var existingMetaList = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(existingMetaList),
                "该空间下已存在本体对象，无法创建画布", HttpStatus.BAD_REQUEST);

        Integer defaultCategoryId = getOntologyCategoryRoot(spaceId);
        Integer defaultLinkCategoryId = getLinkCategoryRoot(spaceId);

        // create ontologies & properties, record apiName/displayName -> uniqueIdentifier for link resolution
        Map<String, String> uidByApiName = new HashMap<>();
        Map<String, String> uidByDisplayName = new HashMap<>();
        if (CollectionUtils.isNotEmpty(param.getOntologies())) {
            for (var canvasOntology : param.getOntologies()) {
                var metaParam = new OntologyMetaCreateParam()
                        .setDisplayName(canvasOntology.getDisplayName())
                        .setApiName(canvasOntology.getApiName())
                        .setDescription(canvasOntology.getDescription())
                        .setIconUrl(canvasOntology.getIconUrl())
                        .setCategoryId(defaultCategoryId);
                metaParam.setSpaceId(spaceId);

                var uniqueIdentifier = ontologyMetaService.createOntology(metaParam);
                uidByApiName.put(canvasOntology.getApiName(), uniqueIdentifier);
                uidByDisplayName.put(canvasOntology.getDisplayName(), uniqueIdentifier);

                Integer defaultPropertyCategoryId = getPropertyCategoryRoot(uniqueIdentifier);

                if (CollectionUtils.isNotEmpty(canvasOntology.getProperties())) {
                    for (var canvasProperty : canvasOntology.getProperties()) {
                        ontologyPropertyService.createProperty(
                                buildPropertyCreateParam(uniqueIdentifier, canvasProperty, defaultPropertyCategoryId));
                    }
                }
            }
        }

        // create links; each link is bound to the default relation category root
        if (CollectionUtils.isNotEmpty(param.getLinks())) {
            for (var canvasLink : param.getLinks()) {
                var fromUid = resolveOntologyUid(canvasLink.getFromOntologyApiName(), uidByApiName, uidByDisplayName);
                var toUid = resolveOntologyUid(canvasLink.getToOntologyApiName(), uidByApiName, uidByDisplayName);
                // apiName is required by createLink; pass through the canvas value, fall back to a generated one
                var linkApiName = StringUtils.isNotBlank(canvasLink.getApiName())
                        ? canvasLink.getApiName()
                        : ("relation_" + IdGenerator.generateUUID());
                var linkParam = new OntologyLinkCreateParam()
                        .setName(canvasLink.getName())
                        .setApiName(linkApiName)
                        .setOntologyUniqueIdentifierFrom(fromUid)
                        .setOntologyUniqueIdentifierTo(toUid)
                        .setType(OntologyLinkTypeEnum.OTHER)
                        .setSpaceId(spaceId)
                        .setCategoryId(defaultLinkCategoryId);
                ontologyLinkGroupService.createLink(linkParam);
            }
        }

        return OntologySpaceCanvasCreateVO.builder()
                .spaceId(spaceId)
                .build();
    }

    private OntologyPropertyCreateParam buildPropertyCreateParam(String ontologyUniqueIdentifier,
                                                                 OntologySpaceCanvasCreateParam.CanvasProperty canvasProperty,
                                                                 Integer propertyCategoryId) {
        var propertyParam = new OntologyPropertyCreateParam()
                .setDisplayName(canvasProperty.getDisplayName())
                .setApiName(canvasProperty.getApiName())
                .setDataType(canvasProperty.getDataType())
                .setDescription(canvasProperty.getDescription())
                .setIsPrimaryKey(Boolean.TRUE.equals(canvasProperty.getIsPrimaryKey()))
                .setIsTitleKey(Boolean.TRUE.equals(canvasProperty.getIsTitleKey()))
                .setDefaultValue(canvasProperty.getDefaultValue())
                .setStorageGroup(canvasProperty.getStorageGroup())
                .setCategoryId(propertyCategoryId);
        propertyParam.setOntologyIdentifier(ontologyUniqueIdentifier);
        return propertyParam;
    }

    /**
     * 创建（或复用）本体的属性分类树根节点，让画布导入的属性都能挂到分类下。
     */
    private Integer createDefaultPropertyCategoryRoot(String ontologyUniqueIdentifier) {
        var existingRoot = propertyCategoryService.getOne(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(PropertyCategory::getParentId, 0));
        if (existingRoot != null) {
            return existingRoot.getId();
        }
        var categoryParam = PropertyCategoryCreateParam.builder()
                .parentId(0)
                .name("全部")
                .ontologyIdentifier(ontologyUniqueIdentifier)
                .build();
        ontologyPropertyService.createCategory(categoryParam);
        var created = propertyCategoryService.getOne(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(PropertyCategory::getParentId, 0)
                .orderByDesc(PropertyCategory::getId));
        PreconditionUtils.checkNotNull(created, "创建属性分类失败", HttpStatus.INTERNAL_SERVER_ERROR);
        return created.getId();
    }

    /**
     * 创建（或复用）空间的关系分类树根节点，让画布导入的关系都能挂到分类下。
     */
    private Integer createDefaultLinkCategoryRoot(Integer spaceId) {
        var existingRoot = ontologyLinkCategoryService.getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                .eq(OntologyLinkCategory::getParentId, 0));
        if (existingRoot != null) {
            return existingRoot.getId();
        }
        var categoryParam = new OntologyLinkCategoryCreateParam()
                .setParentId(0)
                .setName("全部");
        categoryParam.setSpaceId(spaceId);
        ontologyLinkCategoryService.createCategory(categoryParam);
        var created = ontologyLinkCategoryService.getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                .eq(OntologyLinkCategory::getParentId, 0)
                .orderByDesc(OntologyLinkCategory::getId));
        PreconditionUtils.checkNotNull(created, "创建关系分类失败", HttpStatus.INTERNAL_SERVER_ERROR);
        return created.getId();
    }

    /**
     * 创建（或复用）空间的本体分类树全部，让画布导入的本体都能挂到分类下。
     */
    private Integer createDefaultOntologyCategoryRoot(Integer spaceId) {
        var existingRoot = categoryService.getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .eq(OntologyCategory::getParentId, 0));
        if (existingRoot != null) {
            return existingRoot.getId();
        }
        var categoryParam = new OntologyCategoryCreateParam()
                .setParentId(0)
                .setName("全部");
        categoryParam.setSpaceId(spaceId);
        categoryService.createCategory(categoryParam);
        var created = categoryService.getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .eq(OntologyCategory::getParentId, 0)
                .orderByDesc(OntologyCategory::getId));
        PreconditionUtils.checkNotNull(created, "创建本体分类失败", HttpStatus.INTERNAL_SERVER_ERROR);
        return created.getId();
    }

    private String resolveOntologyUid(String apiNameOrDisplayName, Map<String, String> uidByApiName, Map<String, String> uidByDisplayName) {
        var uid = uidByApiName.get(apiNameOrDisplayName);
        if (uid == null) {
            uid = uidByDisplayName.get(apiNameOrDisplayName);
        }
        PreconditionUtils.checkArgument(uid != null, "画布中不存在对象：" + apiNameOrDisplayName, HttpStatus.BAD_REQUEST);
        return uid;
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void updateSpace(OntologySpaceUpdateParam param) {
        //check param
        var space = spaceMapper.selectById(param.getSpaceId());
        PreconditionUtils.checkNotNull(space, "空间id不存在", HttpStatus.BAD_REQUEST);
        //update space by id
        spaceMapper.updateById(space.setDisplayName(param.getDisplayName())
                .setDescription(param.getDescription())
                .setIcon(param.getIconUrl()));
    }

    @Override
    public List<OntologySpaceVO> querySpace() {
        var spaces = list();
        if (CollectionUtils.isEmpty(spaces)) {
            return Lists.newArrayList();
        }
        var spaceMap = spaces.stream().collect(Collectors.toMap(v -> v.getId(), v -> v));
        // 获取本体meta map（按照空间id分组）
        var metaMap = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .in(OntologyMeta::getOntologySpaceId, spaceMap.keySet()))
                .stream().collect(Collectors.groupingBy(OntologyMeta::getOntologySpaceId));

        // 获取本体action map（key为ontology uniqueIdentifier, value为action 数量）
        Map<String, Integer> actionMap = actionMapper.countGroupByOntology().stream()
                .collect(Collectors.toMap(OntologyStatisticsCountView::getOntologyUniqueIdentifier, OntologyStatisticsCountView::getCnt));
        // 获取本体property map（key为ontology uniqueIdentifier, value为property 数量）
        Map<String, Integer> propertyMap = propertyMapper.countGroupByOntology().stream()
                .collect(Collectors.toMap(OntologyStatisticsCountView::getOntologyUniqueIdentifier, OntologyStatisticsCountView::getCnt));
        // 获取本体link map（key为ontology spaceId, value为link 数量）
        Map<Integer, Integer> linkMap = linkMapper.countGroupByOntologySpace().stream()
                .collect(Collectors.toMap(SpaceStatisticsCountView::getOntologySpaceId, SpaceStatisticsCountView::getCnt));

        //build OntologySpaceVO list
        var res = spaceMap.values().stream()
                .<OntologySpaceVO>map(space -> {
                    int ontologyCnt = 0, actionCnt = 0, propertyCnt = 0, linkCnt = 0;
                    var metaList = metaMap.get(space.getId());
                    if (CollectionUtils.isNotEmpty(metaList)) {
                        ontologyCnt = metaList.size();
                        for (OntologyMeta meta : metaList) {
                            var ontologyId = meta.getUniqueIdentifier();
                            propertyCnt += propertyMap.getOrDefault(ontologyId, 0);
                            actionCnt += actionMap.getOrDefault(ontologyId, 0);
                        }
                        linkCnt = linkMap.getOrDefault(space.getId(), 0);
                    }

                    return OntologySpaceVO.builder()
                            .iconUrl(space.getIcon())
                            .apiName(space.getApiName())
                            .spaceId(space.getId())
                            .displayName(space.getDisplayName())
                            .description(space.getDescription())
                            .ontologyCount(ontologyCnt)
                            .actionCount(actionCnt)
                            .propertyCount(propertyCnt)
                            .linkCount(linkCnt)
                            .createTime(space.getCreateTime())
                            .updateTime(space.getUpdateTime())
                            .build();
                })
                .collect(Collectors.toList());

        return res;
    }

    @Override
    public OntologySpaceStatisticVO getStatistic(Integer spaceId) {
        //check space
        var space = spaceMapper.selectById(spaceId);
        PreconditionUtils.checkNotNull(space, "空间id不存在", HttpStatus.BAD_REQUEST);

        // 对象（本体）数量
        var ontologyCount = metaMapper.selectCount(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId)
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        // 关系数量
        var linkCount = linkMapper.selectCount(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologySpaceId, spaceId)
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue()));
        // 函数算子数量
        var functionCount = functionMapper.selectCount(new LambdaQueryWrapper<Function>()
                .eq(Function::getOntologySpaceId, spaceId)
                .eq(Function::getStatus, Status.ENABLE.getValue()));
        // 行为数量
        var actionCount = actionMapper.selectCount(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getOntologySpaceId, spaceId)
                .eq(OntologyAction::getStatus, Status.ENABLE.getValue()));
        // 行为调度数量（调度规则 + 调度任务）
        var actionSchedulingCount = ruleMapper.selectCount(new LambdaQueryWrapper<ActionHandleRule>()
                .eq(ActionHandleRule::getOntologySpaceId, spaceId))
                + taskMapper.selectCount(new LambdaQueryWrapper<ActionHandleTask>()
                .eq(ActionHandleTask::getOntologySpaceId, spaceId));

        return OntologySpaceStatisticVO.builder()
                .spaceId(spaceId)
                .ontologyCount(Math.toIntExact(ontologyCount))
                .linkCount(Math.toIntExact(linkCount))
                .functionCount(Math.toIntExact(functionCount))
                .actionCount(Math.toIntExact(actionCount))
                .actionSchedulingCount(Math.toIntExact(actionSchedulingCount))
                .build();
    }

    /**
     * 当空间本体数量为0时才可删除，删除本体空间不会删除db下的schema
     *
     * @param spaceId
     */
    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void deleteSpace(Integer spaceId) {
        //check param
        var space = spaceMapper.selectById(spaceId);
        PreconditionUtils.checkNotNull(space, "空间id不存在", HttpStatus.BAD_REQUEST);
        var metaList = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(metaList), "该空间下存在本体，不能删除", HttpStatus.BAD_REQUEST);
        //delete ontology category
        categoryMapper.delete(new LambdaQueryWrapper<OntologyCategory>().eq(OntologyCategory::getOntologySpaceId, spaceId));
        //delete space
        removeById(spaceId);
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    @SneakyThrows
    public List<String> importOntologySpace(MultipartFile file) {
        InputStream inputStream = file.getInputStream();
        var spaceCreateDTO = jsonMapper.readValue(inputStream, new TypeReference<OntologySpaceCreateDTO>() {
        });
        //create ontology space
        var ontologySpace = spaceCreateDTO.getOntologySpace();
        if (ontologySpace == null) {
            return Lists.newArrayList();
        }
        var spaceId = proxy.createSpace(OntologySpaceCreateParam.builder()
                .apiName(ontologySpace.getApiName())
                .description(ontologySpace.getDescription())
                .displayName(ontologySpace.getDisplayName())
                .build());
        //create ontology category：挂到 createSpace 已默认创建的根分类"全部"下（用根 id 作 parentId），避免与其争用 parentId=0 而报"无效的parentId"
        var category = spaceCreateDTO.getOntologyCategory();
        if (category != null) {
            var rootId = getOntologyCategoryRoot(spaceId);
            // 如果导入的分类树顶层名为"全部"（与自动创建的根重名），跳过它，直接导入其子节点，避免嵌套成"全部/全部/..."
            if ("全部".equals(category.getName()) && CollectionUtils.isNotEmpty(category.getChildren())) {
                for (var child : category.getChildren()) {
                    var childParam = OntologyCategoryCreateParam.builder()
                            .name(child.getName())
                            .children(child.getChildren())
                            .parentId(rootId)
                            .build();
                    childParam.setSpaceId(spaceId);
                    categoryService.createCategory(childParam);
                }
            } else {
                category.setParentId(rootId);
                category.setSpaceId(spaceId);
                categoryService.createCategory(category);
            }
        }
        //import ontologies
        List<String> failedOntology = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(spaceCreateDTO.getOntologies())) {
            spaceCreateDTO.getOntologies().forEach(dto -> {
                try {
                    ontologyMetaService.importOntology(dto);
                } catch (Exception e) {
                    log.error("本体 {} 导入失败", dto.getMetadata().getDisplayName(), e);
                    failedOntology.add(dto.getMetadata().getDisplayName());
                }
            });
        }
        return failedOntology;
    }

    @Override
    public OntologySpaceCreateDTO exportOntologySpace(Integer spaceId, OntologyExportTypeEnum exportType) {
        var space = getById(spaceId);
        PreconditionUtils.checkNotNull(space, "本体空间不存在:" + spaceId);

        var ontologySpace = OntologySpaceDTO.builder()
                .apiName(space.getApiName())
                .displayName(space.getDisplayName())
                .description(space.getDescription())
                .build();

        var categories = categoryService.list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, space.getId()));
        var ontologyCategory = buildOntologyCategoryTree(categories);

        //一次装配空间下全部本体
        var ontologies = ontologyMetaService.exportOntologies(space, exportType);

        return OntologySpaceCreateDTO.builder()
                .ontologySpace(ontologySpace)
                .ontologyCategory(ontologyCategory)
                .ontologies(ontologies)
                .build();
    }

    private OntologyCategoryCreateParam buildOntologyCategoryTree(List<OntologyCategory> categories) {
        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }
        var root = categories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() == 0)
                .findFirst().orElse(null);
        if (root == null) {
            return null;
        }
        return OntologyCategoryCreateParam.builder()
                .parentId(root.getParentId())
                .name(root.getName())
                .children(buildOntologyCategoryChildren(root.getId(), categories))
                .build();
    }

    private List<CategoryNode> buildOntologyCategoryChildren(Integer parentId, List<OntologyCategory> categories) {
        return categories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId().equals(parentId))
                .map(c -> CategoryNode.builder()
                        .name(c.getName())
                        .children(buildOntologyCategoryChildren(c.getId(), categories))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 查询本体的属性分类树根节点（创建本体时已默认生成）。
     */
    private Integer getPropertyCategoryRoot(String ontologyUniqueIdentifier) {
        var root = propertyCategoryService.getOne(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(PropertyCategory::getParentId, 0));
        PreconditionUtils.checkNotNull(root, "属性分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }

    /**
     * 查询空间的关系分类树根节点（创建空间时已默认生成）。
     */
    private Integer getLinkCategoryRoot(Integer spaceId) {
        var root = ontologyLinkCategoryService.getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                .eq(OntologyLinkCategory::getParentId, 0));

        PreconditionUtils.checkNotNull(root, "关系分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }

    /**
     * 查询空间的本体（本地对象）分类树根节点（创建空间时已默认生成）。
     */
    private Integer getOntologyCategoryRoot(Integer spaceId) {
        var root = categoryService.getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .eq(OntologyCategory::getParentId, 0));
        PreconditionUtils.checkNotNull(root, "本体分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }

    // ===================== 子空间创建（原 OntologySubspaceServiceImpl 合并至此） =====================

    @Override
    @Transactional(transactionManager = "chainedTransactionManager", rollbackFor = Exception.class)
    public OntologySubspaceCreateVO createSubspace(OntologySubspaceCreateParam param) {
        // 1. 校验父空间
        var parentSpace = spaceMapper.selectById(param.getParentSpaceId());
        PreconditionUtils.checkNotNull(parentSpace, "父空间不存在", HttpStatus.BAD_REQUEST);

        // 2. 校验子空间名称/apiName 不重复
        checkSpaceNameConflict(param.getDisplayName(), param.getApiName());

        // 3. 预查并校验源本体
        var sourceOntologyIds = param.getSelectedOntologies().stream()
                .map(OntologySubspaceCreateParam.OntologySelection::getSourceOntologyUniqueIdentifier)
                .collect(Collectors.toList());
        var sourceMetas = metaMapper.selectList(
                new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, param.getParentSpaceId())
                        .in(OntologyMeta::getUniqueIdentifier, sourceOntologyIds)
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkArgument(sourceMetas.size() == sourceOntologyIds.size(),
                "选中的对象不存在或已停用", HttpStatus.BAD_REQUEST);
        var sourceMetaMap = sourceMetas.stream()
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, v -> v));

        // 4. 创建子空间
        var spaceId = createSpace(OntologySpaceCreateParam.builder()
                .displayName(param.getDisplayName())
                .apiName(param.getApiName())
                .description(param.getDescription())
                .iconUrl(param.getIconUrl())
                .build());

        // 4.1 拷贝父空间的本体分类树与关系分类树（含多级）到子空间，并记录  映射
        Integer newOntologyCatRoot = getOntologyCategoryRoot(spaceId);
        Integer newLinkCatRoot = getLinkCategoryRoot(spaceId);
        Map<Integer, Integer> ontologyCatMap = copyOntologyCategoryTree(param.getParentSpaceId(), spaceId);
        Map<Integer, Integer> linkCatMap = copyLinkCategoryTree(param.getParentSpaceId(), spaceId);

        // 5. 复制对象、属性、实例
        Map<String, String> ontologyMapping = new HashMap<>();
        for (var ontologySelection : param.getSelectedOntologies()) {
            var sourceMeta = sourceMetaMap.get(ontologySelection.getSourceOntologyUniqueIdentifier());
            var targetOntCat = resolveCategory(ontologyCatMap, sourceMeta.getOntologyCategoryId(), newOntologyCatRoot);
            var newOntologyId = createOntologyInSubspace(spaceId, sourceMeta, targetOntCat);
            ontologyMapping.put(sourceMeta.getUniqueIdentifier(), newOntologyId);

            var sourceProps = propertyMapper.selectList(
                    new LambdaQueryWrapper<OntologyProperty>()
                            .eq(OntologyProperty::getOntologyUniqueIdentifier, sourceMeta.getUniqueIdentifier())
                            .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()));
            var sourcePropMap = sourceProps.stream()
                    .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, v -> v));

            var selectedPropIds = ontologySelection.getSelectedProperties().stream()
                    .map(OntologySubspaceCreateParam.PropertySelection::getSourcePropertyUniqueIdentifier)
                    .collect(Collectors.toList());
            PreconditionUtils.checkArgument(sourcePropMap.keySet().containsAll(selectedPropIds),
                    "选中的属性不存在：" + selectedPropIds, HttpStatus.BAD_REQUEST);

            var newPropRoot = getPropertyCategoryRoot(newOntologyId);
            Map<Integer, Integer> propCatMap = copyPropertyCategoryTree(sourceMeta.getUniqueIdentifier(), newOntologyId);
            for (var propSelection : ontologySelection.getSelectedProperties()) {
                var sourceProp = sourcePropMap.get(propSelection.getSourcePropertyUniqueIdentifier());
                var targetPropCat = resolveCategory(propCatMap, sourceProp.getPropertyCategoryId(), newPropRoot);
                var newPropId = createPropertyInSubspace(newOntologyId, sourceProp, targetPropCat);
                savePropertyFilter(spaceId, newOntologyId, sourceProp.getUniqueIdentifier(), newPropId, propSelection.getFilter());
            }

            // 自动绑定数据源并创建实体表
            ontologyPropertyService.autoBindDatasource(newOntologyId);

            // 导入实例（按显式勾选主键过滤；属性筛选条件已由 savePropertyFilter 作为元数据写入新表）
            importInstances(sourceMeta.getUniqueIdentifier(), newOntologyId,
                    ontologySelection.getSelectedInstancePrimaryKeys());

        }

        // 6. 复制关系
        Map<String, String> linkMapping = new HashMap<>();
        if (CollectionUtils.isNotEmpty(param.getSelectedLinks())) {
            var sourceLinkIds = param.getSelectedLinks().stream()
                    .map(OntologySubspaceCreateParam.LinkSelection::getSourceLinkUniqueIdentifier)
                    .collect(Collectors.toList());
            var sourceLinks = linkMapper.selectList(
                    new LambdaQueryWrapper<OntologyLinkGroup>()
                            .eq(OntologyLinkGroup::getOntologySpaceId, param.getParentSpaceId())
                            .in(OntologyLinkGroup::getUniqueIdentifier, sourceLinkIds)
                            .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue()));
            PreconditionUtils.checkArgument(sourceLinks.size() == sourceLinkIds.size(),
                    "选中的关系不存在或已停用", HttpStatus.BAD_REQUEST);
            var sourceLinkMap = sourceLinks.stream()
                    .collect(Collectors.toMap(OntologyLinkGroup::getUniqueIdentifier, v -> v));

            for (var linkSelection : param.getSelectedLinks()) {
                var sourceLink = sourceLinkMap.get(linkSelection.getSourceLinkUniqueIdentifier());
                var newFromId = ontologyMapping.get(sourceLink.getOntologyUniqueIdentifierFrom());
                var newToId = ontologyMapping.get(sourceLink.getOntologyUniqueIdentifierTo());
                PreconditionUtils.checkArgument(StringUtils.isNotBlank(newFromId) && StringUtils.isNotBlank(newToId),
                        "关系两端对象未被选中：" + sourceLink.getUniqueIdentifier(), HttpStatus.BAD_REQUEST);

                var targetLinkCat = resolveCategory(linkCatMap, sourceLink.getCategoryId(), newLinkCatRoot);
                var newLinkId = createLinkInSubspace(spaceId, sourceLink, newFromId, newToId, linkSelection, targetLinkCat);
                linkMapping.put(sourceLink.getUniqueIdentifier(), newLinkId);
            }
        }

        return OntologySubspaceCreateVO.builder()
                .spaceId(spaceId)
                .ontologyMapping(ontologyMapping)
                .linkMapping(linkMapping)
                .build();
    }

    private void checkSpaceNameConflict(String displayName, String apiName) {
        var exist = spaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getDisplayName, displayName)
                .or().eq(OntologySpace::getApiName, apiName));
        PreconditionUtils.checkArgument(exist == null, "空间显示名称或api名称已存在", HttpStatus.BAD_REQUEST);
    }

    private String createOntologyInSubspace(Integer spaceId, OntologyMeta sourceMeta, Integer targetCategoryId) {
        var metaParam = OntologyMetaCreateParam.builder()
                .displayName(sourceMeta.getDisplayName())
                .apiName(sourceMeta.getApiName())
                .description(sourceMeta.getDescription())
                .iconUrl(sourceMeta.getIcon())
                .categoryId(targetCategoryId)
                .spaceId(spaceId)
                .build();
        return ontologyMetaService.createOntology(metaParam);
    }

    private String createPropertyInSubspace(String ontologyIdentifier, OntologyProperty sourceProp, Integer categoryId) {
        var propertyParam = OntologyPropertyCreateParam.builder()
                .ontologyIdentifier(ontologyIdentifier)
                .displayName(sourceProp.getDisplayName())
                .apiName(sourceProp.getApiName())
                .description(sourceProp.getDescription())
                .dataType(sourceProp.getPropertyType())
                .isPrimaryKey(sourceProp.getIsPrimaryKey() != null && sourceProp.getIsPrimaryKey() == 1)
                .isTitleKey(sourceProp.getIsTitleKey() != null && sourceProp.getIsTitleKey() == 1)
                .defaultValue(sourceProp.getDefaultValue())
                .storageGroup(StringUtils.defaultString(sourceProp.getStorageGroup(), "main"))
                .categoryId(categoryId)
                .build();
        ontologyPropertyService.createProperty(propertyParam);

        var newProp = propertyMapper.selectOne(
                new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                        .eq(OntologyProperty::getApiName, sourceProp.getApiName()));
        PreconditionUtils.checkNotNull(newProp, "属性创建失败：" + sourceProp.getApiName(), HttpStatus.INTERNAL_SERVER_ERROR);
        return newProp.getUniqueIdentifier();
    }

    private void savePropertyFilter(Integer spaceId, String ontologyId, String sourcePropId,
                                    String newPropId, OntologySubspaceCreateParam.PropertyFilterConfig filter) {
        if (filter == null) {
            return;
        }
        var entity = SubspacePropertyFilter.builder()
                .spaceId(spaceId)
                .ontologyUniqueIdentifier(ontologyId)
                .propertyUniqueIdentifier(newPropId)
                .sourcePropertyUniqueIdentifier(sourcePropId)
                .filterOp(filter.getOp() != null ? filter.getOp().name() : null)
                .dataType(filter.getDataType() != null ? filter.getDataType().name() : null)
                .status(Status.ENABLE.getValue())
                .build();
        try {
            Object v = filter.getValue();
            if (v instanceof Collection || v instanceof Object[]) {
                // 多值 op（IN / BETWEEN 等）：前端把数组放进 value，按 JSON 数组存储，如 ["福特","通用"] 或 [10, 20]
                Collection<?> coll = (v instanceof Collection)
                        ? (Collection<?>) v
                        : Arrays.asList((Object[]) v);
                if (!coll.isEmpty()) {
                    entity.setFilterValue(jsonMapper.writeValueAsString(coll));
                }
            } else if (v != null) {
                // 单值 op（EQ / NEQ / LIKE / GT / GE / LT / LE）：直接存原始值，不做 JSON 字符串序列化，
                // 避免对单字符串多包一层双引号（LIKE "驱逐舰" 应存成 驱逐舰 而非 "驱逐舰"）
                entity.setFilterValue(String.valueOf(v));
            }
        } catch (Exception e) {
            throw new BusinessException("筛选条件序列化失败：" + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        subspacePropertyFilterMapper.insert(entity);
    }

    private void importInstances(String sourceOntologyId, String newOntologyId,
                                 List<Object> selectedPrimaryKeys) {
        var exported = entityService.exportInstances(sourceOntologyId);
        if (CollectionUtils.isEmpty(exported.getNodes())) {
            return;
        }
        var nodes = exported.getNodes();


        if (CollectionUtils.isNotEmpty(selectedPrimaryKeys)) {
            var selectedPkSet = selectedPrimaryKeys.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());
            nodes = nodes.stream()
                    .filter(n -> n.getPrimaryKey() != null
                            && selectedPkSet.contains(String.valueOf(n.getPrimaryKey())))
                    .collect(Collectors.toList());
        }

        if (CollectionUtils.isEmpty(nodes)) {
            log.info("子空间实例导入：过滤后无匹配实例，sourceOntologyId={}", sourceOntologyId);
            return;
        }

        var instances = OntologyInstancesExportDTO.builder().nodes(nodes).build();
        entityService.importInstances(newOntologyId, instances);
    }

    private String createLinkInSubspace(Integer spaceId, OntologyLinkGroup sourceLink,
                                        String fromId, String toId,
                                        OntologySubspaceCreateParam.LinkSelection selection,
                                        Integer targetCategoryId) {
        var linkApiName = StringUtils.defaultString(selection.getApiName(), "relation_" + IdGenerator.generateUUID());
        var linkParam = new OntologyLinkCreateParam()
                .setSpaceId(spaceId)
                .setOntologyUniqueIdentifierFrom(fromId)
                .setOntologyUniqueIdentifierTo(toId)
                .setName(StringUtils.defaultString(selection.getName(), sourceLink.getName()))
                .setApiName(linkApiName)
                .setType(selection.getType() != null ? selection.getType() : sourceLink.getType())
                .setDescription(StringUtils.defaultString(selection.getDescription(), sourceLink.getDescription()))
                .setCategoryId(targetCategoryId);
        ontologyLinkGroupService.createLink(linkParam);

        var newLink = linkMapper.selectOne(
                new LambdaQueryWrapper<OntologyLinkGroup>()
                        .eq(OntologyLinkGroup::getOntologySpaceId, spaceId)
                        .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, fromId)
                        .eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, toId)
                        .eq(OntologyLinkGroup::getApiName, linkApiName));
        PreconditionUtils.checkNotNull(newLink, "关系创建失败", HttpStatus.INTERNAL_SERVER_ERROR);
        // 平台 createLink 生成的关系边默认 Status.DELETE（按启用过滤的查询看不到）。
        // 子空间为一次性全量复制，这里将新建关系边置为 ENABLE，使其在子空间中可见可用。
        entityService.activateLinkRelations(newLink.getUniqueIdentifier());
        return newLink.getUniqueIdentifier();
    }

    /**
     * 拷贝父空间的本体分类树（ontology_category，按 spaceId）到子空间。
     * 复用 createSpace 已生成的“全部”根节点，仅复制其下多级子节点，并把 旧分类id -> 新分类id 映射返回。
     */
    private Map<Integer, Integer> copyOntologyCategoryTree(Integer parentSpaceId, Integer newSpaceId) {
        Integer newRootId = getOntologyCategoryRoot(newSpaceId);
        OntologyCategory newRoot = categoryService.getById(newRootId);
        List<OntologyCategory> sources = categoryService.list(
                new LambdaQueryWrapper<OntologyCategory>().eq(OntologyCategory::getOntologySpaceId, parentSpaceId));
        Map<Integer, Integer> map = new HashMap<>();
        OntologyCategory parentRoot = sources.stream().filter(c -> c.getParentId() == 0).findFirst().orElse(null);
        if (parentRoot != null) {
            map.put(parentRoot.getId(), newRootId);
        }
        String parentRootPath = parentRoot != null ? parentRoot.getPath() : "";
        String newRootPath = newRoot != null ? newRoot.getPath() : "";
        Map<Integer, List<OntologyCategory>> byParent = sources.stream()
                .filter(c -> c.getParentId() != 0)
                .collect(Collectors.groupingBy(OntologyCategory::getParentId));
        List<OntologyCategory> current = parentRoot != null
                ? byParent.getOrDefault(parentRoot.getId(), new ArrayList<>())
                : new ArrayList<>();
        while (CollectionUtils.isNotEmpty(current)) {
            List<OntologyCategory> next = new ArrayList<>();
            for (var node : current) {
                Integer newParentId = map.getOrDefault(node.getParentId(), newRootId);
                OntologyCategory clone = OntologyCategory.builder()
                        .ontologySpaceId(newSpaceId)
                        .name(node.getName())
                        .parentId(newParentId)
                        .path(rerootPath(node.getPath(), parentRootPath, newRootPath))
                        .build();
                categoryService.save(clone);
                map.put(node.getId(), clone.getId());
                next.addAll(byParent.getOrDefault(node.getId(), new ArrayList<>()));
            }
            current = next;
        }
        return map;
    }

    /**
     * 拷贝父空间的关系分类树（ontology_link_category，按 spaceId）到子空间，复用已生成的“全部”根节点。
     */
    private Map<Integer, Integer> copyLinkCategoryTree(Integer parentSpaceId, Integer newSpaceId) {
        Integer newRootId = getLinkCategoryRoot(newSpaceId);
        OntologyLinkCategory newRoot = ontologyLinkCategoryService.getById(newRootId);
        List<OntologyLinkCategory> sources = ontologyLinkCategoryService.list(
                new LambdaQueryWrapper<OntologyLinkCategory>().eq(OntologyLinkCategory::getOntologySpaceId, parentSpaceId));
        Map<Integer, Integer> map = new HashMap<>();
        OntologyLinkCategory parentRoot = sources.stream().filter(c -> c.getParentId() == 0).findFirst().orElse(null);
        if (parentRoot != null) {
            map.put(parentRoot.getId(), newRootId);
        }
        String parentRootPath = parentRoot != null ? parentRoot.getPath() : "";
        String newRootPath = newRoot != null ? newRoot.getPath() : "";
        Map<Integer, List<OntologyLinkCategory>> byParent = sources.stream()
                .filter(c -> c.getParentId() != 0)
                .collect(Collectors.groupingBy(OntologyLinkCategory::getParentId));
        List<OntologyLinkCategory> current = parentRoot != null
                ? byParent.getOrDefault(parentRoot.getId(), new ArrayList<>())
                : new ArrayList<>();
        while (CollectionUtils.isNotEmpty(current)) {
            List<OntologyLinkCategory> next = new ArrayList<>();
            for (var node : current) {
                Integer newParentId = map.getOrDefault(node.getParentId(), newRootId);
                OntologyLinkCategory clone = OntologyLinkCategory.builder()
                        .ontologySpaceId(newSpaceId)
                        .name(node.getName())
                        .parentId(newParentId)
                        .path(rerootPath(node.getPath(), parentRootPath, newRootPath))
                        .build();
                ontologyLinkCategoryService.save(clone);
                map.put(node.getId(), clone.getId());
                next.addAll(byParent.getOrDefault(node.getId(), new ArrayList<>()));
            }
            current = next;
        }
        return map;
    }

    /**
     * 拷贝源本体的属性分类树（property_category，按 ontology_unique_identifier）到新本体，复用已生成的“全部”根节点。
     */
    private Map<Integer, Integer> copyPropertyCategoryTree(String sourceOntologyUid, String newOntologyUid) {
        Integer newRootId = getPropertyCategoryRoot(newOntologyUid);
        PropertyCategory newRoot = propertyCategoryService.getById(newRootId);
        List<PropertyCategory> sources = propertyCategoryService.list(
                new LambdaQueryWrapper<PropertyCategory>().eq(PropertyCategory::getOntologyUniqueIdentifier, sourceOntologyUid));
        Map<Integer, Integer> map = new HashMap<>();
        PropertyCategory parentRoot = sources.stream().filter(c -> c.getParentId() == 0).findFirst().orElse(null);
        if (parentRoot != null) {
            map.put(parentRoot.getId(), newRootId);
        }
        String parentRootPath = parentRoot != null ? parentRoot.getPath() : "";
        String newRootPath = newRoot != null ? newRoot.getPath() : "";
        Map<Integer, List<PropertyCategory>> byParent = sources.stream()
                .filter(c -> c.getParentId() != 0)
                .collect(Collectors.groupingBy(PropertyCategory::getParentId));
        List<PropertyCategory> current = parentRoot != null
                ? byParent.getOrDefault(parentRoot.getId(), new ArrayList<>())
                : new ArrayList<>();
        while (CollectionUtils.isNotEmpty(current)) {
            List<PropertyCategory> next = new ArrayList<>();
            for (var node : current) {
                Integer newParentId = map.getOrDefault(node.getParentId(), newRootId);
                PropertyCategory clone = PropertyCategory.builder()
                        .ontologyUniqueIdentifier(newOntologyUid)
                        .name(node.getName())
                        .parentId(newParentId)
                        .path(rerootPath(node.getPath(), parentRootPath, newRootPath))
                        .build();
                propertyCategoryService.save(clone);
                map.put(node.getId(), clone.getId());
                next.addAll(byParent.getOrDefault(node.getId(), new ArrayList<>()));
            }
            current = next;
        }
        return map;
    }

    /**
     * 将源分类id映射到子空间的新分类id；源未分类或映射缺失时回落到对应分类树的根节点。
     */
    private Integer resolveCategory(Map<Integer, Integer> idMap, Integer sourceCategoryId, Integer defaultRootId) {
        if (sourceCategoryId == null) {
            return defaultRootId;
        }
        return idMap.getOrDefault(sourceCategoryId, defaultRootId);
    }

    /**
     * 将源分类节点路径重新挂载到子空间根节点之下，保持原有的多级结构。
     */
    private String rerootPath(String sourcePath, String parentRootPath, String newRootPath) {
        if (sourcePath == null) {
            return null;
        }
        if (StringUtils.isBlank(parentRootPath) || sourcePath.equals(parentRootPath)) {
            return newRootPath;
        }
        if (sourcePath.startsWith(parentRootPath + "/")) {
            return newRootPath + sourcePath.substring(parentRootPath.length());
        }
        return sourcePath;
    }
}