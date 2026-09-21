package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceCreateDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCanvasCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.view.OntologyStatisticsCountView;
import com.aircas.ptr.foundry.ontology.model.view.SpaceStatisticsCountView;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceCanvasCreateVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
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

    private final OntologySpaceMapper spaceMapper;

    private final OntologyCategoryMapper categoryMapper;

    private final OntologyCategoryService categoryService;

    private final TableMetadataService tableMetadataService;

    private final OntologyMetaServiceImpl ontologyMetaService;

    private final OntologyPropertyService ontologyPropertyService;

    private final OntologyLinkGroupService ontologyLinkGroupService;

    private final OntologyLinkCategoryMapper ontologyLinkCategoryMapper;

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
        return ontologySpace.getId();
    }

    @Transactional(transactionManager = "chainedTransactionManager", rollbackFor = Exception.class)
    @Override
    public OntologySpaceCanvasCreateVO createSpaceWithCanvasContent(OntologySpaceCanvasCreateParam param) {
        // create space
        var spaceParam = new OntologySpaceCreateParam()
                .setIconUrl(param.getIconUrl())
                .setDisplayName(param.getDisplayName())
                .setDescription(param.getDescription())
                .setApiName(param.getApiName());
        var spaceId = createSpace(spaceParam);

        // create ontologies & properties, record apiName/displayName -> uniqueIdentifier for link resolution
        Map<String, String> uidByApiName = new HashMap<>();
        Map<String, String> uidByDisplayName = new HashMap<>();
        List<OntologySpaceCanvasCreateVO.OntologyItem> ontologyItems = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(param.getOntologies())) {
            for (var canvasOntology : param.getOntologies()) {
                var metaParam = new OntologyMetaCreateParam()
                        .setDisplayName(canvasOntology.getDisplayName())
                        .setApiName(canvasOntology.getApiName())
                        .setDescription(canvasOntology.getDescription())
                        .setIconUrl(canvasOntology.getIconUrl())
                        .setCategoryId(canvasOntology.getCategoryId())
                        .setGroupIds(canvasOntology.getGroupIds());
                metaParam.setSpaceId(spaceId);
                var uniqueIdentifier = ontologyMetaService.createOntology(metaParam);
                uidByApiName.put(canvasOntology.getApiName(), uniqueIdentifier);
                uidByDisplayName.put(canvasOntology.getDisplayName(), uniqueIdentifier);

                if (CollectionUtils.isNotEmpty(canvasOntology.getProperties())) {
                    for (var canvasProperty : canvasOntology.getProperties()) {
                        ontologyPropertyService.createProperty(buildPropertyCreateParam(uniqueIdentifier, canvasProperty));
                    }
                }
                ontologyItems.add(OntologySpaceCanvasCreateVO.OntologyItem.builder()
                        .apiName(canvasOntology.getApiName())
                        .displayName(canvasOntology.getDisplayName())
                        .uniqueIdentifier(uniqueIdentifier)
                        .build());
            }
        }

        // create links; links without categoryId go to a lazily created default category of this space
        if (CollectionUtils.isNotEmpty(param.getLinks())) {
            Integer defaultCategoryId = null;
            for (var canvasLink : param.getLinks()) {
                var fromUid = resolveOntologyUid(canvasLink.getFromOntologyApiName(), uidByApiName, uidByDisplayName);
                var toUid = resolveOntologyUid(canvasLink.getToOntologyApiName(), uidByApiName, uidByDisplayName);
                var categoryId = canvasLink.getCategoryId();
                if (categoryId == null) {
                    if (defaultCategoryId == null) {
                        defaultCategoryId = createDefaultLinkCategory(spaceId);
                    }
                    categoryId = defaultCategoryId;
                }
                var linkParam = new OntologyLinkCreateParam()
                        .setName(canvasLink.getName())
                        .setOntologyUniqueIdentifierFrom(fromUid)
                        .setOntologyUniqueIdentifierTo(toUid)
                        .setType(resolveLinkType(canvasLink.getType()))
                        .setCategoryId(categoryId);
                ontologyLinkGroupService.createLink(linkParam);
            }
        }

        return OntologySpaceCanvasCreateVO.builder()
                .spaceId(spaceId)
                .ontologies(ontologyItems)
                .build();
    }

    private OntologyPropertyCreateParam buildPropertyCreateParam(String ontologyUniqueIdentifier,
                                                                 OntologySpaceCanvasCreateParam.CanvasProperty canvasProperty) {
        var propertyParam = new OntologyPropertyCreateParam()
                .setDisplayName(canvasProperty.getDisplayName())
                .setApiName(canvasProperty.getApiName())
                .setDataType(resolveDataType(canvasProperty.getDataType()))
                .setDescription(canvasProperty.getDescription())
                .setIsPrimaryKey(Boolean.TRUE.equals(canvasProperty.getIsPrimaryKey()))
                .setIsTitleKey(Boolean.TRUE.equals(canvasProperty.getIsTitleKey()))
                .setDefaultValue(canvasProperty.getDefaultValue())
                .setCategoryId(canvasProperty.getCategoryId())
                // required by createProperty, canvas has no such input, use default storage group
                .setStorageGroup("main");
        propertyParam.setOntologyIdentifier(ontologyUniqueIdentifier);
        return propertyParam;
    }

    private OntologyDataTypeEnum resolveDataType(String dataType) {
        if (StringUtils.isBlank(dataType)) {
            return OntologyDataTypeEnum.String;
        }
        try {
            return OntologyDataTypeEnum.valueOf(dataType.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的数据类型：" + dataType, HttpStatus.BAD_REQUEST);
        }
    }

    private OntologyLinkTypeEnum resolveLinkType(String type) {
        if (StringUtils.isBlank(type)) {
            return OntologyLinkTypeEnum.OTHER;
        }
        try {
            return OntologyLinkTypeEnum.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的关系类型：" + type, HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveOntologyUid(String apiNameOrDisplayName, Map<String, String> uidByApiName, Map<String, String> uidByDisplayName) {
        var uid = uidByApiName.get(apiNameOrDisplayName);
        if (uid == null) {
            uid = uidByDisplayName.get(apiNameOrDisplayName);
        }
        PreconditionUtils.checkArgument(uid != null, "画布中不存在对象：" + apiNameOrDisplayName, HttpStatus.BAD_REQUEST);
        return uid;
    }

    private Integer createDefaultLinkCategory(Integer spaceId) {
        var category = OntologyLinkCategory.builder()
                .ontologySpaceId(spaceId)
                .name("默认分类")
                .parentId(0)
                .path("默认分类")
                .build();
        ontologyLinkCategoryMapper.insert(category);
        return category.getId();
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

        //一次装配本体与空间级函数，复用已加载的 space，共享函数详情缓存
        var exportData = ontologyMetaService.exportOntologiesWithFunctions(space);

        return OntologySpaceCreateDTO.builder()
                .ontologySpace(ontologySpace)
                .ontologyCategory(ontologyCategory)
                .functions(exportData.getFunctions())
                .ontologies(exportData.getOntologies())
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


}