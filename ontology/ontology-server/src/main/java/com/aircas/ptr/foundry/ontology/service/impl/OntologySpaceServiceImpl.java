package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceCreateDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceDTO;
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

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Lazy
    @Resource
    private OntologySpaceServiceImpl proxy;


    @Transactional(transactionManager = "chainedTransactionManager")
    @Override
    public Integer createSpace(OntologySpaceCreateParam param) {
        //check param
        var existByName = spaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getDisplayName, param.getDisplayName()));
        PreconditionUtils.checkArgument(existByName == null, 
                "空间显示名称 '" + param.getDisplayName() + "' 已存在", HttpStatus.BAD_REQUEST);
        var existByApi = spaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getApiName, param.getApiName()));
        PreconditionUtils.checkArgument(existByApi == null, 
                "空间api名称 '" + param.getApiName() + "' 已存在", HttpStatus.BAD_REQUEST);
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
                // required by createProperty, canvas has no such input, use default storage group
                .setStorageGroup("main")
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
                .eq(PropertyCategory::getParentId, 0)
                .last("limit 1"));
        if (existingRoot != null) {
            return existingRoot.getId();
        }
        var categoryParam = PropertyCategoryCreateParam.builder()
                .parentId(0)
                .name("根节点")
                .ontologyIdentifier(ontologyUniqueIdentifier)
                .build();
        ontologyPropertyService.createCategory(categoryParam);
        var created = propertyCategoryService.getOne(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(PropertyCategory::getParentId, 0)
                .orderByDesc(PropertyCategory::getId)
                .last("limit 1"));
        PreconditionUtils.checkNotNull(created, "创建属性分类失败", HttpStatus.INTERNAL_SERVER_ERROR);
        return created.getId();
    }

    /**
     * 创建（或复用）空间的关系分类树根节点，让画布导入的关系都能挂到分类下。
     */
    private Integer createDefaultLinkCategoryRoot(Integer spaceId) {
        var existingRoot = ontologyLinkCategoryService.getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                .eq(OntologyLinkCategory::getParentId, 0)
                .last("limit 1"));
        if (existingRoot != null) {
            return existingRoot.getId();
        }
        var categoryParam = new OntologyLinkCategoryCreateParam()
                .setParentId(0)
                .setName("根节点");
        categoryParam.setSpaceId(spaceId);
        ontologyLinkCategoryService.createCategory(categoryParam);
        var created = ontologyLinkCategoryService.getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                .eq(OntologyLinkCategory::getParentId, 0)
                .orderByDesc(OntologyLinkCategory::getId)
                .last("limit 1"));
        PreconditionUtils.checkNotNull(created, "创建关系分类失败", HttpStatus.INTERNAL_SERVER_ERROR);
        return created.getId();
    }

    /**
     * 创建（或复用）空间的本体分类树根节点，让画布导入的本体都能挂到分类下。
     */
    private Integer createDefaultOntologyCategoryRoot(Integer spaceId) {
        var existingRoot = categoryService.getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .eq(OntologyCategory::getParentId, 0)
                .last("limit 1"));
        if (existingRoot != null) {
            return existingRoot.getId();
        }
        var categoryParam = new OntologyCategoryCreateParam()
                .setParentId(0)
                .setName("根节点");
        categoryParam.setSpaceId(spaceId);
        categoryService.createCategory(categoryParam);
        var created = categoryService.getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .eq(OntologyCategory::getParentId, 0)
                .orderByDesc(OntologyCategory::getId)
                .last("limit 1"));
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
        // 预检查：空间是否已存在（避免调用 createSpace 抛异常后污染外层事务导致 UnexpectedRollbackException）
        var existByName = spaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getDisplayName, ontologySpace.getDisplayName()));
        if (existByName != null) {
            return Lists.newArrayList("空间显示名称 '" + ontologySpace.getDisplayName() + "' 已存在");
        }
        var existByApi = spaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getApiName, ontologySpace.getApiName()));
        if (existByApi != null) {
            return Lists.newArrayList("空间api名称 '" + ontologySpace.getApiName() + "' 已存在");
        }
        Integer spaceId;
        try {
            spaceId = proxy.createSpace(OntologySpaceCreateParam.builder()
                    .apiName(ontologySpace.getApiName())
                    .description(ontologySpace.getDescription())
                    .displayName(ontologySpace.getDisplayName())
                    .build());
        } catch (Exception e) {
            log.error("空间 {} 导入失败", ontologySpace.getDisplayName(), e);
            String reason = e.getMessage();
            if (reason == null && e.getCause() != null) {
                reason = e.getCause().getMessage();
            }
            return Lists.newArrayList("空间 '" + ontologySpace.getDisplayName() + "' 导入失败：" + (reason != null ? reason : "未知错误"));
        }
        //create ontology category
        var category = spaceCreateDTO.getOntologyCategory();
        if (category != null) {
            category.setParentId(0);
            category.setSpaceId(spaceId);
            categoryService.createCategory(category);
        }
        //import ontologies
        List<String> failedOntology = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(spaceCreateDTO.getOntologies())) {
            spaceCreateDTO.getOntologies().forEach(dto -> {
                // 预检查：本体是否已存在（避免 importOntology 抛异常后污染外层事务）
                var existingMeta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, spaceId)
                        .eq(OntologyMeta::getDisplayName, dto.getMetadata().getDisplayName()));
                if (existingMeta != null) {
                    failedOntology.add(dto.getMetadata().getDisplayName() + "：本体 '" + dto.getMetadata().getDisplayName() + "' 在空间下已存在");
                    return;
                }
                try {
                    ontologyMetaService.importOntology(dto);
                } catch (Exception e) {
                    log.error("本体 {} 导入失败", dto.getMetadata().getDisplayName(), e);
                    String reason = e.getMessage();
                    if (reason == null && e.getCause() != null) {
                        reason = e.getCause().getMessage();
                    }
                    failedOntology.add(dto.getMetadata().getDisplayName() + "：" + (reason != null ? reason : "未知错误"));
                }
            });
        }
        return failedOntology;
    }

    @Override
    public OntologySpaceCreateDTO exportOntologySpace(Integer spaceId) {
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
        var ontologies = ontologyMetaService.exportOntologies(space);

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
                .eq(PropertyCategory::getParentId, 0)
                .last("limit 1"));
        PreconditionUtils.checkNotNull(root, "属性分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }

    /**
     * 查询空间的关系分类树根节点（创建空间时已默认生成）。
     */
    private Integer getLinkCategoryRoot(Integer spaceId) {
        var root = ontologyLinkCategoryService.getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                .eq(OntologyLinkCategory::getParentId, 0)
                .last("limit 1"));
        PreconditionUtils.checkNotNull(root, "关系分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }

    /**
     * 查询空间的本体（本地对象）分类树根节点（创建空间时已默认生成）。
     */
    private Integer getOntologyCategoryRoot(Integer spaceId) {
        var root = categoryService.getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .eq(OntologyCategory::getParentId, 0)
                .last("limit 1"));
        PreconditionUtils.checkNotNull(root, "本体分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }
}