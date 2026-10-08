package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyInstancesExportDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.QueryOpEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySubspaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.po.PropertyCategory;
import com.aircas.ptr.foundry.ontology.model.po.SubspacePropertyFilter;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySubspaceCreateVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.SubspacePropertyFilterMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.aircas.ptr.foundry.ontology.service.OntologySubspaceService;
import com.aircas.ptr.foundry.ontology.service.PropertyCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 子空间创建服务实现：基于父空间按向导四步生成子空间。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OntologySubspaceServiceImpl implements OntologySubspaceService {

    private final OntologySpaceService ontologySpaceService;
    private final OntologySpaceMapper ontologySpaceMapper;
    private final OntologyMetaService ontologyMetaService;
    private final OntologyMetaMapper ontologyMetaMapper;
    private final OntologyPropertyService ontologyPropertyService;
    private final OntologyPropertyMapper ontologyPropertyMapper;
    private final OntologyLinkGroupService ontologyLinkGroupService;
    private final OntologyLinkGroupMapper ontologyLinkGroupMapper;
    private final EntityService entityService;
    private final SubspacePropertyFilterMapper subspacePropertyFilterMapper;
    private final PropertyCategoryService propertyCategoryService;
    private final OntologyCategoryService ontologyCategoryService;
    private final ObjectMapper jsonMapper;

    @Override
    @Transactional(transactionManager = "chainedTransactionManager", rollbackFor = Exception.class)
    public OntologySubspaceCreateVO createSubspace(OntologySubspaceCreateParam param) {
        // 1. 校验父空间
        var parentSpace = ontologySpaceMapper.selectById(param.getParentSpaceId());
        PreconditionUtils.checkNotNull(parentSpace, "父空间不存在", HttpStatus.BAD_REQUEST);

        // 2. 校验子空间名称/apiName 不重复
        checkSpaceNameConflict(param.getDisplayName(), param.getApiName());

        // 3. 预查并校验源本体
        var sourceOntologyIds = param.getSelectedOntologies().stream()
                .map(OntologySubspaceCreateParam.OntologySelection::getSourceOntologyUniqueIdentifier)
                .collect(Collectors.toList());
        var sourceMetas = ontologyMetaMapper.selectList(
                new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, param.getParentSpaceId())
                        .in(OntologyMeta::getUniqueIdentifier, sourceOntologyIds)
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkArgument(sourceMetas.size() == sourceOntologyIds.size(),
                "选中的对象不存在或已停用", HttpStatus.BAD_REQUEST);
        var sourceMetaMap = sourceMetas.stream()
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, v -> v));

        // 4. 创建子空间
        var spaceId = ontologySpaceService.createSpace(OntologySpaceCreateParam.builder()
                .displayName(param.getDisplayName())
                .apiName(param.getApiName())
                .description(param.getDescription())
                .iconUrl(param.getIconUrl())
                .build());

        // 5. 复制对象、属性、实例
        Map<String, String> ontologyMapping = new HashMap<>();
        for (var ontologySelection : param.getSelectedOntologies()) {
            var sourceMeta = sourceMetaMap.get(ontologySelection.getSourceOntologyUniqueIdentifier());
            var newOntologyId = createOntologyInSubspace(spaceId, sourceMeta);
            ontologyMapping.put(sourceMeta.getUniqueIdentifier(), newOntologyId);

            var sourceProps = ontologyPropertyMapper.selectList(
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

            var propertyCategoryRootId = getPropertyCategoryRoot(newOntologyId);
            for (var propSelection : ontologySelection.getSelectedProperties()) {
                var sourceProp = sourcePropMap.get(propSelection.getSourcePropertyUniqueIdentifier());
                var newPropId = createPropertyInSubspace(newOntologyId, sourceProp, propertyCategoryRootId);
                savePropertyFilter(spaceId, newOntologyId, sourceProp.getUniqueIdentifier(), newPropId, propSelection.getFilter());
            }

            // 自动绑定数据源并创建实体表
            ontologyPropertyService.autoBindDatasource(newOntologyId);

            // 导入实例（按显式勾选主键过滤；属性筛选条件已由 savePropertyFilter 作为元数据写入新表）
            importInstances(sourceMeta.getUniqueIdentifier(), newOntologyId,
                    ontologySelection.getSelectedInstancePrimaryKeys());

            // 同步 ArangoDB 实体节点，供后续创建关系使用
            entityService.createEntityNodes(newOntologyId);
        }

        // 6. 复制关系
        Map<String, String> linkMapping = new HashMap<>();
        if (CollectionUtils.isNotEmpty(param.getSelectedLinks())) {
            var sourceLinkIds = param.getSelectedLinks().stream()
                    .map(OntologySubspaceCreateParam.LinkSelection::getSourceLinkUniqueIdentifier)
                    .collect(Collectors.toList());
            var sourceLinks = ontologyLinkGroupMapper.selectList(
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

                var newLinkId = createLinkInSubspace(spaceId, sourceLink, newFromId, newToId, linkSelection);
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
        var exist = ontologySpaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getDisplayName, displayName)
                .or().eq(OntologySpace::getApiName, apiName));
        PreconditionUtils.checkArgument(exist == null, "空间显示名称或api名称已存在", HttpStatus.BAD_REQUEST);
    }

    private String createOntologyInSubspace(Integer spaceId, OntologyMeta sourceMeta) {
        var categoryRootId = getOntologyCategoryRoot(spaceId);
        var metaParam = OntologyMetaCreateParam.builder()
                .displayName(sourceMeta.getDisplayName())
                .apiName(sourceMeta.getApiName())
                .description(sourceMeta.getDescription())
                .iconUrl(sourceMeta.getIcon())
                .categoryId(categoryRootId)
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

        var newProp = ontologyPropertyMapper.selectOne(
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
            if (filter.getValue() != null) {
                entity.setFilterValue(jsonMapper.writeValueAsString(filter.getValue()));
            }
            if (CollectionUtils.isNotEmpty(filter.getValues())) {
                entity.setFilterValues(jsonMapper.writeValueAsString(filter.getValues()));
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

        // 仅按显式勾选的主键过滤；为空表示导入该本体下全部实例。
        // 说明：第三步配置的属性筛选条件仅作为元数据写入 ontology_subspace_property_filter 表，
        // 不参与本次实例导入的裁剪（需求：选实例=决定导哪些，选属性配置=记录筛选规则）。
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
                                        OntologySubspaceCreateParam.LinkSelection selection) {
        var linkApiName = StringUtils.defaultString(selection.getApiName(), "relation_" + IdGenerator.generateUUID());
        var linkParam = new OntologyLinkCreateParam()
                .setSpaceId(spaceId)
                .setOntologyUniqueIdentifierFrom(fromId)
                .setOntologyUniqueIdentifierTo(toId)
                .setName(StringUtils.defaultString(selection.getName(), sourceLink.getName()))
                .setApiName(linkApiName)
                .setType(selection.getType() != null ? selection.getType() : sourceLink.getType())
                .setDescription(StringUtils.defaultString(selection.getDescription(), sourceLink.getDescription()));
        ontologyLinkGroupService.createLink(linkParam);

        var newLink = ontologyLinkGroupMapper.selectOne(
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

    private Integer getPropertyCategoryRoot(String ontologyUniqueIdentifier) {
        var root = propertyCategoryService.getOne(
                new LambdaQueryWrapper<PropertyCategory>()
                        .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                        .eq(PropertyCategory::getParentId, 0));
        PreconditionUtils.checkNotNull(root, "属性分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }

    private Integer getOntologyCategoryRoot(Integer spaceId) {
        var root = ontologyCategoryService.getOne(
                new LambdaQueryWrapper<OntologyCategory>()
                        .eq(OntologyCategory::getOntologySpaceId, spaceId)
                        .eq(OntologyCategory::getParentId, 0));
        PreconditionUtils.checkNotNull(root, "本体分类根节点不存在", HttpStatus.INTERNAL_SERVER_ERROR);
        return root.getId();
    }
}
