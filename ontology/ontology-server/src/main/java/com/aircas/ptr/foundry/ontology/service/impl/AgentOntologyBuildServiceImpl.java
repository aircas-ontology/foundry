package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.AgentOntologyBuildParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.vo.AgentOntologyBuildResultVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkCategoryMapper;
import com.aircas.ptr.foundry.ontology.service.AgentOntologyBuildService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Agent 本体构建落库服务实现。
 *
 * <p>编排逻辑严格模仿画布一键建空间 {@code OntologySpaceServiceImpl#createSpaceWithCanvasContent}：
 * 先建本体元数据拿到 uniqueIdentifier，再逐条建属性，最后逐条建关系；区别在于本实现**不建空间**
 * （spaceId 由用户选定），且关系的目标端是空间内已有本体（uniqueIdentifier 由功能四给出），
 * 无需像画布那样在批次内做 apiName→uid 解析。</p>
 *
 * <p>关系分类（categoryId）为 {@code ontology_link_group} 必填项：入参未携带时，沿用画布策略
 * 懒创建本空间的"默认分类"（{@code ontology_link_category}），同一批次内复用，避免重复建。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentOntologyBuildServiceImpl implements AgentOntologyBuildService {

    /** 属性默认存储分组，与画布落库保持一致（画布无此输入，固定填 "main"）。 */
    private static final String DEFAULT_STORAGE_GROUP = "main";

    /** 关系默认分类名称，与画布 createDefaultLinkCategory 一致。 */
    private static final String DEFAULT_LINK_CATEGORY_NAME = "默认分类";

    /** 合法 apiName（数据库标识符）格式：字母/下划线/$ 开头，后接字母数字下划线$，总长 ≤63。 */
    private static final Pattern API_NAME_PATTERN = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$");

    private final OntologyMetaService ontologyMetaService;
    private final OntologyPropertyService ontologyPropertyService;
    private final OntologyLinkGroupService ontologyLinkGroupService;
    private final OntologyLinkCategoryMapper ontologyLinkCategoryMapper;

    @Transactional(transactionManager = "chainedTransactionManager", rollbackFor = Exception.class)
    @Override
    public AgentOntologyBuildResultVO buildOntology(AgentOntologyBuildParam param) {
        // 0. 入口兜底校验：MCP 写工具经大模型调用，@Valid 在工具入参上不保证触发，
        //    此处在事务开始前拦截非法入参，避免脏数据直达 DB 或落库到一半回滚。
        PreconditionUtils.checkNotNull(param, "本体构建入参不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkNotNull(param.getSpaceId(), "spaceId（本体空间 id）不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(param.getDisplayName()),
                "displayName（对象名称）不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(param.getApiName()),
                "apiName（对象标识）不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(API_NAME_PATTERN.matcher(param.getApiName()).matches(),
                "apiName 非法（须为数据库标识符，形如 ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$）：" + param.getApiName(),
                HttpStatus.BAD_REQUEST);

        // 1. 创建本体元数据，拿到 uniqueIdentifier（与画布 createOntology 同一入口，含同空间重名校验）
        var metaParam = new OntologyMetaCreateParam()
                .setDisplayName(param.getDisplayName())
                .setApiName(param.getApiName())
                .setDescription(param.getDescription())
                .setCategoryId(param.getCategoryId());
        metaParam.setSpaceId(param.getSpaceId());
        String uniqueIdentifier = ontologyMetaService.createOntology(metaParam);
        log.info("[AgentOntologyBuild] 本体已创建 uniqueIdentifier={}, apiName={}, spaceId={}, sourceTable={}",
                uniqueIdentifier, param.getApiName(), param.getSpaceId(), param.getSourceTable());

        // 2. 逐条创建属性（与画布 buildPropertyCreateParam + createProperty 一致）
        int propertyCount = 0;
        if (CollectionUtils.isNotEmpty(param.getProperties())) {
            for (var agentProperty : param.getProperties()) {
                ontologyPropertyService.createProperty(buildPropertyCreateParam(uniqueIdentifier, agentProperty));
                propertyCount++;
            }
        }

        // 3. 逐条创建关系；categoryId 为空时懒创建本空间默认关系分类（与画布 createDefaultLinkCategory 一致）
        int linkCount = 0;
        if (CollectionUtils.isNotEmpty(param.getRelations())) {
            Integer defaultCategoryId = null;
            for (var agentRelation : param.getRelations()) {
                var categoryId = agentRelation.getCategoryId();
                if (categoryId == null) {
                    if (defaultCategoryId == null) {
                        defaultCategoryId = createDefaultLinkCategory(param.getSpaceId());
                    }
                    categoryId = defaultCategoryId;
                }
                var linkParam = new OntologyLinkCreateParam()
                        .setName(agentRelation.getName())
                        // 源端固定为本次新建本体，目标端为功能四选定的同空间已有本体
                        .setOntologyUniqueIdentifierFrom(uniqueIdentifier)
                        .setOntologyUniqueIdentifierTo(agentRelation.getTargetUniqueIdentifier())
                        .setType(resolveLinkType(agentRelation.getType()))
                        .setCategoryId(categoryId);
                ontologyLinkGroupService.createLink(linkParam);
                linkCount++;
            }
        }

        return AgentOntologyBuildResultVO.builder()
                .uniqueIdentifier(uniqueIdentifier)
                .displayName(param.getDisplayName())
                .apiName(param.getApiName())
                .spaceId(param.getSpaceId())
                .propertyCount(propertyCount)
                .linkCount(linkCount)
                .build();
    }

    /**
     * 组装属性创建入参，语义对齐画布 {@code buildPropertyCreateParam}。
     *
     * <p>差异点：Agent 属性的 apiName 直接取数据库列名（snake_case），dataType 由数据库原生类型字符串
     * 归一化为 {@link OntologyDataTypeEnum}；isTitleKey 画布由用户勾选，Agent 场景默认 false，
     * 后续如需"名称键"可由功能三选择阶段补充。溯源字段 sourceTable / viaField 当前仅记日志，
     * 不落 ontology_property（该表无对应列），避免为此做 DDL 迁移。</p>
     */
    private OntologyPropertyCreateParam buildPropertyCreateParam(String ontologyUniqueIdentifier,
                                                                 AgentOntologyBuildParam.AgentProperty agentProperty) {
        var propertyParam = new OntologyPropertyCreateParam()
                .setDisplayName(agentProperty.getDisplayName())
                .setApiName(agentProperty.getApiName())
                .setDataType(OntologyDataTypeEnum.valueOfJdbcType(agentProperty.getDataType()))
                .setDescription(agentProperty.getDescription())
                .setIsPrimaryKey(Boolean.TRUE.equals(agentProperty.getIsPrimaryKey()))
                .setIsTitleKey(Boolean.TRUE.equals(agentProperty.getIsTitleKey()))
                .setStorageGroup(DEFAULT_STORAGE_GROUP);
        propertyParam.setOntologyIdentifier(ontologyUniqueIdentifier);
        if (StringUtils.isNotBlank(agentProperty.getSourceTable()) || StringUtils.isNotBlank(agentProperty.getViaField())) {
            log.debug("[AgentOntologyBuild] 属性溯源 apiName={}, sourceTable={}, viaField={}",
                    agentProperty.getApiName(), agentProperty.getSourceTable(), agentProperty.getViaField());
        }
        return propertyParam;
    }

    /**
     * 懒创建本空间的默认关系分类，返回其 id。与画布 {@code createDefaultLinkCategory} 同构。
     */
    private Integer createDefaultLinkCategory(Integer spaceId) {
        var category = OntologyLinkCategory.builder()
                .ontologySpaceId(spaceId)
                .name(DEFAULT_LINK_CATEGORY_NAME)
                .parentId(0)
                .path(DEFAULT_LINK_CATEGORY_NAME)
                .build();
        ontologyLinkCategoryMapper.insert(category);
        return category.getId();
    }

    /**
     * 关系类型字符串归一化为枚举；空值兜底 OTHER，非法值抛业务异常（与画布 resolveLinkType 一致）。
     */
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
}
