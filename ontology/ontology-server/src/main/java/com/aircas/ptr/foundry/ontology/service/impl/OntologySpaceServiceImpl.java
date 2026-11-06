package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.context.UserContextHolder;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyCreateDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceCreateDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.SpaceTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCanvasCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleTask;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.po.ActionCategory;
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
import com.aircas.ptr.foundry.ontology.service.ActionCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.service.PropertyCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
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

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.dto.EntityNodeExportDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyInstancesExportDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyLinkDTO;
import com.aircas.ptr.foundry.ontology.model.enums.QueryOpEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologySubspaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.SubspacePropertyFilter;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySubspaceCreateVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.SubspacePropertyFilterMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;


@Service
@RequiredArgsConstructor
@Slf4j
public class OntologySpaceServiceImpl extends ServiceImpl<OntologySpaceMapper, OntologySpace> implements OntologySpaceService {

    // 管理员可查看全部主题，不按创建人过滤
    private static final Set<String> TOPIC_ADMIN_USERNAMES = Set.of("admin", "admin1");

    private static final String CONFLICT_TYPE_ONTOLOGY = "ONTOLOGY";
    private static final String CONFLICT_TYPE_PROPERTY = "PROPERTY";
    private static final String CONFLICT_TYPE_LINK = "LINK";
    private static final String REASON_FIELD_CONFLICT = "FIELD_CONFLICT";
    private static final String REASON_DELETED_BY_OTHERS = "DELETED_BY_OTHERS";
    private static final String REASON_DELETE_CONFLICT = "DELETE_CONFLICT";
    private static final String REASON_NAME_CONFLICT = "NAME_CONFLICT";
    private static final String REASON_API_NAME_IMMUTABLE = "API_NAME_IMMUTABLE";
    private static final String REASON_ENDPOINT_IMMUTABLE = "ENDPOINT_IMMUTABLE";
    private static final String REASON_INVALID_BASE = "INVALID_BASE";
    private static final String REASON_PROPERTY_DELETE_FORBIDDEN = "PROPERTY_DELETE_FORBIDDEN";
    private static final String REASON_PARENT_DELETED = "PARENT_DELETED";

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

    private final ActionCategoryService actionCategoryService;

    private final EntityService entityService;

    private final SubspacePropertyFilterMapper subspacePropertyFilterMapper;

    // 导入的是历史导出文件，字段会随版本增删，未知字段一律忽略
    private final ObjectMapper jsonMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Lazy
    @Resource
    private OntologySpaceServiceImpl proxy;


    @Transactional(transactionManager = "chainedTransactionManager")
    @Override
    public Integer createSpace(OntologySpaceCreateParam param) {
        return doCreateSpace(param, SpaceTypeEnum.SPACE.getValue());
    }

    /**
     * 创建空间/子空间公共流程：根据 type 写入类型（0=空间，1=主题/子空间），并记录创建用户名。
     */
    private Integer doCreateSpace(OntologySpaceCreateParam param, Integer type) {
        //check param
        var space = spaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getDisplayName, param.getDisplayName())
                .or().eq(OntologySpace::getApiName, param.getApiName()));
        PreconditionUtils.checkArgument(space == null, "空间显示名称或api名称已存在", HttpStatus.BAD_REQUEST);
        //create space
        var userContext = UserContextHolder.get();
        var ontologySpace = OntologySpace.builder()
                .description(param.getDescription())
                .icon(param.getIconUrl())
                .displayName(param.getDisplayName())
                .apiName(param.getApiName())
                .username(userContext == null ? null : userContext.getUsername())
                .type(type)
                .build();
        spaceMapper.insert(ontologySpace);
        //create new schema &  table_filed_mapping table
        tableMetadataService.initSpaceSchema(param.getApiName());
        // 默认创建对象（本地对象）分类树根节点
        createDefaultOntologyCategoryRoot(ontologySpace.getId());
        // 默认创建关系分类树根节点
        createDefaultLinkCategoryRoot(ontologySpace.getId());
        // 默认创建行为分类树根节点：行为分类是创建行为的必填项，没有根节点则本空间无法创建任何行为
        createDefaultActionCategoryRoot(ontologySpace.getId());
        return ontologySpace.getId();
    }

    @Transactional(transactionManager = "chainedTransactionManager", rollbackFor = Exception.class)
    @Override
    public OntologySpaceCanvasCreateVO createSpaceWithCanvasContent(OntologySpaceCanvasCreateParam param) {
        Integer spaceId = param.getSpaceId();
        PreconditionUtils.checkNotNull(spaceId, "spaceId is empty", HttpStatus.BAD_REQUEST);
        // 锁空间行，让同一空间的画布保存串行执行，避免两个请求同时基于旧快照互相覆盖
        var space = spaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getId, spaceId)
                .last("FOR UPDATE"));
        PreconditionUtils.checkNotNull(space, "空间id不存在", HttpStatus.BAD_REQUEST);

        // ===== 数据库现状 =====
        var dbMetas = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId)
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .orderByAsc(OntologyMeta::getId));
        Map<String, OntologyMeta> dbMetaByUid = dbMetas.stream()
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, v -> v, (a, b) -> a));
        List<OntologyProperty> dbProperties = dbMetas.isEmpty() ? Lists.newArrayList()
                : propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .in(OntologyProperty::getOntologyUniqueIdentifier, dbMetaByUid.keySet())
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue())
                .orderByAsc(OntologyProperty::getId));
        Map<String, OntologyProperty> dbPropertyByUid = dbProperties.stream()
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, v -> v, (a, b) -> a));
        var dbLinks = linkMapper.selectList(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologySpaceId, spaceId)
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue())
                .orderByAsc(OntologyLinkGroup::getId));
        Map<String, OntologyLinkGroup> dbLinkByUid = dbLinks.stream()
                .collect(Collectors.toMap(OntologyLinkGroup::getUniqueIdentifier, v -> v, (a, b) -> a));

        var structuralConflicts = new ArrayList<OntologySpaceCanvasCreateVO.CanvasConflict>();
        var fieldConflicts = new ArrayList<OntologySpaceCanvasCreateVO.CanvasConflict>();
        var base = param.getBase();
        boolean createOnly = base == null;
        // base 模式下 current 列表是"完整画布"，缺字段会被误判成全删，直接当结构错误拒绝
        if (!createOnly && (param.getOntologies() == null || param.getLinks() == null)) {
            addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, null, null, null, null,
                    REASON_INVALID_BASE, "带 base 保存时必须同时传完整的 ontologies 和 links 列表");
        }

        // ===== base / current 结构校验 =====
        Map<String, OntologySpaceCanvasCreateParam.CanvasOntology> baseOntologyByUid = new LinkedHashMap<>();
        Map<String, CanvasPropertyRef> basePropertyByUid = new LinkedHashMap<>();
        Map<String, OntologySpaceCanvasCreateParam.CanvasLink> baseLinkByUid = new LinkedHashMap<>();
        if (base != null) {
            for (var baseOntology : safeList(base.getOntologies())) {
                String uid = baseOntology.getUniqueIdentifier();
                if (StringUtils.isBlank(uid)) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, null,
                            baseOntology.getApiName(), baseOntology.getDisplayName(), null,
                            REASON_INVALID_BASE, "base 中的对象缺少 uniqueIdentifier");
                    continue;
                }
                if (baseOntologyByUid.put(uid, baseOntology) != null) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, uid,
                            baseOntology.getApiName(), baseOntology.getDisplayName(), null,
                            REASON_INVALID_BASE, "base 中对象 uniqueIdentifier 重复：" + uid);
                }
                for (var baseProperty : safeList(baseOntology.getProperties())) {
                    String propertyUid = baseProperty.getUniqueIdentifier();
                    if (StringUtils.isBlank(propertyUid)) {
                        addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, null,
                                baseProperty.getApiName(), baseProperty.getDisplayName(), uid,
                                REASON_INVALID_BASE, "base 中的属性缺少 uniqueIdentifier");
                        continue;
                    }
                    if (basePropertyByUid.put(propertyUid, new CanvasPropertyRef(uid, baseProperty)) != null) {
                        addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, propertyUid,
                                baseProperty.getApiName(), baseProperty.getDisplayName(), uid,
                                REASON_INVALID_BASE, "base 中属性 uniqueIdentifier 重复：" + propertyUid);
                    }
                }
            }
            for (var baseLink : safeList(base.getLinks())) {
                String uid = baseLink.getUniqueIdentifier();
                if (StringUtils.isBlank(uid)) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_LINK, null,
                            baseLink.getApiName(), baseLink.getName(), null,
                            REASON_INVALID_BASE, "base 中的关系缺少 uniqueIdentifier");
                    continue;
                }
                if (baseLinkByUid.put(uid, baseLink) != null) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_LINK, uid,
                            baseLink.getApiName(), baseLink.getName(), null,
                            REASON_INVALID_BASE, "base 中关系 uniqueIdentifier 重复：" + uid);
                }
            }
        }

        Map<String, OntologySpaceCanvasCreateParam.CanvasOntology> currentOntologyByUid = new LinkedHashMap<>();
        Map<String, CanvasPropertyRef> currentPropertyByUid = new LinkedHashMap<>();
        Map<String, OntologySpaceCanvasCreateParam.CanvasLink> currentLinkByUid = new LinkedHashMap<>();
        var ontologyCreates = new ArrayList<OntologySpaceCanvasCreateParam.CanvasOntology>();
        var propertyCreates = new ArrayList<CanvasPropertyCreate>();
        var linkCreates = new ArrayList<OntologySpaceCanvasCreateParam.CanvasLink>();

        for (var ontology : safeList(param.getOntologies())) {
            String uid = ontology.getUniqueIdentifier();
            if (StringUtils.isBlank(uid)) {
                ontologyCreates.add(ontology);
                // 空串同样是"新增"：统一成 null，后续按 ownerOntologyUid == null 判断归属
                uid = null;
            } else if (createOnly) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, uid,
                        ontology.getApiName(), ontology.getDisplayName(), null,
                        REASON_INVALID_BASE, "不传 base 时不能带 uniqueIdentifier");
            } else if (!baseOntologyByUid.containsKey(uid)) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, uid,
                        ontology.getApiName(), ontology.getDisplayName(), null,
                        REASON_INVALID_BASE, "对象不在 base 中：" + uid);
            } else if (currentOntologyByUid.put(uid, ontology) != null) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, uid,
                        ontology.getApiName(), ontology.getDisplayName(), null,
                        REASON_INVALID_BASE, "对象 uniqueIdentifier 重复：" + uid);
            }
            for (var property : safeList(ontology.getProperties())) {
                String propertyUid = property.getUniqueIdentifier();
                if (StringUtils.isBlank(propertyUid)) {
                    propertyCreates.add(new CanvasPropertyCreate(uid, ontology, property));
                    continue;
                }
                if (createOnly) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, propertyUid,
                            property.getApiName(), property.getDisplayName(), uid,
                            REASON_INVALID_BASE, "不传 base 时不能带 uniqueIdentifier");
                    continue;
                }
                var baseRef = basePropertyByUid.get(propertyUid);
                if (baseRef == null) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, propertyUid,
                            property.getApiName(), property.getDisplayName(), uid,
                            REASON_INVALID_BASE, "属性不在 base 中：" + propertyUid);
                    continue;
                }
                if (uid == null || !Objects.equals(baseRef.ownerOntologyUid, uid)) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, propertyUid,
                            property.getApiName(), property.getDisplayName(), uid,
                            REASON_INVALID_BASE, "属性不能移动到其他对象下：" + propertyUid);
                    continue;
                }
                if (currentPropertyByUid.put(propertyUid, new CanvasPropertyRef(uid, property)) != null) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, propertyUid,
                            property.getApiName(), property.getDisplayName(), uid,
                            REASON_INVALID_BASE, "属性 uniqueIdentifier 重复：" + propertyUid);
                }
            }
        }

        for (var link : safeList(param.getLinks())) {
            String uid = link.getUniqueIdentifier();
            if (StringUtils.isBlank(uid)) {
                linkCreates.add(link);
                continue;
            }
            if (createOnly) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_LINK, uid,
                        link.getApiName(), link.getName(), null,
                        REASON_INVALID_BASE, "不传 base 时不能带 uniqueIdentifier");
                continue;
            }
            if (!baseLinkByUid.containsKey(uid)) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_LINK, uid,
                        link.getApiName(), link.getName(), null,
                        REASON_INVALID_BASE, "关系不在 base 中：" + uid);
                continue;
            }
            if (currentLinkByUid.put(uid, link) != null) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_LINK, uid,
                        link.getApiName(), link.getName(), null,
                        REASON_INVALID_BASE, "关系 uniqueIdentifier 重复：" + uid);
            }
        }

        // ===== 对象 diff =====
        var ontologyUpdates = new ArrayList<CanvasOntologyUpdate>();
        Map<String, CanvasOntologyUpdate> ontologyUpdateByUid = new LinkedHashMap<>();
        var ontologyDeletes = new LinkedHashSet<String>();
        for (var current : currentOntologyByUid.values()) {
            String uid = current.getUniqueIdentifier();
            var baseOntology = baseOntologyByUid.get(uid);
            var db = dbMetaByUid.get(uid);
            if (db == null) {
                if (ontologyUserEdited(current, baseOntology)) {
                    addFieldConflict(fieldConflicts, CONFLICT_TYPE_ONTOLOGY, uid, current.getApiName(),
                            current.getDisplayName(), null, REASON_DELETED_BY_OTHERS,
                            "该对象已被他人删除，本次修改未保存");
                }
                continue;
            }
            if (!Objects.equals(current.getApiName(), db.getApiName())) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, uid, current.getApiName(),
                        current.getDisplayName(), null, REASON_API_NAME_IMMUTABLE,
                        "对象 apiName 不允许修改，请删除后重建");
                continue;
            }
            var merge = mergeOntology(baseOntology, current, db);
            if (!merge.conflicts.isEmpty()) {
                fieldConflicts.add(merge.toConflict(CONFLICT_TYPE_ONTOLOGY, uid,
                        current.getApiName(), current.getDisplayName(), null));
            }
            if (!merge.writes.isEmpty()) {
                var update = new CanvasOntologyUpdate(uid, merge.writes);
                ontologyUpdates.add(update);
                ontologyUpdateByUid.put(uid, update);
            }
        }
        for (var entry : baseOntologyByUid.entrySet()) {
            String uid = entry.getKey();
            if (currentOntologyByUid.containsKey(uid)) {
                continue;
            }
            var db = dbMetaByUid.get(uid);
            if (db == null) {
                continue;
            }
            if (ontologyDbChanged(db, entry.getValue())) {
                addFieldConflict(fieldConflicts, CONFLICT_TYPE_ONTOLOGY, uid, db.getApiName(),
                        db.getDisplayName(), null, REASON_DELETE_CONFLICT,
                        "该对象在加载后已被他人修改，本次删除未执行");
                continue;
            }
            ontologyDeletes.add(uid);
        }

        // ===== 属性 diff =====
        var propertyDeletes = new LinkedHashSet<String>();
        var propertyUpdates = new ArrayList<CanvasPropertyUpdate>();
        Map<String, CanvasPropertyUpdate> propertyUpdateByUid = new LinkedHashMap<>();
        Map<String, List<OntologySpaceCanvasCreateParam.CanvasProperty>> currentPropertiesByOwner = new HashMap<>();
        for (var currentOntology : currentOntologyByUid.values()) {
            currentPropertiesByOwner.put(currentOntology.getUniqueIdentifier(),
                    safeList(currentOntology.getProperties()));
        }
        for (var currentOntology : safeList(param.getOntologies())) {
            String ownerUid = currentOntology.getUniqueIdentifier();
            var baseOntology = ownerUid == null ? null : baseOntologyByUid.get(ownerUid);
            if (baseOntology == null) {
                continue;
            }
            Set<String> currentPropertyUids = safeList(currentOntology.getProperties()).stream()
                    .map(OntologySpaceCanvasCreateParam.CanvasProperty::getUniqueIdentifier)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
            for (var baseProperty : safeList(baseOntology.getProperties())) {
                String propertyUid = baseProperty.getUniqueIdentifier();
                if (StringUtils.isBlank(propertyUid) || currentPropertyUids.contains(propertyUid)) {
                    continue;
                }
                var dbProperty = dbPropertyByUid.get(propertyUid);
                if (dbProperty == null) {
                    continue;
                }
                if (propertyDbChanged(dbProperty, baseProperty)) {
                    addFieldConflict(fieldConflicts, CONFLICT_TYPE_PROPERTY, propertyUid,
                            dbProperty.getApiName(), dbProperty.getDisplayName(), ownerUid,
                            REASON_DELETE_CONFLICT, "该属性在加载后已被他人修改，本次删除未执行");
                    continue;
                }
                // 同一保存里已有别的属性接手主键/标题键时允许删除（落库前先清旧标记再删）
                var ownerProperties = currentPropertiesByOwner.get(ownerUid);
                boolean primaryKeyLeftUncovered = isOne(dbProperty.getIsPrimaryKey())
                        && !hasFinalKey(ownerUid, ownerProperties, basePropertyByUid, true);
                boolean titleKeyLeftUncovered = isOne(dbProperty.getIsTitleKey())
                        && !hasFinalKey(ownerUid, ownerProperties, basePropertyByUid, false);
                if (primaryKeyLeftUncovered || titleKeyLeftUncovered) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, propertyUid,
                            dbProperty.getApiName(), dbProperty.getDisplayName(), ownerUid,
                            REASON_PROPERTY_DELETE_FORBIDDEN, "主键和标题键属性不能删除");
                    continue;
                }
                propertyDeletes.add(propertyUid);
            }
        }

        for (var currentOntology : safeList(param.getOntologies())) {
            String ownerUid = StringUtils.isBlank(currentOntology.getUniqueIdentifier())
                    ? null : currentOntology.getUniqueIdentifier();
            boolean ownerIsNew = ownerUid == null;
            boolean ownerDeletedByOthers = !ownerIsNew && !dbMetaByUid.containsKey(ownerUid);
            for (var currentProperty : safeList(currentOntology.getProperties())) {
                String propertyUid = currentProperty.getUniqueIdentifier();
                if (StringUtils.isBlank(propertyUid)) {
                    if (ownerDeletedByOthers) {
                        addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, null,
                                currentProperty.getApiName(), currentProperty.getDisplayName(), ownerUid,
                                REASON_PARENT_DELETED, "属性所属对象已被他人删除，本次新增未执行");
                    }
                    continue;
                }
                var baseRef = basePropertyByUid.get(propertyUid);
                if (baseRef == null) {
                    // 结构校验阶段已记录 INVALID_BASE
                    continue;
                }
                var dbProperty = dbPropertyByUid.get(propertyUid);
                if (dbProperty == null) {
                    if (propertyUserEdited(currentProperty, baseRef.property)) {
                        addFieldConflict(fieldConflicts, CONFLICT_TYPE_PROPERTY, propertyUid,
                                currentProperty.getApiName(), currentProperty.getDisplayName(), ownerUid,
                                REASON_DELETED_BY_OTHERS, "该属性已被他人删除，本次修改未保存");
                    }
                    continue;
                }
                if (!Objects.equals(currentProperty.getApiName(), dbProperty.getApiName())) {
                    addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, propertyUid,
                            currentProperty.getApiName(), currentProperty.getDisplayName(), ownerUid,
                            REASON_API_NAME_IMMUTABLE, "属性 apiName 不允许修改，请删除后重建");
                    continue;
                }
                var merge = mergeProperty(baseRef.property, currentProperty, dbProperty);
                if (!merge.conflicts.isEmpty()) {
                    fieldConflicts.add(merge.toConflict(CONFLICT_TYPE_PROPERTY, propertyUid,
                            currentProperty.getApiName(), currentProperty.getDisplayName(), ownerUid));
                }
                if (!merge.writes.isEmpty()) {
                    var update = new CanvasPropertyUpdate(propertyUid, dbProperty, merge.writes);
                    propertyUpdates.add(update);
                    propertyUpdateByUid.put(propertyUid, update);
                }
            }
        }

        // ===== 最终存活集合的唯一性校验 =====
        Set<String> finalApiNames = new LinkedHashSet<>();
        Map<String, String> displayNameToFinalApiName = new LinkedHashMap<>();
        Set<String> ontologyApiNames = new HashSet<>();
        Set<String> ontologyDisplayNames = new HashSet<>();
        for (var db : dbMetas) {
            String uid = db.getUniqueIdentifier();
            if (ontologyDeletes.contains(uid)) {
                continue;
            }
            var update = ontologyUpdateByUid.get(uid);
            String displayName = update != null && update.writes.containsKey("displayName")
                    ? (String) update.writes.get("displayName") : db.getDisplayName();
            finalApiNames.add(db.getApiName());
            displayNameToFinalApiName.put(displayName, db.getApiName());
            if (!ontologyApiNames.add(db.getApiName())) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, uid, db.getApiName(),
                        displayName, null, REASON_NAME_CONFLICT, "对象 apiName 重复：" + db.getApiName());
            }
            if (!ontologyDisplayNames.add(displayName)) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, uid, db.getApiName(),
                        displayName, null, REASON_NAME_CONFLICT, "对象名称重复：" + displayName);
            }
        }
        for (var create : ontologyCreates) {
            finalApiNames.add(create.getApiName());
            displayNameToFinalApiName.put(create.getDisplayName(), create.getApiName());
            if (!ontologyApiNames.add(create.getApiName())) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, null, create.getApiName(),
                        create.getDisplayName(), null, REASON_NAME_CONFLICT,
                        "对象 apiName 重复：" + create.getApiName());
            }
            if (!ontologyDisplayNames.add(create.getDisplayName())) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_ONTOLOGY, null, create.getApiName(),
                        create.getDisplayName(), null, REASON_NAME_CONFLICT,
                        "对象名称重复：" + create.getDisplayName());
            }
        }

        Map<OntologySpaceCanvasCreateParam.CanvasOntology, String> newOntologyKeys = new IdentityHashMap<>();
        for (int i = 0; i < ontologyCreates.size(); i++) {
            newOntologyKeys.put(ontologyCreates.get(i), "new#" + i);
        }
        Map<String, Set<String>> propertyApiNames = new HashMap<>();
        Map<String, Set<String>> propertyDisplayNames = new HashMap<>();
        for (var dbProperty : dbProperties) {
            String ownerUid = dbProperty.getOntologyUniqueIdentifier();
            if (ontologyDeletes.contains(ownerUid) || propertyDeletes.contains(dbProperty.getUniqueIdentifier())) {
                continue;
            }
            var update = propertyUpdateByUid.get(dbProperty.getUniqueIdentifier());
            String displayName = update != null && update.writes.containsKey("displayName")
                    ? (String) update.writes.get("displayName") : dbProperty.getDisplayName();
            if (!propertyApiNames.computeIfAbsent(ownerUid, k -> new HashSet<>()).add(dbProperty.getApiName())) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, dbProperty.getUniqueIdentifier(),
                        dbProperty.getApiName(), displayName, ownerUid, REASON_NAME_CONFLICT,
                        "属性 apiName 重复：" + dbProperty.getApiName());
            }
            if (!propertyDisplayNames.computeIfAbsent(ownerUid, k -> new HashSet<>()).add(displayName)) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, dbProperty.getUniqueIdentifier(),
                        dbProperty.getApiName(), displayName, ownerUid, REASON_NAME_CONFLICT,
                        "属性名称重复：" + displayName);
            }
        }
        for (var create : propertyCreates) {
            String ownerKey = create.ownerOntologyUid != null
                    ? create.ownerOntologyUid : newOntologyKeys.get(create.ownerOntology);
            if (!propertyApiNames.computeIfAbsent(ownerKey, k -> new HashSet<>()).add(create.property.getApiName())) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, null,
                        create.property.getApiName(), create.property.getDisplayName(),
                        create.ownerOntologyUid, REASON_NAME_CONFLICT,
                        "属性 apiName 重复：" + create.property.getApiName());
            }
            if (!propertyDisplayNames.computeIfAbsent(ownerKey, k -> new HashSet<>())
                    .add(create.property.getDisplayName())) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_PROPERTY, null,
                        create.property.getApiName(), create.property.getDisplayName(),
                        create.ownerOntologyUid, REASON_NAME_CONFLICT,
                        "属性名称重复：" + create.property.getDisplayName());
            }
        }

        // ===== 关系 diff =====
        var linkDeletes = new LinkedHashSet<String>();
        var linkUpdates = new ArrayList<CanvasLinkUpdate>();
        var newLinkCreates = new ArrayList<CanvasLinkCreate>();
        for (var current : currentLinkByUid.values()) {
            String uid = current.getUniqueIdentifier();
            var baseLink = baseLinkByUid.get(uid);
            var dbLink = dbLinkByUid.get(uid);
            if (dbLink == null) {
                if (linkUserEdited(current, baseLink)) {
                    addFieldConflict(fieldConflicts, CONFLICT_TYPE_LINK, uid, current.getApiName(),
                            current.getName(), null, REASON_DELETED_BY_OTHERS,
                            "该关系已被他人删除，本次修改未保存");
                }
                continue;
            }
            if (ontologyDeletes.contains(dbLink.getOntologyUniqueIdentifierFrom())
                    || ontologyDeletes.contains(dbLink.getOntologyUniqueIdentifierTo())) {
                continue;
            }
            String fromApiName = resolveFinalApiName(current.getFromOntologyApiName(),
                    finalApiNames, displayNameToFinalApiName);
            String toApiName = resolveFinalApiName(current.getToOntologyApiName(),
                    finalApiNames, displayNameToFinalApiName);
            if (fromApiName == null || toApiName == null) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_LINK, uid, current.getApiName(),
                        current.getName(), null, REASON_PARENT_DELETED, "关系端点对象不存在或已被删除");
                continue;
            }
            var dbFrom = dbMetaByUid.get(dbLink.getOntologyUniqueIdentifierFrom());
            var dbTo = dbMetaByUid.get(dbLink.getOntologyUniqueIdentifierTo());
            if (!Objects.equals(fromApiName, dbFrom == null ? null : dbFrom.getApiName())
                    || !Objects.equals(toApiName, dbTo == null ? null : dbTo.getApiName())) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_LINK, uid, current.getApiName(),
                        current.getName(), null, REASON_ENDPOINT_IMMUTABLE,
                        "关系两端对象不允许修改，请删除后重建");
                continue;
            }
            var merge = mergeLink(baseLink, current, dbLink);
            if (!merge.conflicts.isEmpty()) {
                fieldConflicts.add(merge.toConflict(CONFLICT_TYPE_LINK, uid,
                        current.getApiName(), current.getName(), null));
            }
            if (!merge.writes.isEmpty()) {
                linkUpdates.add(new CanvasLinkUpdate(uid, merge.writes));
            }
        }
        for (var entry : baseLinkByUid.entrySet()) {
            String uid = entry.getKey();
            if (currentLinkByUid.containsKey(uid)) {
                continue;
            }
            var dbLink = dbLinkByUid.get(uid);
            if (dbLink == null) {
                continue;
            }
            if (ontologyDeletes.contains(dbLink.getOntologyUniqueIdentifierFrom())
                    || ontologyDeletes.contains(dbLink.getOntologyUniqueIdentifierTo())) {
                continue;
            }
            if (linkDbChanged(dbLink, entry.getValue())) {
                addFieldConflict(fieldConflicts, CONFLICT_TYPE_LINK, uid, dbLink.getApiName(),
                        dbLink.getName(), null, REASON_DELETE_CONFLICT,
                        "该关系在加载后已被他人修改，本次删除未执行");
                continue;
            }
            linkDeletes.add(uid);
        }
        for (var create : linkCreates) {
            String fromApiName = resolveFinalApiName(create.getFromOntologyApiName(),
                    finalApiNames, displayNameToFinalApiName);
            String toApiName = resolveFinalApiName(create.getToOntologyApiName(),
                    finalApiNames, displayNameToFinalApiName);
            if (fromApiName == null || toApiName == null) {
                addStructuralConflict(structuralConflicts, CONFLICT_TYPE_LINK, null, create.getApiName(),
                        create.getName(), null, REASON_PARENT_DELETED, "关系端点对象不存在或已被删除");
                continue;
            }
            newLinkCreates.add(new CanvasLinkCreate(create, fromApiName, toApiName));
        }

        // ===== 结构性冲突：整单不写 =====
        var allConflicts = new ArrayList<OntologySpaceCanvasCreateVO.CanvasConflict>();
        allConflicts.addAll(structuralConflicts);
        allConflicts.addAll(fieldConflicts);
        if (!structuralConflicts.isEmpty()) {
            return OntologySpaceCanvasCreateVO.builder()
                    .spaceId(spaceId)
                    .committed(false)
                    .saved(zeroSummary())
                    .conflicts(allConflicts)
                    .canvas(getSpaceCanvasContent(spaceId))
                    .build();
        }

        // ===== 落库：先删后建，最后建关系 =====
        // deleteOntology 会级联删属性/关系，这里把它们也算进本次删除数量
        var summary = zeroSummary()
                .setDeletedPropertyCount((int) dbProperties.stream()
                        .filter(p -> ontologyDeletes.contains(p.getOntologyUniqueIdentifier())).count())
                .setDeletedLinkCount((int) dbLinks.stream()
                        .filter(l -> ontologyDeletes.contains(l.getOntologyUniqueIdentifierFrom())
                                || ontologyDeletes.contains(l.getOntologyUniqueIdentifierTo())).count());
        for (String uid : linkDeletes) {
            ontologyLinkGroupService.deleteLinkByLinkUniqueIdentifier(uid);
            summary.setDeletedLinkCount(summary.getDeletedLinkCount() + 1);
        }
        for (String uid : propertyDeletes) {
            clearPropertyKeysBeforeDelete(dbPropertyByUid.get(uid));
            ontologyPropertyService.deleteProperty(uid);
            summary.setDeletedPropertyCount(summary.getDeletedPropertyCount() + 1);
        }
        for (String uid : ontologyDeletes) {
            ontologyMetaService.deleteOntology(uid);
            summary.setDeletedOntologyCount(summary.getDeletedOntologyCount() + 1);
        }
        // 对象改名放在创建之前，避免"把 A 改名释放名称、再新建同名对象"被唯一索引拦下
        for (var update : ontologyUpdates) {
            var wrapper = new LambdaUpdateWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, update.uid)
                    .set(OntologyMeta::getUpdateTime, new Date());
            if (update.writes.containsKey("displayName")) {
                wrapper.set(OntologyMeta::getDisplayName, update.writes.get("displayName"));
            }
            if (update.writes.containsKey("description")) {
                wrapper.set(OntologyMeta::getDescription, update.writes.get("description"));
            }
            if (update.writes.containsKey("iconUrl")) {
                wrapper.set(OntologyMeta::getIcon, update.writes.get("iconUrl"));
            }
            metaMapper.update(null, wrapper);
            summary.setUpdatedOntologyCount(summary.getUpdatedOntologyCount() + 1);
        }

        Integer defaultOntologyCategoryId = ontologyCreates.isEmpty() ? null : getOntologyCategoryRoot(spaceId);
        Map<OntologySpaceCanvasCreateParam.CanvasOntology, String> createdOntologyUid = new IdentityHashMap<>();
        Map<String, String> uidByFinalApiName = new HashMap<>();
        for (var db : dbMetas) {
            if (!ontologyDeletes.contains(db.getUniqueIdentifier())) {
                uidByFinalApiName.put(db.getApiName(), db.getUniqueIdentifier());
            }
        }
        for (var create : ontologyCreates) {
            var metaParam = new OntologyMetaCreateParam()
                    .setDisplayName(create.getDisplayName())
                    .setApiName(create.getApiName())
                    .setDescription(create.getDescription())
                    .setIconUrl(create.getIconUrl())
                    .setCategoryId(defaultOntologyCategoryId);
            metaParam.setSpaceId(spaceId);
            String uid = ontologyMetaService.createOntology(metaParam);
            createdOntologyUid.put(create, uid);
            uidByFinalApiName.put(create.getApiName(), uid);
            summary.setCreatedOntologyCount(summary.getCreatedOntologyCount() + 1);
        }

        // 先降后升：主键/标题键切换（含两个属性互换）时，先把所有要清掉的旧标记清掉，
        // 否则 updateProperty/createProperty 的"主键已存在/标题键已存在"校验会中途报错
        for (var update : propertyUpdates) {
            if (!update.needsKeyClear()) {
                continue;
            }
            var keyClearWrapper = new LambdaUpdateWrapper<OntologyProperty>()
                    .eq(OntologyProperty::getUniqueIdentifier, update.uid)
                    .set(OntologyProperty::getUpdateTime, new Date());
            if (isOne(update.db.getIsPrimaryKey())) {
                keyClearWrapper.set(OntologyProperty::getIsPrimaryKey, 0);
            }
            if (isOne(update.db.getIsTitleKey())) {
                keyClearWrapper.set(OntologyProperty::getIsTitleKey, 0);
            }
            propertyMapper.update(null, keyClearWrapper);
        }
        for (var update : propertyUpdates) {
            applyCanvasPropertyUpdate(update);
            summary.setUpdatedPropertyCount(summary.getUpdatedPropertyCount() + 1);
        }

        Map<String, Integer> propertyCategoryRootByOwner = new HashMap<>();
        for (var create : propertyCreates) {
            String ownerUid = create.ownerOntologyUid != null
                    ? create.ownerOntologyUid : createdOntologyUid.get(create.ownerOntology);
            if (ownerUid == null) {
                continue;
            }
            Integer categoryId = propertyCategoryRootByOwner
                    .computeIfAbsent(ownerUid, this::getPropertyCategoryRoot);
            var propertyParam = buildPropertyCreateParam(ownerUid, create.property, categoryId);
            if (propertyParam.getDataType() == null) {
                propertyParam.setDataType(OntologyDataTypeEnum.String);
            }
            ontologyPropertyService.createProperty(propertyParam);
            summary.setCreatedPropertyCount(summary.getCreatedPropertyCount() + 1);
        }

        Integer defaultLinkCategoryId = newLinkCreates.isEmpty() ? null : getLinkCategoryRoot(spaceId);
        for (var create : newLinkCreates) {
            var linkApiName = StringUtils.isNotBlank(create.link.getApiName())
                    ? create.link.getApiName()
                    : ("relation_" + IdGenerator.generateUUID());
            var linkParam = new OntologyLinkCreateParam()
                    .setName(create.link.getName())
                    .setApiName(linkApiName)
                    .setDescription(create.link.getDescription())
                    .setOntologyUniqueIdentifierFrom(uidByFinalApiName.get(create.fromApiName))
                    .setOntologyUniqueIdentifierTo(uidByFinalApiName.get(create.toApiName))
                    .setType(OntologyLinkTypeEnum.OTHER)
                    .setSpaceId(spaceId)
                    .setCategoryId(defaultLinkCategoryId);
            ontologyLinkGroupService.createLink(linkParam);
            summary.setCreatedLinkCount(summary.getCreatedLinkCount() + 1);
        }
        for (var update : linkUpdates) {
            var wrapper = new LambdaUpdateWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getUniqueIdentifier, update.uid)
                    .set(OntologyLinkGroup::getUpdateTime, new Date());
            if (update.writes.containsKey("name")) {
                wrapper.set(OntologyLinkGroup::getName, update.writes.get("name"));
            }
            if (update.writes.containsKey("apiName")) {
                wrapper.set(OntologyLinkGroup::getApiName, update.writes.get("apiName"));
            }
            if (update.writes.containsKey("description")) {
                wrapper.set(OntologyLinkGroup::getDescription, update.writes.get("description"));
            }
            linkMapper.update(null, wrapper);
            summary.setUpdatedLinkCount(summary.getUpdatedLinkCount() + 1);
        }

        return OntologySpaceCanvasCreateVO.builder()
                .spaceId(spaceId)
                .committed(true)
                .saved(summary)
                .conflicts(allConflicts)
                .canvas(getSpaceCanvasContent(spaceId))
                .build();
    }

    @Override
    public OntologySpaceCanvasCreateParam getSpaceCanvasContent(Integer spaceId) {
        var space = spaceMapper.selectById(spaceId);
        PreconditionUtils.checkNotNull(space, "空间id不存在", HttpStatus.BAD_REQUEST);

        var canvas = new OntologySpaceCanvasCreateParam()
                .setSpaceId(spaceId)
                .setOntologies(Lists.newArrayList())
                .setLinks(Lists.newArrayList());

        var metas = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId)
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .orderByAsc(OntologyMeta::getId));
        if (CollectionUtils.isEmpty(metas)) {
            return canvas;
        }

        var properties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .in(OntologyProperty::getOntologyUniqueIdentifier,
                        metas.stream().map(OntologyMeta::getUniqueIdentifier).collect(Collectors.toList()))
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue())
                .orderByAsc(OntologyProperty::getId));
        var propertiesByOntology = properties.stream()
                .collect(Collectors.groupingBy(OntologyProperty::getOntologyUniqueIdentifier));

        // 关系端点以对象 apiName 表达，与创建时 fromOntologyApiName/toOntologyApiName 的解析口径一致
        var apiNameByUid = metas.stream()
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, OntologyMeta::getApiName, (a, b) -> a));
        var links = linkMapper.selectList(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologySpaceId, spaceId)
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue())
                .orderByAsc(OntologyLinkGroup::getId));

        canvas.setOntologies(metas.stream()
                .map(meta -> new OntologySpaceCanvasCreateParam.CanvasOntology()
                        .setUniqueIdentifier(meta.getUniqueIdentifier())
                        .setDisplayName(meta.getDisplayName())
                        .setApiName(meta.getApiName())
                        .setDescription(meta.getDescription())
                        .setIconUrl(meta.getIcon())
                        .setProperties(propertiesByOntology.getOrDefault(meta.getUniqueIdentifier(), Lists.newArrayList())
                                .stream().map(this::buildCanvasProperty).collect(Collectors.toList())))
                .collect(Collectors.toList()));
        //端点对象已停用/已删除的关系在画布上连不出线，不返回
        canvas.setLinks(links.stream()
                .filter(link -> apiNameByUid.containsKey(link.getOntologyUniqueIdentifierFrom())
                        && apiNameByUid.containsKey(link.getOntologyUniqueIdentifierTo()))
                .map(link -> new OntologySpaceCanvasCreateParam.CanvasLink()
                        .setUniqueIdentifier(link.getUniqueIdentifier())
                        .setName(link.getName())
                        .setApiName(link.getApiName())
                        .setDescription(link.getDescription())
                        .setFromOntologyApiName(apiNameByUid.get(link.getOntologyUniqueIdentifierFrom()))
                        .setToOntologyApiName(apiNameByUid.get(link.getOntologyUniqueIdentifierTo())))
                .collect(Collectors.toList()));
        return canvas;
    }

    private OntologySpaceCanvasCreateParam.CanvasProperty buildCanvasProperty(OntologyProperty property) {
        return new OntologySpaceCanvasCreateParam.CanvasProperty()
                .setUniqueIdentifier(property.getUniqueIdentifier())
                .setDisplayName(property.getDisplayName())
                .setApiName(property.getApiName())
                .setDataType(property.getPropertyType())
                .setStorageGroup(property.getStorageGroup())
                .setDescription(property.getDescription())
                .setIsPrimaryKey(property.getIsPrimaryKey() != null && property.getIsPrimaryKey() == 1)
                .setIsTitleKey(property.getIsTitleKey() != null && property.getIsTitleKey() == 1)
                .setDefaultValue(property.getDefaultValue());
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

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static boolean isOne(Integer value) {
        return value != null && value == 1;
    }

    private static OntologySpaceCanvasCreateVO.CanvasSaveSummary zeroSummary() {
        return OntologySpaceCanvasCreateVO.CanvasSaveSummary.builder().build();
    }

    private static void addStructuralConflict(List<OntologySpaceCanvasCreateVO.CanvasConflict> conflicts,
                                              String type, String uid, String apiName, String displayName,
                                              String parentOntologyUid, String reason, String message) {
        addConflict(conflicts, type, uid, apiName, displayName, parentOntologyUid, reason, message);
    }

    private static void addFieldConflict(List<OntologySpaceCanvasCreateVO.CanvasConflict> conflicts,
                                         String type, String uid, String apiName, String displayName,
                                         String parentOntologyUid, String reason, String message) {
        addConflict(conflicts, type, uid, apiName, displayName, parentOntologyUid, reason, message);
    }

    private static void addConflict(List<OntologySpaceCanvasCreateVO.CanvasConflict> conflicts,
                                    String type, String uid, String apiName, String displayName,
                                    String parentOntologyUid, String reason, String message) {
        conflicts.add(OntologySpaceCanvasCreateVO.CanvasConflict.builder()
                .type(type)
                .uniqueIdentifier(uid)
                .apiName(apiName)
                .displayName(displayName)
                .parentOntologyUniqueIdentifier(parentOntologyUid)
                .reason(reason)
                .message(message)
                .build());
    }

    /**
     * 字段级三段合并：current == null 表示前端未修改；用户没改的字段一律保留数据库现值，
     * 双方都改且值不同才记冲突，冲突字段不写入。
     */
    private static void mergeField(CanvasFieldMerge merge, String fieldName,
                                   Object baseValue, Object currentValue, Object dbValue) {
        if (currentValue == null) {
            return;
        }
        boolean userChanged = !Objects.equals(currentValue, baseValue);
        boolean otherChanged = !Objects.equals(dbValue, baseValue);
        if (!userChanged) {
            return;
        }
        if (!otherChanged) {
            merge.writes.put(fieldName, currentValue);
            return;
        }
        if (Objects.equals(currentValue, dbValue)) {
            return;
        }
        merge.conflicts.add(OntologySpaceCanvasCreateVO.FieldConflict.builder()
                .field(fieldName)
                .baseValue(baseValue)
                .currentValue(currentValue)
                .dbValue(dbValue)
                .build());
    }

    private static CanvasFieldMerge mergeOntology(OntologySpaceCanvasCreateParam.CanvasOntology base,
                                                  OntologySpaceCanvasCreateParam.CanvasOntology current,
                                                  OntologyMeta db) {
        var merge = new CanvasFieldMerge();
        mergeField(merge, "displayName",
                base == null ? null : base.getDisplayName(), current.getDisplayName(), db.getDisplayName());
        mergeField(merge, "description",
                base == null ? null : base.getDescription(), current.getDescription(), db.getDescription());
        mergeField(merge, "iconUrl",
                base == null ? null : base.getIconUrl(), current.getIconUrl(), db.getIcon());
        return merge;
    }

    private static CanvasFieldMerge mergeProperty(OntologySpaceCanvasCreateParam.CanvasProperty base,
                                                  OntologySpaceCanvasCreateParam.CanvasProperty current,
                                                  OntologyProperty db) {
        var merge = new CanvasFieldMerge();
        mergeField(merge, "displayName", base.getDisplayName(), current.getDisplayName(), db.getDisplayName());
        mergeField(merge, "dataType", base.getDataType(), current.getDataType(), db.getPropertyType());
        mergeField(merge, "storageGroup", base.getStorageGroup(), current.getStorageGroup(), db.getStorageGroup());
        mergeField(merge, "description", base.getDescription(), current.getDescription(), db.getDescription());
        mergeField(merge, "isPrimaryKey", base.getIsPrimaryKey(), current.getIsPrimaryKey(), isOne(db.getIsPrimaryKey()));
        mergeField(merge, "isTitleKey", base.getIsTitleKey(), current.getIsTitleKey(), isOne(db.getIsTitleKey()));
        mergeField(merge, "defaultValue", base.getDefaultValue(), current.getDefaultValue(), db.getDefaultValue());
        return merge;
    }

    private static CanvasFieldMerge mergeLink(OntologySpaceCanvasCreateParam.CanvasLink base,
                                              OntologySpaceCanvasCreateParam.CanvasLink current,
                                              OntologyLinkGroup db) {
        var merge = new CanvasFieldMerge();
        mergeField(merge, "name", base.getName(), current.getName(), db.getName());
        mergeField(merge, "apiName", base.getApiName(), current.getApiName(), db.getApiName());
        mergeField(merge, "description", base.getDescription(), current.getDescription(), db.getDescription());
        return merge;
    }

    private static boolean userChanged(Object currentValue, Object baseValue) {
        return currentValue != null && !Objects.equals(currentValue, baseValue);
    }

    private static boolean ontologyUserEdited(OntologySpaceCanvasCreateParam.CanvasOntology current,
                                              OntologySpaceCanvasCreateParam.CanvasOntology base) {
        if (base == null) {
            return true;
        }
        return userChanged(current.getDisplayName(), base.getDisplayName())
                || userChanged(current.getDescription(), base.getDescription())
                || userChanged(current.getIconUrl(), base.getIconUrl());
    }

    private static boolean ontologyDbChanged(OntologyMeta db, OntologySpaceCanvasCreateParam.CanvasOntology base) {
        return !Objects.equals(db.getDisplayName(), base.getDisplayName())
                || !Objects.equals(db.getDescription(), base.getDescription())
                || !Objects.equals(db.getIcon(), base.getIconUrl());
    }

    private static boolean propertyUserEdited(OntologySpaceCanvasCreateParam.CanvasProperty current,
                                              OntologySpaceCanvasCreateParam.CanvasProperty base) {
        if (base == null) {
            return true;
        }
        return userChanged(current.getDisplayName(), base.getDisplayName())
                || userChanged(current.getDataType(), base.getDataType())
                || userChanged(current.getStorageGroup(), base.getStorageGroup())
                || userChanged(current.getDescription(), base.getDescription())
                || userChanged(current.getIsPrimaryKey(), base.getIsPrimaryKey())
                || userChanged(current.getIsTitleKey(), base.getIsTitleKey())
                || userChanged(current.getDefaultValue(), base.getDefaultValue());
    }

    private static boolean propertyDbChanged(OntologyProperty db,
                                             OntologySpaceCanvasCreateParam.CanvasProperty base) {
        return !Objects.equals(db.getDisplayName(), base.getDisplayName())
                || !Objects.equals(db.getPropertyType(), base.getDataType())
                || !Objects.equals(db.getStorageGroup(), base.getStorageGroup())
                || !Objects.equals(db.getDescription(), base.getDescription())
                || !Objects.equals(isOne(db.getIsPrimaryKey()), base.getIsPrimaryKey())
                || !Objects.equals(isOne(db.getIsTitleKey()), base.getIsTitleKey())
                || !Objects.equals(db.getDefaultValue(), base.getDefaultValue());
    }

    private static boolean linkUserEdited(OntologySpaceCanvasCreateParam.CanvasLink current,
                                          OntologySpaceCanvasCreateParam.CanvasLink base) {
        if (base == null) {
            return true;
        }
        return userChanged(current.getName(), base.getName())
                || userChanged(current.getApiName(), base.getApiName())
                || userChanged(current.getDescription(), base.getDescription())
                || userChanged(current.getFromOntologyApiName(), base.getFromOntologyApiName())
                || userChanged(current.getToOntologyApiName(), base.getToOntologyApiName());
    }

    private static boolean linkDbChanged(OntologyLinkGroup db,
                                         OntologySpaceCanvasCreateParam.CanvasLink base) {
        return !Objects.equals(db.getName(), base.getName())
                || !Objects.equals(db.getApiName(), base.getApiName())
                || !Objects.equals(db.getDescription(), base.getDescription());
    }

    /**
     * 把前端给的对象标识解析成最终存活对象的 apiName：先按 apiName 匹配，再按显示名称匹配。
     */
    private static String resolveFinalApiName(String apiNameOrDisplayName,
                                              Set<String> finalApiNames,
                                              Map<String, String> displayNameToFinalApiName) {
        if (StringUtils.isBlank(apiNameOrDisplayName)) {
            return null;
        }
        if (finalApiNames.contains(apiNameOrDisplayName)) {
            return apiNameOrDisplayName;
        }
        return displayNameToFinalApiName.get(apiNameOrDisplayName);
    }

    /**
     * 合并后该对象是否还有属性承担主键/标题键：前端没传的开关按 base 值算。
     */
    private static boolean hasFinalKey(String ownerUid,
                                       List<OntologySpaceCanvasCreateParam.CanvasProperty> currentProperties,
                                       Map<String, CanvasPropertyRef> basePropertyByUid,
                                       boolean primaryKey) {
        for (var property : safeList(currentProperties)) {
            Boolean value = primaryKey ? property.getIsPrimaryKey() : property.getIsTitleKey();
            if (value == null) {
                String uid = property.getUniqueIdentifier();
                var baseRef = uid == null ? null : basePropertyByUid.get(uid);
                if (baseRef == null || !Objects.equals(baseRef.ownerOntologyUid, ownerUid)) {
                    continue;
                }
                value = primaryKey ? baseRef.property.getIsPrimaryKey() : baseRef.property.getIsTitleKey();
            }
            if (Boolean.TRUE.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 删除主键/标题键属性前先把标记清掉：deleteProperty 以数据库现值为准拒绝删除，
     * 而“换主键时删旧属性”需要这一步让位。
     */
    private void clearPropertyKeysBeforeDelete(OntologyProperty property) {
        if (property == null) {
            return;
        }
        boolean clearPrimaryKey = isOne(property.getIsPrimaryKey());
        boolean clearTitleKey = isOne(property.getIsTitleKey());
        if (!clearPrimaryKey && !clearTitleKey) {
            return;
        }
        var wrapper = new LambdaUpdateWrapper<OntologyProperty>()
                .eq(OntologyProperty::getUniqueIdentifier, property.getUniqueIdentifier())
                .set(OntologyProperty::getUpdateTime, new Date());
        if (clearPrimaryKey) {
            wrapper.set(OntologyProperty::getIsPrimaryKey, 0);
        }
        if (clearTitleKey) {
            wrapper.set(OntologyProperty::getIsTitleKey, 0);
        }
        propertyMapper.update(null, wrapper);
    }

    private void applyCanvasPropertyUpdate(CanvasPropertyUpdate update) {
        var param = new OntologyPropertyUpdateParam()
                .setDisplayName(update.finalDisplayName())
                .setDataType(update.finalDataType())
                .setDescription(update.finalDescription())
                .setIsTitleKey(update.finalTitleKey())
                .setIsPrimaryKey(update.finalPrimaryKey())
                .setDefaultValue(update.finalDefaultValue())
                .setStorageGroup(update.finalStorageGroup());
        param.setUniqueIdentifier(update.uid);
        ontologyPropertyService.updateProperty(param);
    }

    /** base/current 中的属性及其所属对象，用于校验“属性不能挪到别的对象下”。 */
    private static class CanvasPropertyRef {

        private final String ownerOntologyUid;

        private final OntologySpaceCanvasCreateParam.CanvasProperty property;

        private CanvasPropertyRef(String ownerOntologyUid,
                                  OntologySpaceCanvasCreateParam.CanvasProperty property) {
            this.ownerOntologyUid = ownerOntologyUid;
            this.property = property;
        }
    }

    /** 待新建的属性；所属对象是新建对象时此时还没有 uid。 */
    private static class CanvasPropertyCreate {

        private final String ownerOntologyUid;

        private final OntologySpaceCanvasCreateParam.CanvasOntology ownerOntology;

        private final OntologySpaceCanvasCreateParam.CanvasProperty property;

        private CanvasPropertyCreate(String ownerOntologyUid,
                                     OntologySpaceCanvasCreateParam.CanvasOntology ownerOntology,
                                     OntologySpaceCanvasCreateParam.CanvasProperty property) {
            this.ownerOntologyUid = ownerOntologyUid;
            this.ownerOntology = ownerOntology;
            this.property = property;
        }
    }

    private static class CanvasOntologyUpdate {

        private final String uid;

        private final Map<String, Object> writes;

        private CanvasOntologyUpdate(String uid, Map<String, Object> writes) {
            this.uid = uid;
            this.writes = writes;
        }
    }

    private static class CanvasPropertyUpdate {

        private final String uid;

        private final OntologyProperty db;

        private final Map<String, Object> writes;

        private CanvasPropertyUpdate(String uid, OntologyProperty db, Map<String, Object> writes) {
            this.uid = uid;
            this.db = db;
            this.writes = writes;
        }

        private String finalDisplayName() {
            return (String) writes.getOrDefault("displayName", db.getDisplayName());
        }

        private OntologyDataTypeEnum finalDataType() {
            return (OntologyDataTypeEnum) writes.getOrDefault("dataType", db.getPropertyType());
        }

        private String finalStorageGroup() {
            return (String) writes.getOrDefault("storageGroup", db.getStorageGroup());
        }

        private String finalDescription() {
            return (String) writes.getOrDefault("description", db.getDescription());
        }

        private String finalDefaultValue() {
            return (String) writes.getOrDefault("defaultValue", db.getDefaultValue());
        }

        private Boolean finalPrimaryKey() {
            return (Boolean) writes.getOrDefault("isPrimaryKey", isOne(db.getIsPrimaryKey()));
        }

        private Boolean finalTitleKey() {
            return (Boolean) writes.getOrDefault("isTitleKey", isOne(db.getIsTitleKey()));
        }

        private boolean needsKeyClear() {
            return (isOne(db.getIsPrimaryKey()) && !finalPrimaryKey())
                    || (isOne(db.getIsTitleKey()) && !finalTitleKey());
        }
    }

    private static class CanvasLinkUpdate {

        private final String uid;

        private final Map<String, Object> writes;

        private CanvasLinkUpdate(String uid, Map<String, Object> writes) {
            this.uid = uid;
            this.writes = writes;
        }
    }

    private static class CanvasLinkCreate {

        private final OntologySpaceCanvasCreateParam.CanvasLink link;

        private final String fromApiName;

        private final String toApiName;

        private CanvasLinkCreate(OntologySpaceCanvasCreateParam.CanvasLink link,
                                 String fromApiName, String toApiName) {
            this.link = link;
            this.fromApiName = fromApiName;
            this.toApiName = toApiName;
        }
    }

    private static class CanvasFieldMerge {

        private final Map<String, Object> writes = new LinkedHashMap<>();

        private final List<OntologySpaceCanvasCreateVO.FieldConflict> conflicts = new ArrayList<>();

        private OntologySpaceCanvasCreateVO.CanvasConflict toConflict(String type, String uid, String apiName,
                                                                      String displayName,
                                                                      String parentOntologyUid) {
            String fieldNames = conflicts.stream()
                    .map(OntologySpaceCanvasCreateVO.FieldConflict::getField)
                    .collect(Collectors.joining(", "));
            return OntologySpaceCanvasCreateVO.CanvasConflict.builder()
                    .type(type)
                    .uniqueIdentifier(uid)
                    .apiName(apiName)
                    .displayName(displayName)
                    .parentOntologyUniqueIdentifier(parentOntologyUid)
                    .reason(REASON_FIELD_CONFLICT)
                    .fields(conflicts)
                    .message(fieldNames + " 已被他人修改，本次未覆盖")
                    .build();
        }
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
     * 创建（或复用）空间的对象分类树全部，让画布导入的对象都能挂到分类下。
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
        PreconditionUtils.checkNotNull(created, "创建对象分类失败", HttpStatus.INTERNAL_SERVER_ERROR);
        return created.getId();
    }

    /**
     * 创建（或复用）空间的行为分类树根节点。
     */
    private Integer createDefaultActionCategoryRoot(Integer spaceId) {
        var existingRoot = actionCategoryService.getOne(new LambdaQueryWrapper<ActionCategory>()
                .eq(ActionCategory::getOntologySpaceId, spaceId)
                .eq(ActionCategory::getParentId, 0));
        if (existingRoot != null) {
            return existingRoot.getId();
        }
        var categoryParam = new ActionCategoryCreateParam()
                .setParentId(0)
                .setName("全部");
        categoryParam.setSpaceId(spaceId);
        actionCategoryService.createActionCategory(categoryParam);
        var created = actionCategoryService.getOne(new LambdaQueryWrapper<ActionCategory>()
                .eq(ActionCategory::getOntologySpaceId, spaceId)
                .eq(ActionCategory::getParentId, 0)
                .orderByDesc(ActionCategory::getId));
        PreconditionUtils.checkNotNull(created, "创建行为分类失败", HttpStatus.INTERNAL_SERVER_ERROR);
        return created.getId();
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
    public List<OntologySpaceVO> querySpace(Integer type) {
        var userContext = UserContextHolder.get();
        List<OntologySpace> spaces;
        if (SpaceTypeEnum.TOPIC.getValue().equals(SpaceTypeEnum.normalize(type))) {
            // 主题：按创建用户名过滤，admin/admin1 不过滤
            var username = userContext == null ? null : userContext.getUsername();
            var skipUserFilter = username != null && TOPIC_ADMIN_USERNAMES.contains(username);
            spaces = list(new LambdaQueryWrapper<OntologySpace>()
                    .eq(OntologySpace::getType, SpaceTypeEnum.TOPIC.getValue())
                    .eq(!skipUserFilter, OntologySpace::getUsername, username));
        } else {
            // 空间：当前用户配置了绑定空间时仅返回该空间，未配置则返回全部空间
            var bindSpaceId = userContext == null ? null : userContext.getSpaceId();
            spaces = list(new LambdaQueryWrapper<OntologySpace>()
                    .eq(OntologySpace::getType, SpaceTypeEnum.SPACE.getValue())
                    .eq(bindSpaceId != null, OntologySpace::getId, bindSpaceId));
        }
        if (CollectionUtils.isEmpty(spaces)) {
            return Lists.newArrayList();
        }
        var spaceMap = spaces.stream().collect(Collectors.toMap(v -> v.getId(), v -> v));
        // 获取对象meta map（按照空间id分组）
        var metaMap = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .in(OntologyMeta::getOntologySpaceId, spaceMap.keySet()))
                .stream().collect(Collectors.groupingBy(OntologyMeta::getOntologySpaceId));

        // 获取对象action map（key为ontology uniqueIdentifier, value为action 数量）
        Map<String, Integer> actionMap = actionMapper.countGroupByOntology().stream()
                .collect(Collectors.toMap(OntologyStatisticsCountView::getOntologyUniqueIdentifier, OntologyStatisticsCountView::getCnt));
        // 获取对象property map（key为ontology uniqueIdentifier, value为property 数量）
        Map<String, Integer> propertyMap = propertyMapper.countGroupByOntology().stream()
                .collect(Collectors.toMap(OntologyStatisticsCountView::getOntologyUniqueIdentifier, OntologyStatisticsCountView::getCnt));
        // 获取对象link map（key为ontology spaceId, value为link 数量）
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

                    String iconUrl = space.getIcon();
                    if (iconUrl == null) {
                        iconUrl = "";
                    }
                    
                    OntologySpaceVO vo = new OntologySpaceVO();
                    vo.setIconUrl(iconUrl);
                    vo.setApiName(space.getApiName());
                    vo.setSpaceId(space.getId());
                    vo.setType(space.getType());
                    vo.setUsername(space.getUsername());
                    vo.setDisplayName(space.getDisplayName());
                    vo.setDescription(space.getDescription());
                    vo.setOntologyCount(ontologyCnt);
                    vo.setActionCount(actionCnt);
                    vo.setPropertyCount(propertyCnt);
                    vo.setLinkCount(linkCnt);
                    vo.setCreateTime(space.getCreateTime());
                    vo.setUpdateTime(space.getUpdateTime());
                    return vo;
                })
                .collect(Collectors.toList());

        return res;
    }

    @Override
    public List<OntologySpaceVO> querySpaceByName(String name) {
        if (StringUtils.isEmpty(name)) {
            return Lists.newArrayList();
        }
        var spaces = list(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getApiName, name)
                .or().eq(OntologySpace::getDisplayName, name));
        if (CollectionUtils.isEmpty(spaces)) {
            return Lists.newArrayList();
        }
        var spaceMap = spaces.stream().collect(Collectors.toMap(OntologySpace::getId, v -> v));
        var metaMap = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .in(OntologyMeta::getOntologySpaceId, spaceMap.keySet()))
                .stream().collect(Collectors.groupingBy(OntologyMeta::getOntologySpaceId));
        Map<String, Integer> actionMap = actionMapper.countGroupByOntology().stream()
                .collect(Collectors.toMap(OntologyStatisticsCountView::getOntologyUniqueIdentifier, OntologyStatisticsCountView::getCnt));
        Map<String, Integer> propertyMap = propertyMapper.countGroupByOntology().stream()
                .collect(Collectors.toMap(OntologyStatisticsCountView::getOntologyUniqueIdentifier, OntologyStatisticsCountView::getCnt));
        Map<Integer, Integer> linkMap = linkMapper.countGroupByOntologySpace().stream()
                .collect(Collectors.toMap(SpaceStatisticsCountView::getOntologySpaceId, SpaceStatisticsCountView::getCnt));

        return spaceMap.values().stream()
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
    }

    @Override
    public OntologySpaceStatisticVO getStatistic(Integer spaceId) {
        //check space
        var space = spaceMapper.selectById(spaceId);
        PreconditionUtils.checkNotNull(space, "空间id不存在", HttpStatus.BAD_REQUEST);

        // 对象数量
        var ontologyCount = metaMapper.selectCount(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId));
        // 关系数量
        var linkCount = linkMapper.selectCount(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologySpaceId, spaceId));
        // 函数算子数量
        var functionCount = functionMapper.selectCount(new LambdaQueryWrapper<Function>()
                .eq(Function::getOntologySpaceId, spaceId));
        // 行为数量
        var actionCount = actionMapper.selectCount(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getOntologySpaceId, spaceId));
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
     * 当空间对象数量为0时才可删除，删除对象空间不会删除db下的schema
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
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(metaList), "该空间下存在对象，不能删除", HttpStatus.BAD_REQUEST);
        //delete ontology category
        categoryMapper.delete(new LambdaQueryWrapper<OntologyCategory>().eq(OntologyCategory::getOntologySpaceId, spaceId));
        //delete links and link categories：不清理会留下指向已删空间的孤儿关系，使全局概览数大于各空间之和
        linkMapper.delete(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getOntologySpaceId, spaceId));
        ontologyLinkCategoryMapper.delete(new LambdaQueryWrapper<OntologyLinkCategory>().eq(OntologyLinkCategory::getOntologySpaceId, spaceId));
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
        //import link category：关系分类同样挂到默认根分类"全部"下
        var linkCategory = spaceCreateDTO.getLinkCategory();
        if (linkCategory != null) {
            var linkRootId = getLinkCategoryRoot(spaceId);
            if ("全部".equals(linkCategory.getName())) {
                // 导出文件的顶层就是自动创建的"全部"根，跳过它自身，只导入子节点，避免嵌套出"全部/全部"
                if (CollectionUtils.isNotEmpty(linkCategory.getChildren())) {
                    for (var child : linkCategory.getChildren()) {
                        var childParam = new OntologyLinkCategoryCreateParam()
                                .setName(child.getName())
                                .setChildren(child.getChildren())
                                .setParentId(linkRootId);
                        childParam.setSpaceId(spaceId);
                        ontologyLinkCategoryService.createCategory(childParam);
                    }
                }
            } else {
                linkCategory.setParentId(linkRootId);
                linkCategory.setSpaceId(spaceId);
                ontologyLinkCategoryService.createCategory(linkCategory);
            }
        }
        //import ontologies
        List<String> failedOntology = Lists.newArrayList();
        //对象显示名称 -> 实例「导出主键 → 新主键」映射，实例关系边落位时据此还原到正确的实例对
        Map<String, Map<String, Object>> pkMappingByOntologyName = new HashMap<>();
        if (CollectionUtils.isNotEmpty(spaceCreateDTO.getOntologies())) {
            spaceCreateDTO.getOntologies().forEach(dto -> {
                try {
                    var pkMapping = ontologyMetaService.importOntology(dto, spaceId);
                    if (pkMapping != null) {
                        pkMappingByOntologyName.put(dto.getMetadata().getDisplayName(), pkMapping);
                    }
                } catch (Exception e) {
                    log.error("对象 {} 导入失败", dto.getMetadata().getDisplayName(), e);
                    failedOntology.add(dto.getMetadata().getDisplayName());
                }
            });
        }
        //处理继承关系：所有对象导入完成后，根据 parentOntologyDisplayName 设置 parentUniqueIdentifier
        importInheritanceRelations(spaceId, spaceCreateDTO.getOntologies());
        //import links：关系端点按对象显示名称解析为新对象id，必须在全部对象导入完成后创建
        importOntologyLinks(spaceId, spaceCreateDTO.getLinks(), pkMappingByOntologyName);
        return failedOntology;
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    @SneakyThrows
    public List<String> importOntologySpaceToTarget(Integer targetSpaceId, MultipartFile file) {
        InputStream inputStream = file.getInputStream();
        var spaceCreateDTO = jsonMapper.readValue(inputStream, new TypeReference<OntologySpaceCreateDTO>() {
        });
        
        // 验证目标空间存在
        var targetSpace = spaceMapper.selectById(targetSpaceId);
        if (targetSpace == null) {
            throw new BusinessException("目标空间不存在：" + targetSpaceId, HttpStatus.BAD_REQUEST);
        }
        
        // 导入分类树（替换目标空间的分类结构）
        var category = spaceCreateDTO.getOntologyCategory();
        if (category != null) {
            // 先删除目标空间的所有分类
            var existingCategories = categoryService.list(new LambdaQueryWrapper<OntologyCategory>()
                    .eq(OntologyCategory::getOntologySpaceId, targetSpaceId));
            if (CollectionUtils.isNotEmpty(existingCategories)) {
                categoryService.removeByIds(existingCategories.stream().map(OntologyCategory::getId).collect(Collectors.toList()));
            }
            // 用导出文件的分类树重新创建
            category.setParentId(0);
            category.setSpaceId(targetSpaceId);
            categoryService.createCategory(category);
        }
        
        // 导入关系分类树（替换目标空间的关系分类结构）
        var linkCategory = spaceCreateDTO.getLinkCategory();
        if (linkCategory != null) {
            // 先删除目标空间的所有关系分类
            var existingLinkCategories = ontologyLinkCategoryService.list(new LambdaQueryWrapper<OntologyLinkCategory>()
                    .eq(OntologyLinkCategory::getOntologySpaceId, targetSpaceId));
            if (CollectionUtils.isNotEmpty(existingLinkCategories)) {
                ontologyLinkCategoryService.removeByIds(existingLinkCategories.stream().map(OntologyLinkCategory::getId).collect(Collectors.toList()));
            }
            // 用导出文件的关系分类树重新创建
            linkCategory.setParentId(0);
            linkCategory.setSpaceId(targetSpaceId);
            ontologyLinkCategoryService.createCategory(linkCategory);
        }
        
        // 导入对象（先删除冲突对象）
        List<String> failedOntology = Lists.newArrayList();
        Map<String, Map<String, Object>> pkMappingByOntologyName = new HashMap<>();
        
        if (CollectionUtils.isNotEmpty(spaceCreateDTO.getOntologies())) {
            // 系统表对象列表，这些对象不应该被导入
            Set<String> systemTables = Set.of("table_field_mapping");
            
            spaceCreateDTO.getOntologies().forEach(dto -> {
                try {
                    var metaData = dto.getMetadata();
                    if (metaData == null) {
                        return;
                    }
                    // 跳过系统表对象
                    if (systemTables.contains(metaData.getApiName()) || systemTables.contains(metaData.getDisplayName())) {
                        log.info("跳过系统表对象：{}", metaData.getDisplayName());
                        return;
                    }
                    // 删除目标空间中冲突的对象（按 displayName 或 apiName 匹配）
                    deleteConflictingOntologyInSpace(targetSpaceId, metaData.getDisplayName(), metaData.getApiName());
                    
                    var pkMapping = ontologyMetaService.importOntology(dto, targetSpaceId);
                    if (pkMapping != null) {
                        pkMappingByOntologyName.put(metaData.getDisplayName(), pkMapping);
                    }
                } catch (Exception e) {
                    log.error("对象 {} 导入失败", dto.getMetadata().getDisplayName(), e);
                    failedOntology.add(dto.getMetadata().getDisplayName());
                }
            });
        }
        
        // 处理继承关系
        importInheritanceRelations(targetSpaceId, spaceCreateDTO.getOntologies());
        
        // 导入关系
        importOntologyLinks(targetSpaceId, spaceCreateDTO.getLinks(), pkMappingByOntologyName);
        
        return failedOntology;
    }

    /**
     * 删除目标空间中冲突的对象（按 displayName 或 apiName 匹配）
     */
    private void deleteConflictingOntologyInSpace(Integer spaceId, String displayName, String apiName) {
        // 按 displayName 查找冲突对象
        var conflictByDisplayName = ontologyMetaService.getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId)
                .eq(OntologyMeta::getDisplayName, displayName));
        if (conflictByDisplayName != null) {
            log.info("删除冲突对象（displayName匹配）：{} ({})", displayName, conflictByDisplayName.getUniqueIdentifier());
            ontologyMetaService.deleteOntology(conflictByDisplayName.getUniqueIdentifier());
            return;
        }
        // 按 apiName 查找冲突对象
        var conflictByApiName = ontologyMetaService.getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId)
                .eq(OntologyMeta::getApiName, apiName));
        if (conflictByApiName != null) {
            log.info("删除冲突对象（apiName匹配）：{} ({})", apiName, conflictByApiName.getUniqueIdentifier());
            ontologyMetaService.deleteOntology(conflictByApiName.getUniqueIdentifier());
        }
    }

    /**
     * 导入对象继承关系：根据导出时的 parentOntologyDisplayName 解析新空间中的父对象 uniqueIdentifier 并更新
     */
    private void importInheritanceRelations(Integer spaceId, List<OntologyCreateDTO> ontologies) {
        if (CollectionUtils.isEmpty(ontologies)) {
            return;
        }
        // 收集有父对象的对象信息
        var ontologiesWithParent = ontologies.stream()
                .filter(dto -> dto.getMetadata() != null
                        && org.apache.commons.lang3.StringUtils.isNotEmpty(dto.getMetadata().getParentOntologyDisplayName()))
                .collect(Collectors.toList());
        if (ontologiesWithParent.isEmpty()) {
            return;
        }
        // 查询新空间中所有对象的 displayName -> uniqueIdentifier 映射
        var displayNameToUid = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, spaceId)
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream().collect(Collectors.toMap(OntologyMeta::getDisplayName, OntologyMeta::getUniqueIdentifier, (a, b) -> a));
        // 逐个更新父对象关系
        for (var dto : ontologiesWithParent) {
            var childDisplayName = dto.getMetadata().getDisplayName();
            var parentDisplayName = dto.getMetadata().getParentOntologyDisplayName();
            var childUid = displayNameToUid.get(childDisplayName);
            var parentUid = displayNameToUid.get(parentDisplayName);
            if (childUid == null) {
                log.warn("继承关系导入跳过：子对象 {} 在新空间不存在", childDisplayName);
                continue;
            }
            if (parentUid == null) {
                log.warn("继承关系导入跳过：父对象 {} 在新空间不存在", parentDisplayName);
                continue;
            }
            // 更新 parentUniqueIdentifier
            metaMapper.update(null, new LambdaUpdateWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, childUid)
                    .set(OntologyMeta::getParentUniqueIdentifier, parentUid));
            log.info("继承关系导入成功：{} -> {}", childDisplayName, parentDisplayName);
        }
    }

    @Override
    public OntologySpaceCreateDTO exportOntologySpace(Integer spaceId, OntologyExportTypeEnum exportType) {
        var space = getById(spaceId);
        PreconditionUtils.checkNotNull(space, "对象空间不存在:" + spaceId);

        var ontologySpace = OntologySpaceDTO.builder()
                .apiName(space.getApiName())
                .displayName(space.getDisplayName())
                .description(space.getDescription())
                .build();

        var categories = categoryService.list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, space.getId()));
        var ontologyCategory = buildOntologyCategoryTree(categories);

        //一次装配空间下全部对象
        var ontologies = ontologyMetaService.exportOntologies(space, exportType);

        //空间下的对象关系：端点以对象显示名称表达，导入时据此解析新生成的对象id
        var uidToDisplayName = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, space.getId())
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream().collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, OntologyMeta::getDisplayName, (a, b) -> a));
        var linkCategories = ontologyLinkCategoryService.list(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, space.getId()));

        return OntologySpaceCreateDTO.builder()
                .ontologySpace(ontologySpace)
                .ontologyCategory(ontologyCategory)
                .linkCategory(buildLinkCategoryTree(linkCategories))
                .links(exportOntologyLinks(space, linkCategories, uidToDisplayName, exportType))
                .ontologies(ontologies)
                .build();
    }

    /**
     * 导出空间内启用对象之间的关系（含关系分类路径与实例关系边）。
     *
     * <p>端点对象已停用/已删除的关系不导出，否则导入后无法还原。
     * 实例关系边仅在 INSTANCE 导出模式下采集，SCHEMA 模式不查图库。</p>
     */
    private List<OntologyLinkDTO> exportOntologyLinks(OntologySpace space,
                                                      List<OntologyLinkCategory> linkCategories,
                                                      Map<String, String> uidToDisplayName,
                                                      OntologyExportTypeEnum exportType) {
        var links = linkMapper.selectList(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologySpaceId, space.getId())
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue()));
        if (CollectionUtils.isEmpty(links)) {
            return Lists.newArrayList();
        }
        var categoryPathMap = linkCategories.stream()
                .collect(Collectors.toMap(OntologyLinkCategory::getId, OntologyLinkCategory::getPath, (a, b) -> a));
        var withInstances = exportType == OntologyExportTypeEnum.INSTANCE;
        return links.stream()
                .filter(link -> uidToDisplayName.containsKey(link.getOntologyUniqueIdentifierFrom())
                        && uidToDisplayName.containsKey(link.getOntologyUniqueIdentifierTo()))
                .map(link -> OntologyLinkDTO.builder()
                        .name(link.getName())
                        .apiName(link.getApiName())
                        .description(link.getDescription())
                        .type(link.getType())
                        .ontologyNameFrom(uidToDisplayName.get(link.getOntologyUniqueIdentifierFrom()))
                        .ontologyNameTo(uidToDisplayName.get(link.getOntologyUniqueIdentifierTo()))
                        .categoryPath(link.getCategoryId() == null ? null : categoryPathMap.get(link.getCategoryId()))
                        .entityRelations(withInstances
                                ? entityService.exportEntityRelations(link.getUniqueIdentifier()) : null)
                        .build())
                .collect(Collectors.toList());
    }

    private OntologyLinkCategoryCreateParam buildLinkCategoryTree(List<OntologyLinkCategory> categories) {
        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }
        var root = categories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() == 0)
                .findFirst().orElse(null);
        if (root == null) {
            return null;
        }
        return OntologyLinkCategoryCreateParam.builder()
                .parentId(root.getParentId())
                .name(root.getName())
                .children(buildLinkCategoryChildren(root.getId(), categories))
                .build();
    }

    private List<CategoryNode> buildLinkCategoryChildren(Integer parentId, List<OntologyLinkCategory> categories) {
        return categories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId().equals(parentId))
                .sorted(java.util.Comparator.comparing(OntologyLinkCategory::getId))
                .map(c -> CategoryNode.builder()
                        .name(c.getName())
                        .children(buildLinkCategoryChildren(c.getId(), categories))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 导入空间下的对象关系：端点对象显示名称与关系分类路径均需在导入完成后才存在，故由导入流程最后调用。
     *
     * <p>先同步两端对象的图节点，实例关系边需要有节点才能落位；导出文件带了实例关系边时，
     * 按「导出主键 → 新主键」映射把这些边原样写到新关系上（状态与时间窗不变），未点亮的实例对不建边。</p>
     */
    private void importOntologyLinks(Integer spaceId,
                                     List<OntologyLinkDTO> links,
                                     Map<String, Map<String, Object>> pkMappingByOntologyName) {
        if (CollectionUtils.isEmpty(links)) {
            return;
        }
        var uidByDisplayName = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, spaceId)
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream().collect(Collectors.toMap(OntologyMeta::getDisplayName, OntologyMeta::getUniqueIdentifier, (a, b) -> a));
        var linkCategories = ontologyLinkCategoryService.list(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, spaceId));
        var categoryIdByPath = linkCategories.stream()
                .collect(Collectors.toMap(OntologyLinkCategory::getPath, OntologyLinkCategory::getId, (a, b) -> a));
        //取关系根分类的id，路径找不到时兜底挂到根下
        Integer linkRootCategoryId = linkCategories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() == 0)
                .map(OntologyLinkCategory::getId)
                .findFirst().orElse(null);
        // 实例关系边以两端节点为端点，同一对象的节点只同步一次
        Set<String> nodeSynced = new HashSet<>();
        for (var link : links) {
            var fromUid = uidByDisplayName.get(link.getOntologyNameFrom());
            var toUid = uidByDisplayName.get(link.getOntologyNameTo());
            if (fromUid == null || toUid == null) {
                log.warn("关系 {} 导入跳过：端点对象不存在，from={}, to={}", link.getName(), link.getOntologyNameFrom(), link.getOntologyNameTo());
                continue;
            }
            //无论导入文件是否带实例关系边都同步节点：后续行为函数与手动激活落边都要有节点可做端点
            Stream.of(fromUid, toUid).filter(nodeSynced::add).forEach(entityService::createEntityNodes);
            var entityRelations = link.getEntityRelations();
            var withEntityRelations = CollectionUtils.isNotEmpty(entityRelations);
            Integer linkCategoryId = null;
            if (StringUtils.isNotBlank(link.getCategoryPath())) {
                linkCategoryId = categoryIdByPath.get(link.getCategoryPath());
                if (linkCategoryId == null) {
                    log.warn("关系 {} 导入：分类路径 {} 在目标空间不存在，兜底挂到根分类下",
                            link.getName(), link.getCategoryPath());
                    linkCategoryId = linkRootCategoryId;
                }
            }
            var linkUid = ontologyLinkGroupService.createLink(new OntologyLinkCreateParam()
                    .setName(link.getName())
                    .setApiName(StringUtils.defaultIfBlank(link.getApiName(), "relation_" + IdGenerator.generateUUID()))
                    .setDescription(link.getDescription())
                    .setType(link.getType() == null ? OntologyLinkTypeEnum.OTHER : link.getType())
                    .setOntologyUniqueIdentifierFrom(fromUid)
                    .setOntologyUniqueIdentifierTo(toUid)
                    .setCategoryId(linkCategoryId)
                    .setSpaceId(spaceId));
            if (withEntityRelations) {
                entityService.importEntityRelations(linkUid, entityRelations,
                        pkMappingByOntologyName.get(link.getOntologyNameFrom()),
                        pkMappingByOntologyName.get(link.getOntologyNameTo()));
            }
        }
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
                .sorted(java.util.Comparator.comparing(OntologyCategory::getId))
                .map(c -> CategoryNode.builder()
                        .name(c.getName())
                        .children(buildOntologyCategoryChildren(c.getId(), categories))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 查询对象的属性分类树根节点（创建对象时已默认生成）。
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
     * 查询空间的对象（本地对象）分类树根节点（创建空间时已默认生成）。
     */
    private Integer getOntologyCategoryRoot(Integer spaceId) {
        var root = categoryService.getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .eq(OntologyCategory::getParentId, 0));
        PreconditionUtils.checkNotNull(root, "对象分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
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

        // 3. 预查并校验源对象
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

        // 4. 创建子空间（type=主题/子空间，同时记录创建用户名）
        var spaceId = doCreateSpace(OntologySpaceCreateParam.builder()
                .displayName(param.getDisplayName())
                .apiName(param.getApiName())
                .description(param.getDescription())
                .iconUrl(param.getIconUrl())
                .build(), SpaceTypeEnum.TOPIC.getValue());

        // 4.1 拷贝父空间的对象分类树与关系分类树（含多级）到子空间，并记录  映射
        Integer newOntologyCatRoot = getOntologyCategoryRoot(spaceId);
        Integer newLinkCatRoot = getLinkCategoryRoot(spaceId);
        Map<Integer, Integer> ontologyCatMap = copyOntologyCategoryTree(param.getParentSpaceId(), spaceId);
        Map<Integer, Integer> linkCatMap = copyLinkCategoryTree(param.getParentSpaceId(), spaceId);

        // 5. 复制对象、属性、实例
        Map<String, String> ontologyMapping = new HashMap<>();
        //新对象唯一标识 -> 「导出主键 → 新主键」映射，第 6 步复制关系边时落位用
        Map<String, Map<String, Object>> pkMappingByNewOntologyUid = new HashMap<>();
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
            // 收集配置了筛选条件的属性（按源属性 apiName 匹配导出实例中的属性键值），用于导入实例时过滤
            var instanceFilters = new ArrayList<InstancePropertyFilter>();
            for (var propSelection : ontologySelection.getSelectedProperties()) {
                var sourceProp = sourcePropMap.get(propSelection.getSourcePropertyUniqueIdentifier());
                var targetPropCat = resolveCategory(propCatMap, sourceProp.getPropertyCategoryId(), newPropRoot);
                var newPropId = createPropertyInSubspace(newOntologyId, sourceProp, targetPropCat);
                savePropertyFilter(spaceId, newOntologyId, sourceProp.getUniqueIdentifier(), newPropId, propSelection.getFilter());
                if (propSelection.getFilter() != null && propSelection.getFilter().getOp() != null) {
                    instanceFilters.add(new InstancePropertyFilter(sourceProp.getApiName(), propSelection.getFilter()));
                }
            }

            // 自动绑定数据源并创建实体表
            var bindResult = ontologyPropertyService.autoBindDatasource(newOntologyId);
            if (bindResult != null && CollectionUtils.isNotEmpty(bindResult.getBlockedItems())) {
                // 未绑定数据源的属性不会建列，下一步 importInstances 会直接丢弃这些属性的实例值
                log.warn("子空间对象{}属性自动关联数据源部分跳过，未绑定属性的实例值不会写入实体表：{}", newOntologyId, bindResult.getMessage());
            }

            // 导入实例（先按显式勾选主键过滤，再按属性筛选条件过滤实例；筛选条件元数据仍由 savePropertyFilter 写入新表）
            pkMappingByNewOntologyUid.put(newOntologyId, importInstances(sourceMeta.getUniqueIdentifier(), newOntologyId,
                    ontologySelection.getSelectedInstancePrimaryKeys(), instanceFilters));

            // 同步导入的实例到 ArangoDB 图（node 集合）：本步补齐“实例数据”，
            // 第 6 步复制的实例关系边以两端节点为端点，后续行为函数与手动激活落边同样依赖节点存在。
            entityService.createEntityNodes(newOntologyId);

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
                var newLinkId = createLinkInSubspace(spaceId, sourceLink, newFromId, newToId, linkSelection,
                        targetLinkCat, pkMappingByNewOntologyUid);
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

    /**
     * 按勾选主键与属性筛选条件导入父空间实例。
     *
     * @return 导出主键到新主键的映射，供关系边落位；无实例导入时为空 map
     */
    private Map<String, Object> importInstances(String sourceOntologyId, String newOntologyId,
                                                List<Object> selectedPrimaryKeys,
                                                List<InstancePropertyFilter> propertyFilters) {
        var exported = entityService.exportInstances(sourceOntologyId);
        if (CollectionUtils.isEmpty(exported.getNodes())) {
            return new HashMap<>();
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

        // 按属性筛选条件过滤实例：实例所有已配置的条件均命中才保留（多条件间为 AND）
        if (CollectionUtils.isNotEmpty(propertyFilters)) {
            nodes = nodes.stream()
                    .filter(n -> matchesFilters(n, propertyFilters))
                    .collect(Collectors.toList());
        }

        if (CollectionUtils.isEmpty(nodes)) {
            log.info("子空间实例导入：过滤后无匹配实例，sourceOntologyId={}", sourceOntologyId);
            return new HashMap<>();
        }

        var instances = OntologyInstancesExportDTO.builder().nodes(nodes).build();
        return entityService.importInstances(newOntologyId, instances);
    }

    private boolean matchesFilters(EntityNodeExportDTO node, List<InstancePropertyFilter> filters) {
        var properties = node.getProperties();
        for (var f : filters) {
            var value = properties == null ? null : properties.get(f.getApiName());
            if (!matchesFilter(value, f.getFilter())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断单个实例属性值是否命中筛选条件。
     *
     * <p>一对多属性值为 List 时，任一元素命中即视为命中；值无法按筛选配置类型解析时视为不命中；
     * APPLY 为函数型表达式，仅作为元数据保存，不参与实例过滤。</p>
     */
    private boolean matchesFilter(Object value, OntologySubspaceCreateParam.PropertyFilterConfig filter) {
        QueryOpEnum op = filter.getOp();
        if (op == QueryOpEnum.IS_NULL) {
            return value == null;
        }
        if (op == QueryOpEnum.IS_NOT_NULL) {
            return value != null;
        }
        if (value == null) {
            return false;
        }
        if (op == QueryOpEnum.APPLY) {
            log.warn("属性筛选条件 op=APPLY 暂不参与实例过滤，默认放行：value={}", filter.getValue());
            return true;
        }
        if (value instanceof Collection) {
            Collection<?> values = (Collection<?>) value;
            if (CollectionUtils.isEmpty(values)) {
                return false;
            }
            return values.stream().anyMatch(v -> evaluateOp(op, v, filter));
        }
        return evaluateOp(op, value, filter);
    }

    private boolean evaluateOp(QueryOpEnum op, Object value,
                               OntologySubspaceCreateParam.PropertyFilterConfig filter) {
        OntologyDataTypeEnum dataType = filter.getDataType();
        Object raw = filter.getValue();
        List<?> operands;
        if (raw instanceof Collection) {
            operands = new ArrayList<>((Collection<?>) raw);
        } else if (raw instanceof Object[]) {
            operands = Arrays.asList((Object[]) raw);
        } else {
            operands = raw == null ? new ArrayList<>() : List.of(raw);
        }
        switch (op) {
            case EQ:
                return compareValue(dataType, value, operands.get(0)) == 0;
            case NE:
                return compareValue(dataType, value, operands.get(0)) != 0;
            case GT:
                return compareValue(dataType, value, operands.get(0)) > 0;
            case GE:
                return compareValue(dataType, value, operands.get(0)) >= 0;
            case LT:
                return compareValue(dataType, value, operands.get(0)) < 0;
            case LE:
                return compareValue(dataType, value, operands.get(0)) <= 0;
            case BETWEEN: {
                if (operands.size() < 2) {
                    throw new BusinessException("BETWEEN 筛选条件需要 [下限, 上限] 两个值", HttpStatus.BAD_REQUEST);
                }
                int lower = compareValue(dataType, value, operands.get(0));
                int upper = compareValue(dataType, value, operands.get(1));
                return lower >= 0 && upper <= 0;
            }
            case NOT_BETWEEN: {
                if (operands.size() < 2) {
                    throw new BusinessException("NOT_BETWEEN 筛选条件需要 [下限, 上限] 两个值", HttpStatus.BAD_REQUEST);
                }
                int lower = compareValue(dataType, value, operands.get(0));
                int upper = compareValue(dataType, value, operands.get(1));
                return lower < 0 || upper > 0;
            }
            case IN:
                return operands.stream().anyMatch(o -> compareValue(dataType, value, o) == 0);
            case NOT_IN:
                return operands.stream().noneMatch(o -> compareValue(dataType, value, o) == 0);
            case LIKE:
                return textOf(dataType, value).contains(textOf(dataType, operands.get(0)));
            case LIKE_LEFT:
                return textOf(dataType, value).startsWith(textOf(dataType, operands.get(0)));
            case LIKE_RIGHT:
                return textOf(dataType, value).endsWith(textOf(dataType, operands.get(0)));
            default:
                throw new BusinessException("不支持的筛选操作符：" + op, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 按筛选配置的数据类型将实例属性值与筛选值归一后比较。
     */
    private int compareValue(OntologyDataTypeEnum dataType, Object value, Object filterVal) {
        if (dataType != null) {
            switch (dataType) {
                case Bool:
                    return Boolean.compare(toBoolean(value), toBoolean(filterVal));
                case Date:
                case Timestamp:
                    return Long.compare(toDate(value).getTime(), toDate(filterVal).getTime());
                default:
                    break;
            }
            if (isNumeric(dataType)) {
                return toDecimal(value).compareTo(toDecimal(filterVal));
            }
        }
        return String.valueOf(value).compareTo(String.valueOf(filterVal));
    }

    private String textOf(OntologyDataTypeEnum dataType, Object value) {
        if (dataType != null && isNumeric(dataType)) {
            return toDecimal(value).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private boolean isNumeric(OntologyDataTypeEnum dataType) {
        switch (dataType) {
            case Byte:
            case Short:
            case Int:
            case Long:
            case Float:
            case Double:
            case Decimal:
                return true;
            default:
                return false;
        }
    }

    private java.math.BigDecimal toDecimal(Object value) {
        if (value instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) value;
        }
        if (value instanceof Number) {
            return java.math.BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("实例属性值无法解析为数值：" + value, HttpStatus.BAD_REQUEST);
        }
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private Date toDate(Object value) {
        if (value instanceof Date) {
            return (Date) value;
        }
        String text = String.valueOf(value).trim();
        // 兼容前端常见格式：2026-10-01 / 2026-10-01 08:00:00 / ISO8601（以 T 分隔）
        try {
            if (text.contains("T")) {
                return new Date(java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli());
            }
            return java.sql.Timestamp.valueOf(text.contains(" ") ? text : text + " 00:00:00");
        } catch (Exception e) {
            throw new BusinessException("实例属性值无法解析为日期：" + value, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 实例过滤条件：源属性 apiName（与导出实例 properties 的键一致）+ 筛选配置。
     */
    @Data
    @AllArgsConstructor
    private static class InstancePropertyFilter {

        private final String apiName;

        private final OntologySubspaceCreateParam.PropertyFilterConfig filter;
    }

    /**
     * 在子空间创建关系，并复制父关系下已成立的实例关系边。
     * <p>
     * 图库里未点亮的实例对没有边，父关系导出为空时新关系同样一条边都不建，
     * 后续由行为函数、手动激活或再次导入产生。
     *
     * @param pkMappingByNewOntologyUid 新对象唯一标识 -> 「导出主键 → 新主键」映射
     */
    private String createLinkInSubspace(Integer spaceId, OntologyLinkGroup sourceLink,
                                        String fromId, String toId,
                                        OntologySubspaceCreateParam.LinkSelection selection,
                                        Integer targetCategoryId,
                                        Map<String, Map<String, Object>> pkMappingByNewOntologyUid) {
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

        var sourceRelations = entityService.exportEntityRelations(sourceLink.getUniqueIdentifier());
        if (CollectionUtils.isNotEmpty(sourceRelations)) {
            entityService.importEntityRelations(newLink.getUniqueIdentifier(), sourceRelations,
                    pkMappingByNewOntologyUid.get(fromId), pkMappingByNewOntologyUid.get(toId));
        }
        return newLink.getUniqueIdentifier();
    }

    /**
     * 拷贝父空间的对象分类树（ontology_category，按 spaceId）到子空间。
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
                ? byParent.getOrDefault(parentRoot.getId(), new ArrayList<>()).stream()
                        .sorted(java.util.Comparator.comparing(OntologyCategory::getId))
                        .collect(Collectors.toList())
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
                next.addAll(byParent.getOrDefault(node.getId(), new ArrayList<>()).stream()
                        .sorted(java.util.Comparator.comparing(OntologyCategory::getId))
                        .collect(Collectors.toList()));
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
                ? byParent.getOrDefault(parentRoot.getId(), new ArrayList<>()).stream()
                        .sorted(java.util.Comparator.comparing(OntologyLinkCategory::getId))
                        .collect(Collectors.toList())
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
                next.addAll(byParent.getOrDefault(node.getId(), new ArrayList<>()).stream()
                        .sorted(java.util.Comparator.comparing(OntologyLinkCategory::getId))
                        .collect(Collectors.toList()));
            }
            current = next;
        }
        return map;
    }

    /**
     * 拷贝源对象的属性分类树（property_category，按 ontology_unique_identifier）到新对象，复用已生成的“全部”根节点。
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
                ? byParent.getOrDefault(parentRoot.getId(), new ArrayList<>()).stream()
                        .sorted(java.util.Comparator.comparing(PropertyCategory::getId))
                        .collect(Collectors.toList())
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
                next.addAll(byParent.getOrDefault(node.getId(), new ArrayList<>()).stream()
                        .sorted(java.util.Comparator.comparing(PropertyCategory::getId))
                        .collect(Collectors.toList()));
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