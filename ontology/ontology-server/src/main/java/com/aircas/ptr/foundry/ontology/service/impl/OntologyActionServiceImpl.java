package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.client.XxlJobClient;
import com.aircas.ptr.foundry.ontology.constant.FunctionConstant;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.enums.*;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.*;
import com.aircas.ptr.foundry.ontology.service.behavior.BtTreeManager;
import com.aircas.ptr.foundry.ontology.service.behavior.CompiledBehaviorTree;
import com.aircas.ptr.foundry.ontology.service.behavior.TreeActionInvoker;
import com.aircas.ptr.foundry.ontology.service.behavior.TreeExecutionRuntime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Lists;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OntologyActionServiceImpl extends ServiceImpl<OntologyActionMapper, OntologyAction> implements OntologyActionService {

    /** 属性值行为的主存储分组：main 行的增删改就是实体本身，由实体接口负责，不允许行为直接操作 */
    private static final String MAIN_STORAGE_GROUP = "main";

    /** 应用行为可绑定的算子类型：基础算子归入基础行为，函数编排不作为行为 */
    private static final List<FunctionTypeEnum> APPLICATION_FUNCTION_TYPES =
            Lists.newArrayList(FunctionTypeEnum.CUSTOMIZE, FunctionTypeEnum.EXTERNAL, FunctionTypeEnum.CONTAINER);

    /** 行为树表达式解析器，保存期只解析语法；运行期求值走只读上下文 */
    private static final SpelExpressionParser TREE_EXPRESSION_PARSER = new SpelExpressionParser();

    /** 行为树里能做大小、等于比较的数值类型 */
    private static final Set<FunctionParamTypeEnum> TREE_NUMBER_TYPES =
            EnumSet.of(FunctionParamTypeEnum.INTEGER, FunctionParamTypeEnum.LONG,
                    FunctionParamTypeEnum.FLOAT, FunctionParamTypeEnum.DOUBLE);

    /** 冷却状态表 scope_key 列宽，超长实例主键截断后参与唯一键 */
    private static final int TREE_COOLDOWN_SCOPE_MAX_LENGTH = 255;


    @Resource
    private ActionHandleLogMapper actionHandleLogMapper;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private OntologyLinkGroupMapper linkGroupMapper;

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private FunctionService functionService;

    @Resource
    private FunctionParamMapper functionParamMapper;

    @Resource
    private OntologyActionLinkMapper actionLinkMapper;

    @Resource
    private OntologyActionMapper actionMapper;

    @Resource
    private ActionCategoryMapper actionCategoryMapper;

    @Resource
    private ObjectMapper entityMapper;

    @Resource
    private TableMetadataMapper tableMetadataMapper;

    @Resource
    private OntologyActionMappingInService ontologyActionMappingInService;

    @Lazy
    @Resource
    private OntologyPropertyService ontologyPropertyService;

    @Resource
    private ActionHandleRuleService actionHandleRuleService;

    @Resource
    private ActionHandleTaskService actionHandleTaskService;


    @Lazy
    @Resource
    private EntityServiceImpl entityService;

    @Resource
    private XxlJobClient xxlJobClient;

    @Resource
    private BtTreeManager btTreeManager;

    @Resource
    private BehaviorTreeCooldownMapper behaviorTreeCooldownMapper;

    @Resource(name = "behaviorTreeBranchExecutor")
    private ThreadPoolTaskExecutor behaviorTreeBranchExecutor;


    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public void removeByOntologyIdentifier(String ontologyIdentifier) {
        var actions = list(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, ontologyIdentifier));
        if (CollectionUtils.isNotEmpty(actions)) {
            var actionIds = actions.stream().map(v -> v.getId()).collect(Collectors.toList());
            actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().in(OntologyActionLink::getOntologyActionId, actionIds));
            remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, actionIds));
            ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, actionIds));
            // 调度行靠 action_id 关联行为，该列已在 alter_table_20260909.sql 中删除，此处无法再按行为清理调度；
            // 遗留的调度任务会在下次触发时因取不到行为而失败，待确认该列是否为误删后补回
        }
    }


    @Override
    @Transactional(value = "mainTransactionManager")
    public void createAction(ActionCreateOrUpdateParam param) {
        // 应用行为可不传 ontologyIdentifier，从分类取空间 id
        Integer spaceId = null;
        if (StringUtils.isNotEmpty(param.getOntologyIdentifier())) {
            var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
            PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
            spaceId = meta.getOntologySpaceId();
        } else {
            var category = actionCategoryMapper.selectById(param.getCategoryId());
            PreconditionUtils.checkNotNull(category, "无效的行为分类：" + param.getCategoryId(), HttpStatus.BAD_REQUEST);
            spaceId = category.getOntologySpaceId();
        }
        var exist = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(exist == null, "action api 已存在：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        var action = new OntologyAction();
        // 新建落草稿：需要显式发布才对外可用（行为测试不受此限制，草稿正是拿来试跑的）
        action.setStatus(ActionStatusEnum.DRAFT.getValue());
        var definition = checkActionDefinition(action, param, spaceId);
        save(action);
        saveActionChildren(action.getId(), definition);
        btTreeManager.invalidate(action.getOntologySpaceId(), action.getApi());
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateAction(ActionCreateOrUpdateParam param) {
        // 应用行为可不传 ontologyIdentifier，从分类取空间 id
        Integer spaceId = null;
        if (StringUtils.isNotEmpty(param.getOntologyIdentifier())) {
            var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
            PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
            spaceId = meta.getOntologySpaceId();
        } else {
            var category = actionCategoryMapper.selectById(param.getCategoryId());
            PreconditionUtils.checkNotNull(category, "无效的行为分类：" + param.getCategoryId(), HttpStatus.BAD_REQUEST);
            spaceId = category.getOntologySpaceId();
        }
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        // 编辑不改状态：草稿还是草稿、已发布还是已发布
        var originalStatus = action.getStatus();
        var definition = checkActionDefinition(action, param, spaceId);
        action.setStatus(originalStatus);
        // 原地更新：行为 id 被关联关系、参数绑定和调度任务引用，删除重建会让它们失去引用
        updateById(action);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getOntologyActionId, action.getId()));
        saveActionChildren(action.getId(), definition);
        if (action.getActionKind() == ActionKindEnum.TREE) {
            // 定义改了，已经不存在的节点不该再留着冷却记录：节点标识被其他节点复用时会把旧冷却带过来
            behaviorTreeCooldownMapper.deleteByTreeExceptNodes(spaceId, action.getApi(),
                    collectTreeNodeIds(readTreeDefinition(action.getDefinition()).getRoots()));
        }
        btTreeManager.invalidate(action.getOntologySpaceId(), action.getApi());
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void publishTree(Integer ontologySpaceId, String actionApi, Integer status) {
        PreconditionUtils.checkArgument(status != null
                        && (status == Status.ENABLE.getValue() || status == Status.DISABLE.getValue()),
                "状态只能传 1 发布或 0 下线", HttpStatus.BAD_REQUEST);
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(action.getActionKind() == ActionKindEnum.TREE,
                "只有行为树需要发布或下线", HttpStatus.BAD_REQUEST);
        if (status == Status.ENABLE.getValue()) {
            // 树没有版本，状态就是唯一开关，发布前用库里的定义再校验一遍：
            // 引用的行为可能已被删除或下架，悬空要在这里拦住，不能等执行时才发现
            var param = ActionCreateOrUpdateParam.builder()
                    .actionApi(action.getApi())
                    .displayName(action.getDisplayName())
                    .categoryId(action.getActionCategoryId())
                    .ontologySpaceId(action.getOntologySpaceId())
                    .actionKind(ActionKindEnum.TREE)
                    .treeDefinition(readTreeDefinition(action.getDefinition()))
                    .build();
            checkTreeDefinition(new OntologyAction(), param);
            action.setStatus(ActionStatusEnum.PUBLISHED.getValue());
        } else {
            // 对外仍用 1/0，落库统一回行为的两态，避免列表按 2 筛草稿时漏掉已下线的树
            action.setStatus(ActionStatusEnum.DRAFT.getValue());
        }
        updateById(action);
        btTreeManager.invalidate(action.getOntologySpaceId(), action.getApi());
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void publishAction(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        // 树走 publishTree：发布前要按库里的定义重新校验，泛化的行为发布不能绕过
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.TREE,
                "行为树请使用发布或下线行为树接口", HttpStatus.BAD_REQUEST);
        checkBoundFunctionPublished(action);
        action.setStatus(ActionStatusEnum.PUBLISHED.getValue());
        updateById(action);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void unpublishAction(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        // 树的下线要同时失效编译缓存，否则缓存里的树仍会被执行
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.TREE,
                "行为树请使用发布或下线行为树接口", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(action.getStatus() == ActionStatusEnum.PUBLISHED.getValue(),
                "该行为未在发布状态，无需撤回", HttpStatus.BAD_REQUEST);
        // 行为树里的行为节点按 api 引用，撤回发布后已发布的树会在执行时失败，先让调用方处理引用它的树
        var referencingTrees = findTreesReferencingAction(ontologySpaceId, actionApi);
        PreconditionUtils.checkArgument(referencingTrees.isEmpty(),
                "该行为被行为树引用，不能撤回发布：" + String.join("、", referencingTrees), HttpStatus.BAD_REQUEST);
        action.setStatus(ActionStatusEnum.DRAFT.getValue());
        updateById(action);
    }

    /** 绑算子的行为发布后要按引用执行，算子还停在草稿时执行必失败，所以发布前先看算子发布没有 */
    private void checkBoundFunctionPublished(OntologyAction action) {
        if (StringUtils.isEmpty(action.getFunctionApi())) {
            return;
        }
        // 与行为定义校验同源：算子 api 只在空间内唯一，不加空间条件会命中其他空间的同名算子；
        // 空间列为空的早期行为没有可比的归属空间，只能退回按 api 找
        var wrapper = new LambdaQueryWrapper<Function>().eq(Function::getApi, action.getFunctionApi());
        if (action.getOntologySpaceId() != null) {
            wrapper.eq(Function::getOntologySpaceId, action.getOntologySpaceId());
        }
        var func = functionMapper.selectList(wrapper)
                .stream()
                .max(Comparator.comparing((Function f) -> f.getVersion() == null ? "" : f.getVersion(),
                        FunctionServiceImpl::compareVersion)
                        .thenComparing(Function::getId))
                .orElse(null);
        PreconditionUtils.checkArgument(func != null, "行为绑定的算子不存在：" + action.getFunctionApi(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(func.getPublish(), FunctionConstant.PUBLISH_PUBLISHED),
                "算子[" + action.getFunctionApi() + "]尚未发布，请先发布算子再发布行为", HttpStatus.BAD_REQUEST);
    }

    /**
     * 校验行为定义：校验通过的类别写入 action，待落库的关联关系与参数绑定收集进返回值。
     */
    private ActionDefinition checkActionDefinition(OntologyAction action, ActionCreateOrUpdateParam param, Integer spaceId) {
        // 编辑不换类别：树只有 definition，行为只有关系与参数绑定，互转会让两边数据对不上
        PreconditionUtils.checkArgument(action.getActionKind() == null
                        || (action.getActionKind() == ActionKindEnum.TREE) == (param.getActionKind() == ActionKindEnum.TREE),
                "行为树与行为不能互相转换，请删除后重建", HttpStatus.BAD_REQUEST);
        // 行为树绑的是已经建好的行为，自己不指向任何对象类型，既取不到 meta 也不走行为的类别分支，
        // 必须在按对象id取 meta 之前返回，否则会被"无效的对象"拦下
        if (param.getActionKind() == ActionKindEnum.TREE) {
            checkTreeDefinition(action, param);
            return new ActionDefinition();
        }
        // 类别、输出方式必须由调用方明确传入：猜错会把行为建成另一种，执行时才暴露很难定位
        PreconditionUtils.checkNotNull(param.getActionKind(), "必须选择行为类别：基础行为/应用行为", HttpStatus.BAD_REQUEST);
        var kind = param.getActionKind();
        // 只有应用行为可以脱离对象挂在空间上；基础行为都作用在某个对象的实例上，缺对象标识会建出执行不了的记录
        if (kind == ActionKindEnum.BASE) {
            // 原来由参数基类的 @NotBlank 兜底，共用入口后移到 service，提示文案保持一致
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(param.getOntologyIdentifier()),
                    "ontologyIdentifier is empty", HttpStatus.BAD_REQUEST);
        }
        // 应用行为不传对象时不校验 meta：对象由 mappingIns 每行各自指定
        if (StringUtils.isNotBlank(param.getOntologyIdentifier())) {
            var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
            PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
        }
        // 归属的行为分类必须存在，且与对象同属一个对象空间
        var category = actionCategoryMapper.selectById(param.getCategoryId());
        PreconditionUtils.checkArgument(category != null, "无效的行为分类：" + param.getCategoryId(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(category.getOntologySpaceId().equals(spaceId),
                "行为分类不属于该对象所在的空间：" + category.getName(), HttpStatus.BAD_REQUEST);
        var definition = new ActionDefinition();
        if (kind == ActionKindEnum.APPLICATION) {
            PreconditionUtils.checkArgument(param.getSubjectType() == null && param.getOperation() == null
                    && StringUtils.isEmpty(param.getStorageGroup()), "应用行为不需要行为类型、基础操作和存储分组", HttpStatus.BAD_REQUEST);
            // outputMode 下沉到 mappingIns 每行：每个出参独立选 RESULT_ONLY / WRITE_PROPERTY
            checkFunctionAction(param, definition, APPLICATION_FUNCTION_TYPES, spaceId);
            checkWritePropertyOutput(definition);
            // 写入对象属性与绑定关系互斥：出参已经指定落属性时，不允许再挂关系
            checkOutputLinkExclusive(param, definition);
            action.setOutputMode(null);
        } else {
            var subjectType = param.getSubjectType();
            if (subjectType == null) {
                throw new BusinessException("基础行为必须选择行为类型", HttpStatus.BAD_REQUEST);
            }
            if (subjectType == ActionSubjectTypeEnum.PROPERTY_VALUE) {
                checkPropertyValueAction(param, definition);
            } else if (subjectType == ActionSubjectTypeEnum.LINK) {
                checkLinkAction(param, definition);
            } else {
                PreconditionUtils.checkArgument(param.getOperation() == null && StringUtils.isEmpty(param.getStorageGroup()),
                        "基础算子行为不需要基础操作和存储分组", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(param.getOutputMode() != ActionOutputModeEnum.WRITE_PROPERTY,
                        "基础算子行为只支持仅返回结果", HttpStatus.BAD_REQUEST);
                checkFunctionAction(param, definition, Collections.singletonList(FunctionTypeEnum.BASIC_QUERY),
                        spaceId);
                // 基础算子也不允许行级 WRITE_PROPERTY
                PreconditionUtils.checkArgument(definition.mappings.stream()
                                .noneMatch(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY),
                        "基础算子行为只支持仅返回结果，出参不可写入属性", HttpStatus.BAD_REQUEST);
            }
            // 属性值、实例关系的输出即落库本身，没有回写方式；基础算子只读
            action.setOutputMode(subjectType == ActionSubjectTypeEnum.BASIC_OPERATOR
                    ? ActionOutputModeEnum.RESULT_ONLY : null);
        }

        action.setApi(param.getActionApi());
        action.setDisplayName(param.getDisplayName());
        action.setDescription(param.getDescription());
        action.setIcon(param.getIcon());
        action.setFunctionApi(param.getFunctionApi());
        action.setOntologyUniqueIdentifier(param.getOntologyIdentifier());
        action.setOntologySpaceId(spaceId);
        action.setActionCategoryId(param.getCategoryId());
        action.setActionKind(kind);
        action.setSubjectType(param.getSubjectType());
        action.setActionOperation(param.getOperation());
        action.setStorageGroup(param.getStorageGroup());
        return definition;
    }

    /**
     * 校验行为树定义并写入 action。
     * <p>树没有对象id，所属空间由调用方直接传，行为分类也要落在同一个空间；
     * 树不绑算子、不写关系，行为侧的类别字段一律显式清空，避免由行为改成树时残留旧值。</p>
     */
    private void checkTreeDefinition(OntologyAction action, ActionCreateOrUpdateParam param) {
        var spaceId = param.getOntologySpaceId();
        PreconditionUtils.checkNotNull(spaceId, "行为树必须选择所属空间", HttpStatus.BAD_REQUEST);
        var category = actionCategoryMapper.selectById(param.getCategoryId());
        PreconditionUtils.checkArgument(category != null, "无效的行为分类：" + param.getCategoryId(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(spaceId.equals(category.getOntologySpaceId()),
                "行为分类不属于该空间：" + category.getName(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isEmpty(param.getFunctionApi()), "行为树不需要绑定算子", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getSubjectType() == null && param.getOperation() == null
                        && StringUtils.isEmpty(param.getStorageGroup()) && param.getOutputMode() == null
                        && param.getLinkMapping() == null && CollectionUtils.isEmpty(param.getMappingIns()),
                "行为树不需要行为类型、基础操作、存储分组、输出方式、关系映射和参数绑定", HttpStatus.BAD_REQUEST);
        var tree = param.getTreeDefinition();
        PreconditionUtils.checkNotNull(tree, "行为树必须提交树定义", HttpStatus.BAD_REQUEST);

        var blackboardTypes = checkTreeBlackboard(tree);
        checkTreeVariables(tree, blackboardTypes);
        var roots = tree.getRoots();
        PreconditionUtils.checkArgument(roots.size() == 1, "行为树必须且只能有一个根节点，当前 " + roots.size() + " 个", HttpStatus.BAD_REQUEST);
        checkTreeNode(roots.get(0), spaceId, blackboardTypes, new HashSet<>(), new HashSet<>());

        action.setApi(param.getActionApi());
        action.setDisplayName(param.getDisplayName());
        action.setDescription(param.getDescription());
        action.setIcon(param.getIcon());
        action.setActionCategoryId(param.getCategoryId());
        action.setActionKind(ActionKindEnum.TREE);
        action.setOntologySpaceId(spaceId);
        action.setOntologyUniqueIdentifier(null);
        action.setFunctionApi(null);
        action.setSubjectType(null);
        action.setActionOperation(null);
        action.setStorageGroup(null);
        action.setOutputMode(null);
        action.setDefinition(writeTreeDefinition(tree));
        // 新建的树是未发布状态，编辑不改状态：发布/下线是单独动作，不像行为那样「编辑即重新发布」
        if (action.getStatus() == null) {
            action.setStatus(ActionStatusEnum.DRAFT.getValue());
        }
    }

    /**
     * 黑板声明全局变量的键名。返回值是 键名 → 声明类型，后面变量和条件节点都按它校验。
     */
    private Map<String, FunctionParamTypeEnum> checkTreeBlackboard(BehaviorTreeDefinition tree) {
        Map<String, FunctionParamTypeEnum> types = new LinkedHashMap<>();
        for (var item : tree.getBlackboard()) {
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(item.getKey()), "黑板键名不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(!types.containsKey(item.getKey()), "黑板键名重复：" + item.getKey(), HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkNotNull(item.getValueType(), "黑板键 " + item.getKey() + " 必须选择值类型", HttpStatus.BAD_REQUEST);
            if (StringUtils.isNotBlank(item.getBindPropertyUniqueIdentifier())) {
                var property = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getUniqueIdentifier, item.getBindPropertyUniqueIdentifier()));
                PreconditionUtils.checkArgument(!property.isEmpty(),
                        "黑板键 " + item.getKey() + " 绑定的对象属性不存在：" + item.getBindPropertyUniqueIdentifier(), HttpStatus.BAD_REQUEST);
            }
            types.put(item.getKey(), item.getValueType());
        }
        PreconditionUtils.checkArgument(!types.isEmpty(), "行为树至少要有一个黑板键", HttpStatus.BAD_REQUEST);
        return types;
    }

    /**
     * 变量给黑板键名提供值，两边一一对应：键名集合不同、类型不一致，执行时一定读不到值或读错类型。
     */
    private void checkTreeVariables(BehaviorTreeDefinition tree, Map<String, FunctionParamTypeEnum> blackboardTypes) {
        var names = new HashSet<String>();
        for (var item : tree.getVariables()) {
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(item.getName()), "变量名不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(names.add(item.getName()), "变量名重复：" + item.getName(), HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(blackboardTypes.containsKey(item.getName()),
                    "变量 " + item.getName() + " 没有同名黑板键，运行时读不到", HttpStatus.BAD_REQUEST);
            var declared = blackboardTypes.get(item.getName());
            PreconditionUtils.checkArgument(item.getValueType() == declared,
                    "变量 " + item.getName() + " 的类型与黑板键不一致：" + item.getValueType() + " != " + declared, HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(item.getScope() == null || item.getScope() == BehaviorVariableScopeEnum.RUNTIME,
                    "变量作用域目前只支持 RUNTIME：" + item.getName(), HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(matchesTreeValueType(item.getDefaultValue(), declared),
                    "变量 " + item.getName() + " 的默认值与声明类型 " + declared.name() + " 不匹配", HttpStatus.BAD_REQUEST);
        }
        for (var key : blackboardTypes.keySet()) {
            PreconditionUtils.checkArgument(names.contains(key), "黑板键 " + key + " 缺少同名变量，没有初始值", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 递归校验节点：标识唯一、子节点数量符合类别、表达式可解析、config 按 kind 的强类型白名单校验。
     *
     * @param visitedIds 已经出现过的节点标识，用于查重复
     * @param actionApis 已经校验过的绑定行为 api，同一棵树里重复绑同一个行为不必再查库
     */
    private void checkTreeNode(BehaviorTreeNode node, Integer spaceId, Map<String, FunctionParamTypeEnum> blackboardTypes,
                               Set<String> visitedIds, Set<String> actionApis) {
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(node.getId()), "节点标识不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(visitedIds.add(node.getId()), "节点标识重复：" + node.getId(), HttpStatus.BAD_REQUEST);
        var kind = node.getKind();
        PreconditionUtils.checkNotNull(kind, "节点 " + node.getId() + " 必须选择节点类型", HttpStatus.BAD_REQUEST);
        checkTreeExpression(node.getSkipIf(), "节点 " + node.getId() + " 的跳过条件");
        checkTreeExpression(node.getWhileCondition(), "节点 " + node.getId() + " 的持续执行条件");

        var children = node.getChildren();
        switch (kind.getCategory()) {
            case COMPOSITE -> PreconditionUtils.checkArgument(!children.isEmpty(),
                    kind.getName() + " " + node.getId() + " 至少要有子节点", HttpStatus.BAD_REQUEST);
            case DECORATOR -> PreconditionUtils.checkArgument(children.size() == 1,
                    kind.getName() + " " + node.getId() + " 只能有一个子节点，当前 " + children.size() + " 个", HttpStatus.BAD_REQUEST);
            default -> PreconditionUtils.checkArgument(children.isEmpty(),
                    kind.getName() + " " + node.getId() + " 是叶子节点，不能有子节点", HttpStatus.BAD_REQUEST);
        }
        checkTreeNodeConfig(node, spaceId, blackboardTypes, actionApis);
        children.forEach(child -> checkTreeNode(child, spaceId, blackboardTypes, visitedIds, actionApis));
    }

    /**
     * 按节点类型校验专属配置：类型下拉、必填项、取值范围都在这里，config 里出现该类型不认识的字段直接拒绝。
     */
    private void checkTreeNodeConfig(BehaviorTreeNode node, Integer spaceId,
                                     Map<String, FunctionParamTypeEnum> blackboardTypes, Set<String> actionApis) {
        var nodeId = node.getId();
        switch (node.getKind()) {
            case SELECTOR, SEQUENCE -> {
                var config = readTreeConfig(node, BehaviorGroupConfig.class);
                PreconditionUtils.checkNotNull(config.getRunMode(), "节点 " + nodeId + " 必须选择执行模式", HttpStatus.BAD_REQUEST);
            }
            case PARALLEL -> {
                var config = readTreeConfig(node, BehaviorParallelConfig.class);
                PreconditionUtils.checkNotNull(config.getPolicy(), "节点 " + nodeId + " 必须选择成败判定策略", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getMaxConcurrency(), "节点 " + nodeId + " 的最大并发数");
            }
            case REPEATER -> {
                var config = readTreeConfig(node, BehaviorRepeaterConfig.class);
                PreconditionUtils.checkNotNull(config.getLoopMode(), "节点 " + nodeId + " 必须选择循环模式", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getTimes(), "节点 " + nodeId + " 的循环次数");
                if (config.getLoopMode() == BehaviorLoopModeEnum.FIXED_TIMES) {
                    PreconditionUtils.checkNotNull(config.getTimes(), "节点 " + nodeId + " 选择固定次数时必须填写次数", HttpStatus.BAD_REQUEST);
                } else {
                    // 按条件与无限循环都靠持续执行条件结束，不填就等于没有终止条件
                    PreconditionUtils.checkArgument(StringUtils.isNotBlank(node.getWhileCondition()),
                            "节点 " + nodeId + " 选择" + config.getLoopMode().getName() + "时必须填写持续执行条件", HttpStatus.BAD_REQUEST);
                }
            }
            case UNTIL_SUCCESS, UNTIL_FAILURE -> {
                var config = readTreeConfig(node, BehaviorUntilConfig.class);
                checkTreeNonNegative(config.getMaxTimes(), "节点 " + nodeId + " 的最大重试次数");
                PreconditionUtils.checkNotNull(config.getFinishState(), "节点 " + nodeId + " 必须选择次数耗尽后的收尾状态", HttpStatus.BAD_REQUEST);
                checkTreeResultState(config.getFinishState(), "节点 " + nodeId + " 的收尾状态");
            }
            case COOLDOWN -> {
                var config = readTreeConfig(node, BehaviorCooldownConfig.class);
                PreconditionUtils.checkNotNull(config.getIntervalSeconds(), "节点 " + nodeId + " 必须填写冷却时长", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getIntervalSeconds(), "节点 " + nodeId + " 的冷却时长");
                PreconditionUtils.checkNotNull(config.getHitResult(), "节点 " + nodeId + " 必须选择冷却期内的结果状态", HttpStatus.BAD_REQUEST);
                checkTreeResultState(config.getHitResult(), "节点 " + nodeId + " 的冷却期内结果状态");
            }
            case TIME_LIMIT -> {
                var config = readTreeConfig(node, BehaviorTimeLimitConfig.class);
                PreconditionUtils.checkNotNull(config.getLimitSeconds(), "节点 " + nodeId + " 必须填写限时时长", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getLimitSeconds(), "节点 " + nodeId + " 的限时时长");
                PreconditionUtils.checkNotNull(config.getTimeoutState(), "节点 " + nodeId + " 必须选择超时后的状态", HttpStatus.BAD_REQUEST);
                checkTreeResultState(config.getTimeoutState(), "节点 " + nodeId + " 的超时状态");
            }
            case INVERTER, FORCE_SUCCESS -> PreconditionUtils.checkArgument(node.getConfig() == null,
                    "节点 " + nodeId + " 的" + node.getKind().getName() + "不需要额外配置", HttpStatus.BAD_REQUEST);
            case BLACKBOARD_CONDITION -> {
                var config = readTreeConfig(node, BehaviorBlackboardConditionConfig.class);
                PreconditionUtils.checkArgument(StringUtils.isNotBlank(config.getKey()), "节点 " + nodeId + " 必须选择黑板键", HttpStatus.BAD_REQUEST);
                var declared = blackboardTypes.get(config.getKey());
                PreconditionUtils.checkArgument(declared != null, "节点 " + nodeId + " 的黑板键未声明：" + config.getKey(), HttpStatus.BAD_REQUEST);
                var op = config.getOp();
                PreconditionUtils.checkNotNull(op, "节点 " + nodeId + " 必须选择比较方式", HttpStatus.BAD_REQUEST);
                checkTreeCompareOpType(nodeId, op, declared);
                if (op == BehaviorCompareOpEnum.IS_EMPTY || op == BehaviorCompareOpEnum.IS_NOT_EMPTY) {
                    PreconditionUtils.checkArgument(config.getValue() == null,
                            "节点 " + nodeId + " 的" + op.getName() + "不需要比较值", HttpStatus.BAD_REQUEST);
                } else {
                    PreconditionUtils.checkArgument(config.getValue() != null, "节点 " + nodeId + " 必须填写比较值", HttpStatus.BAD_REQUEST);
                    PreconditionUtils.checkArgument(matchesTreeValueType(config.getValue(), declared),
                            "节点 " + nodeId + " 的比较值与黑板键 " + config.getKey() + " 的类型 " + declared.name() + " 不匹配", HttpStatus.BAD_REQUEST);
                }
            }
            case CONDITION -> {
                var config = readTreeConfig(node, BehaviorConditionConfig.class);
                PreconditionUtils.checkArgument(StringUtils.isNotBlank(config.getExpression()), "节点 " + nodeId + " 必须填写条件表达式", HttpStatus.BAD_REQUEST);
                checkTreeExpression(config.getExpression(), "节点 " + nodeId + " 的条件表达式");
            }
            case ACTION -> {
                var config = readTreeConfig(node, BehaviorActionConfig.class);
                var boundApi = config.getActionApi();
                PreconditionUtils.checkArgument(StringUtils.isNotBlank(boundApi), "行为节点 " + nodeId + " 必须绑定行为", HttpStatus.BAD_REQUEST);
                // 行为在单轮里同步执行并落库，无法安全中断已经开始的调用，超时没有可执行的语义；
                // 0 或空表示「不限制」，沿用原配置口径，只有真配了正数才拒绝
                PreconditionUtils.checkArgument(config.getTimeoutSeconds() == null || config.getTimeoutSeconds() <= 0,
                        "行为节点 " + nodeId + " 不支持超时时长：单轮同步执行无法中断已经开始的行为，"
                                + "不需要超时就传 0 或不传，需要限制后续步骤请改用限时节点",
                        HttpStatus.BAD_REQUEST);
                checkTreeNonNegative(config.getRetryTimes(), "行为节点 " + nodeId + " 的重试次数");
                // 分组表行主键、关系对端主键只能从黑板取，键名没声明就等于运行时拿不到
                checkTreeBlackboardKeyRef(nodeId, config.getDataPrimaryKeyKey(), blackboardTypes);
                checkTreeBlackboardKeyRef(nodeId, config.getLinkEntityPrimaryKeyKey(), blackboardTypes);
                if (actionApis.add(boundApi)) {
                    checkTreeBoundAction(nodeId, boundApi, spaceId);
                }
            }
        }
    }

    /**
     * 行为节点的分组表行主键、关系对端主键只能从黑板取，键名没在黑板里声明就等于运行时拿不到；不填则按不传处理。
     */
    private void checkTreeBlackboardKeyRef(String nodeId, String key, Map<String, FunctionParamTypeEnum> blackboardTypes) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        PreconditionUtils.checkArgument(blackboardTypes.containsKey(key),
                "行为节点 " + nodeId + " 引用了未声明的黑板键：" + key, HttpStatus.BAD_REQUEST);
    }

    /**
     * 行为节点绑定的行为必须已经建好、在本空间内且已发布；树不能套树，否则执行时形不成单轮语义。
     * <p>行为发布后它绑的算子仍可能被下架，发布树时一并穿透校验，否则树发布成功、执行时才失败。</p>
     */
    private void checkTreeBoundAction(String nodeId, String boundApi, Integer spaceId) {
        var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, boundApi)
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(bound != null, "行为节点 " + nodeId + " 绑定的行为在本空间不存在：" + boundApi, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(bound.getActionKind() != ActionKindEnum.TREE,
                "行为节点只能绑定行为，不能嵌套行为树：" + boundApi, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(bound.getStatus(), ActionStatusEnum.PUBLISHED.getValue()),
                "行为节点 " + nodeId + " 绑定的行为未发布：" + boundApi, HttpStatus.BAD_REQUEST);
        checkBoundFunctionPublished(bound);
    }

    /**
     * 比较方式与黑板键类型必须配得上，否则运行时是拿字符串比大小这类静默错。
     */
    private void checkTreeCompareOpType(String nodeId, BehaviorCompareOpEnum op, FunctionParamTypeEnum declared) {
        if (op == BehaviorCompareOpEnum.GT || op == BehaviorCompareOpEnum.GE
                || op == BehaviorCompareOpEnum.LT || op == BehaviorCompareOpEnum.LE) {
            PreconditionUtils.checkArgument(TREE_NUMBER_TYPES.contains(declared),
                    "节点 " + nodeId + " 的" + op.getName() + "只支持数值类型，黑板键类型是 " + declared.name(), HttpStatus.BAD_REQUEST);
        }
        if (op == BehaviorCompareOpEnum.CONTAINS || op == BehaviorCompareOpEnum.NOT_CONTAINS) {
            PreconditionUtils.checkArgument(declared == FunctionParamTypeEnum.STRING || declared == FunctionParamTypeEnum.LIST,
                    "节点 " + nodeId + " 的" + op.getName() + "只支持字符串和列表，黑板键类型是 " + declared.name(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 表达式的语法校验：保存期只解析不求值，运行期才用只读上下文求值。
     */
    private void checkTreeExpression(String expression, String label) {
        if (StringUtils.isBlank(expression)) {
            return;
        }
        try {
            TREE_EXPRESSION_PARSER.parseExpression(expression);
        } catch (Exception e) {
            throw new BusinessException(label + "不是合法表达式：" + expression, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 把节点的 config 转成该类型的强配置对象，字段不认识或类型不符一律拒绝。
     */
    @SneakyThrows
    private <T> T readTreeConfig(BehaviorTreeNode node, Class<T> configType) {
        var kindName = node.getKind().getName();
        PreconditionUtils.checkNotNull(node.getConfig(), "节点 " + node.getId() + " 缺少" + kindName + "配置", HttpStatus.BAD_REQUEST);
        try {
            return jsonMapper.convertValue(node.getConfig(), configType);
        } catch (IllegalArgumentException e) {
            // convertValue 把 Jackson 的异常包在 IllegalArgumentException 里，取原始消息才看得出没对上的是哪个字段
            var cause = e.getCause();
            var detail = cause instanceof JsonMappingException mapping ? mapping.getOriginalMessage() : e.getMessage();
            throw new BusinessException("节点 " + node.getId() + " 的" + kindName + "配置不对：" + detail, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 树里的秒数、次数这类字段只允许不填或正数，0 会被引擎当成「立刻结束」。
     */
    private void checkTreePositive(Integer value, String label) {
        PreconditionUtils.checkArgument(value == null || value > 0, label + "必须大于 0", HttpStatus.BAD_REQUEST);
    }

    /** 重试次数允许 0（不重试），负数没有意义 */
    private void checkTreeNonNegative(Integer value, String label) {
        PreconditionUtils.checkArgument(value == null || value >= 0, label + "不能为负数", HttpStatus.BAD_REQUEST);
    }

    /**
     * 节点的结果状态只允许成功/失败。单轮同步执行不会产生也不会恢复 RUNNING，
     * 配成 RUNNING 会让一次执行的结果停在「还没跑完」，只能由调用方重跑，语义上是错的。
     */
    private void checkTreeResultState(BehaviorStateEnum state, String label) {
        PreconditionUtils.checkArgument(state != BehaviorStateEnum.RUNNING,
                label + "不能是 RUNNING：单轮同步执行不会恢复，请选择 SUCCESS 或 FAILURE", HttpStatus.BAD_REQUEST);
    }

    /**
     * 判断 JSON 里的值能否按声明类型读出来；数字接受字符串写法，其余按类型匹配。
     */
    private boolean matchesTreeValueType(Object value, FunctionParamTypeEnum type) {
        if (value == null) {
            return true;
        }
        try {
            return switch (type) {
                case STRING, CHAR -> value instanceof String;
                case BOOL -> value instanceof Boolean || "true".equalsIgnoreCase(String.valueOf(value))
                        || "false".equalsIgnoreCase(String.valueOf(value));
                case INTEGER, SHORT, BYTE -> Integer.valueOf(String.valueOf(value).trim()) != null;
                case LONG -> Long.valueOf(String.valueOf(value).trim()) != null;
                case FLOAT, DOUBLE -> Double.valueOf(String.valueOf(value).trim()) != null;
                case DATE -> value instanceof String || value instanceof Number;
                case LIST -> value instanceof List;
                case OBJECT -> value instanceof Map;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 树定义按 JSON 存 ontology_action.definition，详情原样读回给前端渲染面板 */
    @SneakyThrows
    private String writeTreeDefinition(BehaviorTreeDefinition tree) {
        return jsonMapper.writeValueAsString(tree);
    }

    /** 读回行为树定义，历史数据或行为行为空时返回空对象 */
    @SneakyThrows
    private BehaviorTreeDefinition readTreeDefinition(String definition) {
        if (StringUtils.isEmpty(definition)) {
            return new BehaviorTreeDefinition();
        }
        return jsonMapper.readValue(definition, BehaviorTreeDefinition.class);
    }




    /**
     * 执行行为树：一次执行只针对一条实例数据，全树共用；节点走 behavior3java 引擎，行为真实落库。
     * <p>单轮求值，不产生 RUNNING；循环、冷却、限时、并行在单次请求内生效。</p>
     */
    @Override
    public TreeExecuteResultVO executeTree(TreeExecuteParam param) {
        var compiled = btTreeManager.getTree(param.getOntologySpaceId(), param.getTreeApi());
        return executeTreeInstance(compiled, param.getEntityPrimaryKey(), param.getInputs(),
                param.getDataPrimaryKey(), param.getLinkEntityPrimaryKey());
    }

    /**
     * 行为树测试：草稿树也能试跑，用于发布前验证编排；真实落库，与正式执行共用同一套运行时。
     * <p>草稿可能存着早于当前校验规则的配置，试跑前按保存期同一套规则再校验一遍，把悬空引用之类的问题先拦下。</p>
     */
    @Override
    public TreeExecuteResultVO testTree(TreeExecuteParam param) {
        var compiled = btTreeManager.compileForTest(param.getOntologySpaceId(), param.getTreeApi());
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getTreeApi())
                .eq(OntologyAction::getOntologySpaceId, param.getOntologySpaceId()));
        PreconditionUtils.checkNotNull(action, "无效的action api", HttpStatus.BAD_REQUEST);
        var definitionParam = ActionCreateOrUpdateParam.builder()
                .actionApi(action.getApi())
                .displayName(action.getDisplayName())
                .categoryId(action.getActionCategoryId())
                .ontologySpaceId(action.getOntologySpaceId())
                .actionKind(ActionKindEnum.TREE)
                .treeDefinition(compiled.definition())
                .build();
        checkTreeDefinition(new OntologyAction(), definitionParam);
        return executeTreeInstance(compiled, param.getEntityPrimaryKey(), param.getInputs(),
                param.getDataPrimaryKey(), param.getLinkEntityPrimaryKey());
    }

    /**
     * 批量执行行为树：按对象类型遍历实例数据，每条实例独立初始化黑板后跑同一棵树，实例之间互不影响。
     * <p>分片规则与行为批量执行一致（下标取模），供调度侧横向拆分；明细默认只返回失败实例，
     * 避免万级实例时响应体过大。</p>
     */
    @Override
    public TreeBatchExecuteResultVO executeTreeBatch(TreeBatchExecuteParam param) {
        var compiled = btTreeManager.getTree(param.getOntologySpaceId(), param.getTreeApi());
        var shardTotal = param.getShardTotal();
        var shardIndex = param.getShardIndex();
        if (shardTotal == null) {
            shardIndex = null;
        } else {
            PreconditionUtils.checkArgument(shardTotal > 0, "分片总数必须大于 0", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(shardIndex != null && shardIndex >= 0 && shardIndex < shardTotal,
                    "分片序号必须在 0 到 " + (shardTotal - 1) + " 之间", HttpStatus.BAD_REQUEST);
        }
        var primaryKeys = listTreeInstancePrimaryKeys(param.getOntologyUniqueIdentifier());
        var includeSuccess = Boolean.TRUE.equals(param.getIncludeSuccess());
        var results = new ArrayList<TreeBatchInstanceResultVO>();
        var total = 0;
        var successCount = 0;
        for (var i = 0; i < primaryKeys.size(); i++) {
            if (shardTotal != null && i % shardTotal != shardIndex) {
                continue;
            }
            total++;
            var entityPrimaryKey = primaryKeys.get(i);
            TreeBatchInstanceResultVO instanceResult;
            try {
                var executed = executeTreeInstance(compiled, entityPrimaryKey, param.getInputs(),
                        param.getDataPrimaryKey(), param.getLinkEntityPrimaryKey());
                if (executed.getState() == BehaviorStateEnum.SUCCESS) {
                    successCount++;
                    if (!includeSuccess) {
                        continue;
                    }
                    instanceResult = batchInstanceResult(entityPrimaryKey, executed, null);
                } else {
                    var failureMessage = firstFailureMessage(executed.getNodes());
                    instanceResult = batchInstanceResult(entityPrimaryKey, executed,
                            StringUtils.isBlank(failureMessage) ? "行为树执行失败" : failureMessage);
                }
            } catch (Exception e) {
                // 单条实例失败不中断整批，原因按实例回传
                log.warn("行为树批量执行失败, treeApi={}, entityPrimaryKey={}", param.getTreeApi(), entityPrimaryKey, e);
                instanceResult = TreeBatchInstanceResultVO.builder()
                        .entityPrimaryKey(entityPrimaryKey)
                        .state(BehaviorStateEnum.FAILURE)
                        .message(e instanceof BusinessException business ? business.getMessage() : e.getMessage())
                        .build();
            }
            results.add(instanceResult);
        }
        return TreeBatchExecuteResultVO.builder()
                .treeApi(compiled.api())
                .total(total)
                .successCount(successCount)
                .failCount(total - successCount)
                .results(results)
                .build();
    }

    private TreeBatchInstanceResultVO batchInstanceResult(Object entityPrimaryKey, TreeExecuteResultVO executed, String message) {
        return TreeBatchInstanceResultVO.builder()
                .entityPrimaryKey(entityPrimaryKey)
                .state(executed.getState())
                .message(message)
                .blackboard(executed.getBlackboard())
                .nodes(executed.getNodes())
                .build();
    }

    /**
     * 行为树编辑器目录：节点类型（含子节点数量约束）+ 本空间内已发布、可作为行为节点绑定的行为。
     */
    @Override
    public TreeDefsVO getTreeDefs(Integer ontologySpaceId) {
        PreconditionUtils.checkNotNull(ontologySpaceId, "必须传对象空间id", HttpStatus.BAD_REQUEST);
        var nodes = Arrays.stream(BehaviorNodeKindEnum.values())
                .map(this::treeControlNode)
                .collect(Collectors.toList());
        var actions = list(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId)
                .eq(OntologyAction::getStatus, ActionStatusEnum.PUBLISHED.getValue())
                // 类别为空的存量行为要保留，所以只排掉 TREE，不能写成 ne（SQL 里 NULL 不参与比较）
                .and(w -> w.isNull(OntologyAction::getActionKind)
                        .or().ne(OntologyAction::getActionKind, ActionKindEnum.TREE))
                .orderByAsc(OntologyAction::getDisplayName));
        var availableActions = actions.stream().map(this::toTreeAvailableAction).collect(Collectors.toList());
        return TreeDefsVO.builder().nodes(nodes).availableActions(availableActions).build();
    }

    private TreeControlNodeVO treeControlNode(BehaviorNodeKindEnum kind) {
        return TreeControlNodeVO.builder()
                .kind(kind)
                .name(kind.getName())
                .category(kind.getCategory())
                .maxChildren(switch (kind.getCategory()) {
                    case COMPOSITE -> -1;
                    case DECORATOR -> 1;
                    case LEAF -> 0;
                })
                .build();
    }

    /** 可绑定行为的参数按算子定义给出：树把外部输入参数按参数名从黑板取值，前端据此做黑板键映射 */
    private TreeAvailableActionVO toTreeAvailableAction(OntologyAction action) {
        var builder = TreeAvailableActionVO.builder()
                .actionApi(action.getApi())
                .displayName(action.getDisplayName())
                .actionKind(action.getActionKind())
                .subjectType(action.getSubjectType())
                .functionApi(action.getFunctionApi());
        if (StringUtils.isBlank(action.getFunctionApi())) {
            return builder.build();
        }
        try {
            var function = functionService.getFunctionDetailByApi(action.getOntologySpaceId(), action.getFunctionApi(), null);
            if (function == null || function.getParams() == null) {
                return builder.build();
            }
            Map<FunctionParamCategoryEnum, List<FunctionParameterVO>> grouped = function.getParams().stream()
                    .filter(param -> param.getCategory() != null)
                    .collect(Collectors.groupingBy(FunctionParameterVO::getCategory));
            return builder
                    .inputParams(toTreeActionParams(grouped.get(FunctionParamCategoryEnum.INPUT)))
                    .outputParams(toTreeActionParams(grouped.get(FunctionParamCategoryEnum.OUTPUT)))
                    .build();
        } catch (Exception e) {
            // 算子可能已被删除或下架，行为仍要出现在目录里，只是暂时拿不到参数
            log.warn("行为树节点目录取算子参数失败, actionApi={}, functionApi={}", action.getApi(), action.getFunctionApi(), e);
            return builder.build();
        }
    }

    private List<TreeActionParamVO> toTreeActionParams(List<FunctionParameterVO> params) {
        if (CollectionUtils.isEmpty(params)) {
            return new ArrayList<>();
        }
        return params.stream()
                .map(param -> TreeActionParamVO.builder()
                        .name(param.getParamName())
                        .type(param.getParamType())
                        .description(param.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 跑一条实例并还原结果树。每条实例一个运行时与黑板，编译结果可复用。
     */
    private TreeExecuteResultVO executeTreeInstance(CompiledBehaviorTree compiled, Object entityPrimaryKey, Map<String, Object> inputs,
                                                    Object dataPrimaryKey, Object linkEntityPrimaryKey) {
        var runtime = new TreeExecutionRuntime(compiled.definition(), compiled.api(), entityPrimaryKey, inputs,
                dataPrimaryKey, linkEntityPrimaryKey, compiled.spaceId(),
                this::tryRunTreeCooldown, this::runTreeBranches, this::invokeTreeNodeAction);
        compiled.tree().tick(runtime, runtime.getBehaviorBlackboard());
        return buildTreeResult(compiled, runtime);
    }

    /**
     * 并行节点的分支统一走专用线程池，用信号量把同时执行的分支数限制到配置值。
     * 线程池队列为 0，满了在调用线程跑，所以不会因为线程池饱和拒绝任务或互相等线程。
     */
    private List<BehaviorStateEnum> runTreeBranches(List<Supplier<BehaviorStateEnum>> branches, int maxConcurrency)
            throws InterruptedException {
        var permits = new Semaphore(maxConcurrency);
        var futures = new ArrayList<Future<BehaviorStateEnum>>(branches.size());
        try {
            for (var branch : branches) {
                futures.add(behaviorTreeBranchExecutor.submit(() -> {
                    permits.acquire();
                    try {
                        return branch.get();
                    } finally {
                        permits.release();
                    }
                }));
            }
            var results = new ArrayList<BehaviorStateEnum>(futures.size());
            for (var future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    // 分支自己兜异常，这里只可能是线程被中断等极端情况
                    log.warn("行为树并行分支执行异常", e.getCause());
                    results.add(BehaviorStateEnum.FAILURE);
                }
            }
            return results;
        } catch (InterruptedException e) {
            futures.forEach(future -> future.cancel(true));
            throw e;
        }
    }

    /**
     * 冷却占位：同一棵树、同一节点、同一执行实例只保留最近一次执行时间。
     * upsert 自带间隔判断，并发执行时不会出现两次都读到旧时间后同时放行。
     */
    private boolean tryRunTreeCooldown(Integer spaceId, String treeApi, String nodeId, Object entityPrimaryKey, int intervalSeconds) {
        var scopeKey = entityPrimaryKey == null ? "" : String.valueOf(entityPrimaryKey);
        if (scopeKey.length() > TREE_COOLDOWN_SCOPE_MAX_LENGTH) {
            scopeKey = scopeKey.substring(0, TREE_COOLDOWN_SCOPE_MAX_LENGTH);
        }
        return behaviorTreeCooldownMapper.tryAcquire(spaceId, treeApi, nodeId, scopeKey, intervalSeconds) > 0;
    }

    /**
     * 结果树只还原真正执行到的节点：被短路跳过、没轮到的节点不出现，与单轮引擎的口径一致。
     */
    private TreeExecuteResultVO buildTreeResult(CompiledBehaviorTree compiled, TreeExecutionRuntime runtime) {
        var nodes = compiled.definition().getRoots().stream()
                .map(root -> buildNodeResult(root, runtime))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        var state = nodes.isEmpty() ? BehaviorStateEnum.FAILURE : nodes.get(0).getState();
        return TreeExecuteResultVO.builder()
                .treeApi(compiled.api())
                .state(state)
                .blackboard(runtime.snapshotBlackboard())
                .nodes(nodes)
                .build();
    }

    private BehaviorNodeResultVO buildNodeResult(BehaviorTreeNode node, TreeExecutionRuntime runtime) {
        var trace = runtime.getTrace(node.getId());
        if (trace == null) {
            return null;
        }
        var children = node.getChildren().stream()
                .map(child -> buildNodeResult(child, runtime))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return BehaviorNodeResultVO.builder()
                .nodeId(node.getId())
                .kind(node.getKind())
                .displayName(node.getDisplayName())
                .state(trace.getState() == null ? BehaviorStateEnum.SUCCESS : trace.getState())
                .skipped(trace.isSkipped())
                .message(trace.getMessage())
                .children(children)
                .build();
    }

    private String firstFailureMessage(List<BehaviorNodeResultVO> nodes) {
        for (var node : nodes) {
            if (node.getState() == BehaviorStateEnum.FAILURE && StringUtils.isNotBlank(node.getMessage())) {
                return node.getMessage();
            }
            var childMessage = firstFailureMessage(node.getChildren());
            if (StringUtils.isNotBlank(childMessage)) {
                return childMessage;
            }
        }
        return null;
    }

    /**
     * 行为节点：一次树执行共用一个实例主键，外部输入参数按参数名从黑板取，跑完再回填绑定了属性的黑板键。
     */
    private TreeActionInvoker.TreeActionOutcome invokeTreeNodeAction(Integer spaceId, BehaviorActionConfig config,
                                                                    TreeExecutionRuntime runtime) {
        var boundApi = config.getActionApi();
        var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, boundApi)
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        if (bound == null || bound.getActionKind() == ActionKindEnum.TREE
                || !Objects.equals(bound.getStatus(), ActionStatusEnum.PUBLISHED.getValue())) {
            return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.FAILURE,
                    "绑定的行为不可用（不存在、未发布或是树）：" + boundApi);
        }
        var testParam = ActionTestParam.builder()
                .ontologySpaceId(spaceId)
                .actionApi(boundApi)
                .entityPrimaryKey(runtime.getEntityPrimaryKey())
                // 节点配了黑板键就以节点为准，没配用执行请求里的默认值
                .dataPrimaryKey(StringUtils.isBlank(config.getDataPrimaryKeyKey())
                        ? runtime.getDataPrimaryKey() : runtime.getBlackboard().get(config.getDataPrimaryKeyKey()))
                .linkEntityPrimaryKey(StringUtils.isBlank(config.getLinkEntityPrimaryKeyKey())
                        ? runtime.getLinkEntityPrimaryKey() : runtime.getBlackboard().get(config.getLinkEntityPrimaryKeyKey()))
                .params(treeExternalParams(bound, runtime))
                .build();
        var testResult = testAction(testParam);
        refreshTreeBlackboard(bound, runtime);
        // 有关系的执行按对端逐条 try/catch，异常只回一个空串；节点不能把这种结果当成功
        var counterparts = countCounterpartOutcome(testResult.getResult());
        if (counterparts != null && counterparts.failed > 0) {
            return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.FAILURE,
                    "对端执行失败：" + counterparts.failed + "/" + counterparts.total + " 条结果为空");
        }
        // 失败重试由行为节点自己控制，这里只回单次执行结果
        return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.SUCCESS, testResult.getMessage());
    }

    /**
     * 行为执行结果按对端返回 JSON 数组；执行链路吞掉单条对端异常时该位置是空串。
     * <p>只在能解析成数组时统计，基础行为没有结果串，解析失败也不影响节点状态。</p>
     */
    private CounterpartOutcome countCounterpartOutcome(String result) {
        if (StringUtils.isBlank(result)) {
            return null;
        }
        try {
            var root = jsonMapper.readTree(result);
            if (root == null || !root.isArray()) {
                return null;
            }
            var failed = 0;
            for (var item : root) {
                if (item.isNull() || (item.isTextual() && StringUtils.isBlank(item.asText()))) {
                    failed++;
                }
            }
            return new CounterpartOutcome(root.size(), failed);
        } catch (Exception e) {
            log.warn("行为树节点执行结果解析失败", e);
            return null;
        }
    }

    private record CounterpartOutcome(int total, int failed) {
    }

    /**
     * 行为声明的外部输入参数按参数名从黑板取值；黑板里没有的键不传，与行为测试少传一个参数等价。
     */
    private Map<String, Object> treeExternalParams(OntologyAction bound, TreeExecutionRuntime runtime) {
        var mappings = ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>()
                .eq(OntologyActionMappingIn::getOntologyActionId, bound.getId()));
        Map<String, Object> params = new LinkedHashMap<>();
        mappings.stream()
                // 存量的来源为空按外部输入处理，与行为测试取值时的口径一致
                .filter(mapping -> mapping.getSourceType() == null || mapping.getSourceType() == ActionParamSourceEnum.EXTERNAL)
                .filter(mapping -> StringUtils.isNotBlank(mapping.getParameterName()))
                .filter(mapping -> runtime.getBlackboard().containsKey(mapping.getParameterName()))
                .forEach(mapping -> params.put(mapping.getParameterName(), runtime.getBlackboard().get(mapping.getParameterName())));
        return params;
    }

    /**
     * 写后替换：行为把值写进属性之后，用行上的最新值替换绑定了该属性的黑板键。
     * 只回填「本棵树绑了这个对象属性」的键，其余黑板键不动。
     */
    private void refreshTreeBlackboard(OntologyAction bound, TreeExecutionRuntime runtime) {
        // 没有实例主键就没有可回填的实例数据；真写属性的行为在上一步已经因为缺实例失败了
        if (runtime.getEntityPrimaryKey() == null) {
            return;
        }
        // 应用行为的回写方式下沉到每行：任一出参落属性，就说明这次执行可能改了实例属性
        var writesProperty = bound.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE
                || (bound.getActionKind() == ActionKindEnum.APPLICATION
                && ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>()
                        .eq(OntologyActionMappingIn::getOntologyActionId, bound.getId()))
                .stream().anyMatch(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY));
        if (!writesProperty || StringUtils.isBlank(bound.getOntologyUniqueIdentifier())) {
            return;
        }
        var boundProps = runtime.getBlackboardItems().values().stream()
                .map(BehaviorBlackboardItem::getBindPropertyUniqueIdentifier)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (boundProps.isEmpty()) {
            return;
        }
        var detail = entityService.getEntityPropertyRowDetail(EntityPropertyRowQueryParam.builder()
                .ontologyUniqueIdentifier(bound.getOntologyUniqueIdentifier())
                .entityPrimaryKey(runtime.getEntityPrimaryKey())
                .sort(QuerySortEnum.ASC)
                .build());
        if (detail == null || detail.getPropertyGroups() == null) {
            return;
        }
        Map<String, Object> latestValues = new LinkedHashMap<>();
        detail.getPropertyGroups().forEach(group -> {
            if (group.getGroupDataList() == null) {
                return;
            }
            group.getGroupDataList().stream()
                    .filter(row -> row.getProps() != null)
                    .forEach(row -> row.getProps().forEach(prop -> {
                        if (boundProps.contains(prop.getPropertyUniqIdentifier())) {
                            latestValues.put(prop.getPropertyUniqIdentifier(), prop.getPropertyValue());
                        }
                    }));
        });
        runtime.getBlackboardItems().forEach((key, item) -> {
            var bind = item.getBindPropertyUniqueIdentifier();
            if (StringUtils.isNotBlank(bind) && latestValues.containsKey(bind)) {
                runtime.setBlackboardValue(key, latestValues.get(bind));
            }
        });
    }

    /**
     * 按对象类型取全部实例主键。行为树没有对象绑定，不能走 initActionContextInfoDTO（它会拒绝树），
     * 直接从对象主键属性定位实体主表。
     */
    private List<Object> listTreeInstancePrimaryKeys(String ontologyUniqueIdentifier) {
        var primaryProperty = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                        .eq(OntologyProperty::getIsPrimaryKey, 1))
                .stream().findFirst().orElse(null);
        PreconditionUtils.checkNotNull(primaryProperty, "对象没有主键属性：" + ontologyUniqueIdentifier, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(primaryProperty.getDatasourceColumnName()),
                "对象主键未绑定数据源列：" + ontologyUniqueIdentifier, HttpStatus.BAD_REQUEST);
        var hasDeletedField = tableMetadataMapper.isColumnExist(primaryProperty.getDatasourceSchema(),
                primaryProperty.getDatasourceId(), "is_deleted");
        var records = entityMapper.pageQuery(primaryProperty.getDatasourceSchema(), primaryProperty.getDatasourceId(),
                Lists.newArrayList(primaryProperty.getDatasourceColumnName()), null, null,
                Integer.MAX_VALUE, 0, hasDeletedField);
        return records.stream()
                .map(record -> record.get(primaryProperty.getDatasourceColumnName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 行为被行为树引用时不允许删除：树里存的是行为 api，删掉后只能等执行时才暴露悬空引用。
     * <p>行为 api 只在空间内唯一，引用判定也必须限定在同一个空间，否则会误拦其他空间的同名行为。</p>
     */
    private List<String> findTreesReferencingAction(Integer ontologySpaceId, String actionApi) {
        var trees = list(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId)
                .eq(OntologyAction::getActionKind, ActionKindEnum.TREE)
                .select(OntologyAction::getApi, OntologyAction::getDefinition));
        return trees.stream()
                .filter(tree -> treeReferencesAction(readTreeDefinition(tree.getDefinition()).getRoots(), actionApi))
                .map(OntologyAction::getApi)
                .collect(Collectors.toList());
    }

    private Set<String> collectTreeNodeIds(List<BehaviorTreeNode> nodes) {
        Set<String> ids = new HashSet<>();
        collectTreeNodeIds(nodes, ids);
        return ids;
    }

    private void collectTreeNodeIds(List<BehaviorTreeNode> nodes, Set<String> ids) {
        for (var node : nodes) {
            if (StringUtils.isNotBlank(node.getId())) {
                ids.add(node.getId());
            }
            collectTreeNodeIds(node.getChildren(), ids);
        }
    }

    private boolean treeReferencesAction(List<BehaviorTreeNode> nodes, String actionApi) {
        for (var node : nodes) {
            if (node.getKind() == BehaviorNodeKindEnum.ACTION && node.getConfig() != null) {
                // 草稿树可能存着不完全合法的配置，读不出来的节点跳过，不影响其余节点的判定
                try {
                    var config = jsonMapper.convertValue(node.getConfig(), BehaviorActionConfig.class);
                    if (config != null && actionApi.equals(config.getActionApi())) {
                        return true;
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("行为树节点配置解析失败, nodeId={}", node.getId());
                }
            }
            if (treeReferencesAction(node.getChildren(), actionApi)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 绑定算子的行为（应用行为、基础算子行为）：校验算子类型、关联关系和入参绑定。
     * <p>关系是可选项：应用行为在出参全是「仅输出结果」时可以绑一条关系，执行时据此补一条实例关系。</p>
     *
     * @param allowedTypes 该类别允许的算子类型
     * @param spaceId      行为所属空间，算子 api 仅在该空间内唯一
     */
    private void checkFunctionAction(ActionCreateOrUpdateParam param, ActionDefinition definition,
                                     List<FunctionTypeEnum> allowedTypes, Integer spaceId) {
        var funcApi = param.getFunctionApi();
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(funcApi), "行为必须绑定算子", HttpStatus.BAD_REQUEST);
        // 算子 api 只保证空间内唯一（跨空间允许重名），取最新版本必须先限定在本空间，否则会选中其他空间的同名算子
        definition.func = functionMapper.selectList(new LambdaQueryWrapper<Function>()
                        .eq(Function::getOntologySpaceId, spaceId)
                        .eq(Function::getApi, funcApi))
                .stream()
                .max(Comparator.comparing((Function f) -> f.getVersion() == null ? "" : f.getVersion(),
                                FunctionServiceImpl::compareVersion)
                        .thenComparing(Function::getId))
                .orElse(null);
        PreconditionUtils.checkArgument(definition.func != null && allowedTypes.contains(definition.func.getType()),
                "function api 不存在或算子类型不匹配：" + funcApi, HttpStatus.BAD_REQUEST);
        definition.funcParams = functionParamMapper.selectList(new LambdaQueryWrapper<FunctionParamPO>()
                .eq(FunctionParamPO::getFunctionId, definition.func.getId()));
        checkActionLink(param, definition, false);

        var paramMap = definition.funcParams.stream()
                .collect(Collectors.toMap(FunctionParamPO::getId, v -> v, (existingValue, newValue) -> existingValue));
        if (CollectionUtils.isEmpty(param.getMappingIns())) {
            return;
        }
        for (var mapping : param.getMappingIns()) {
            var funcParam = paramMap.get(mapping.getFunctionParamId());
            PreconditionUtils.checkArgument(funcParam != null, "无效的函数参数：" + mapping.getFunctionParamId(), HttpStatus.BAD_REQUEST);
            var isInput = funcParam.getCategory().equals(FunctionParamCategoryEnum.INPUT);
            // 出参没有取值来源：sourceType 只对入参生效，不能套用入参的默认值与属性必填校验
            var sourceType = isInput
                    ? (mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType())
                    : null;
            // 基础算子的入参只能取对象属性或固定值：执行/调度链路没有外部入参入口，外部输入恒为 null
            PreconditionUtils.checkArgument(!(sourceType == ActionParamSourceEnum.EXTERNAL
                            && definition.func.getType() == FunctionTypeEnum.BASIC_QUERY),
                    "基础算子不支持外部输入参数：" + funcParam.getParamName(), HttpStatus.BAD_REQUEST);
            OntologyProperty property = null;
            if (StringUtils.isNotEmpty(mapping.getPropertyUniqueIdentifier())) {
                property = ontologyPropertyService.getOne(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getUniqueIdentifier, mapping.getPropertyUniqueIdentifier()));
            }
            // 固定值和外部输入没有来源属性，其余来源必须指向一个真实属性
            var needProperty = isInput && sourceType != ActionParamSourceEnum.FIXED && sourceType != ActionParamSourceEnum.EXTERNAL;
            PreconditionUtils.checkArgument(!needProperty || property != null,
                    "无效的行为参数：" + mapping.getPropertyUniqueIdentifier(), HttpStatus.BAD_REQUEST);
            var paramPath = normalizeParamPath(funcParam, mapping.getParamPath());
            if (property != null && isInput) {
                var dataType = OntologyDataTypeEnum.valueOfDataType(resolveInputParamType(funcParam, paramPath));
                // 对象/自定义类参数没有对应的属性数据类型，整包绑定时不做标量比对
                PreconditionUtils.checkArgument(dataType == null || dataType.equals(OntologyDataTypeEnum.Array)
                                || property.getPropertyType().equals(dataType),
                        "行为属性类型与函数参数类型不匹配：", HttpStatus.BAD_REQUEST);
            }
            // 出参写回：按行级 outputMode 判断，WRITE_PROPERTY 要求属性存在且归属该行指定的对象
            if (funcParam.getCategory().equals(FunctionParamCategoryEnum.OUTPUT)
                    && mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY) {
                PreconditionUtils.checkArgument(property != null,
                        "写入对象属性的出参必须指定目标属性：" + mapping.getFunctionParamId(), HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())
                                && mapping.getOntologyUniqueIdentifier().equals(property.getOntologyUniqueIdentifier()),
                        "出参写回的属性必须属于该行指定的对象：" + mapping.getPropertyUniqueIdentifier(), HttpStatus.BAD_REQUEST);
            }
            definition.mappings.add(OntologyActionMappingIn.builder()
                    .functionParamId(funcParam.getId())
                    .functionParamExpression(mapping.getFunctionParamExpression())
                    .parameterName(StringUtils.isEmpty(mapping.getParameterName()) ? funcParam.getParamName() : mapping.getParameterName())
                    .propertyUniqueIdentifier(mapping.getPropertyUniqueIdentifier())
                    .sourceType(sourceType)
                    .sourceConfig(mapping.getSourceConfig())
                    .paramPath(paramPath)
                    .outputMode(funcParam.getCategory().equals(FunctionParamCategoryEnum.OUTPUT)
                            ? (mapping.getOutputMode() != null ? mapping.getOutputMode() : ActionOutputModeEnum.RESULT_ONLY)
                            : null)
                    .ontologyUniqueIdentifier(mapping.getOntologyUniqueIdentifier())
                    .build());
        }
    }

    /** 子字段路径归一：去掉前端可能带上的参数名前缀，空白按「未绑定子字段」处理 */
    private String normalizeParamPath(FunctionParamPO funcParam, String paramPath) {
        if (StringUtils.isBlank(paramPath)) {
            return null;
        }
        return StringUtils.removeStart(paramPath.trim(), funcParam.getParamName() + ".");
    }

    /**
     * 取一条入参绑定实际对应的声明类型。
     * <p>
     * 对象（自定义类）参数在 function_param 表里只有一行，子字段整体存在该行的 fields JSON 中、
     * 没有自己的参数 id，所以只能按已归一的子字段路径在字段树里逐层定位。
     */
    private FunctionParamTypeEnum resolveInputParamType(FunctionParamPO funcParam, String paramPath) {
        if (StringUtils.isEmpty(paramPath)) {
            return funcParam.getParamType();
        }
        var fields = parseParamFields(funcParam);
        FunctionParamTypeEnum currentType = funcParam.getParamType();
        for (var segment : paramPath.split("\\.")) {
            var field = fields.stream().filter(f -> segment.equals(f.getParamName())).findFirst().orElse(null);
            PreconditionUtils.checkArgument(field != null,
                    "算子入参 " + funcParam.getParamName() + " 不存在子字段：" + paramPath, HttpStatus.BAD_REQUEST);
            currentType = field.getParamType();
            fields = field.getFields() == null ? Lists.newArrayList() : field.getFields();
        }
        return currentType;
    }

    /** function_param.fields 存的是 {@link FunctionParameterVO} 列表 JSON，解析不了说明算子参数定义本身已损坏，直接抛出 */
    @SneakyThrows
    private List<FunctionParameterVO> parseParamFields(FunctionParamPO funcParam) {
        if (StringUtils.isEmpty(funcParam.getFields())) {
            return Lists.newArrayList();
        }
        return jsonMapper.readValue(funcParam.getFields(), new TypeReference<List<FunctionParameterVO>>() {
        });
    }

    /** 至少有一个出参行选了 WRITE_PROPERTY 时，校验算子确实有出参可供写回 */
    private void checkWritePropertyOutput(ActionDefinition definition) {
        var hasWriteProperty = definition.mappings.stream()
                .anyMatch(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY);
        if (!hasWriteProperty) {
            return;
        }
        var outputParamIds = definition.funcParams.stream()
                .filter(p -> p.getCategory().equals(FunctionParamCategoryEnum.OUTPUT))
                .map(FunctionParamPO::getId)
                .collect(Collectors.toSet());
        PreconditionUtils.checkArgument(!outputParamIds.isEmpty(),
                "算子没有输出参数，无法写入对象属性：" + definition.func.getApi(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(definition.mappings.stream()
                        .anyMatch(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY
                                && outputParamIds.contains(m.getFunctionParamId())),
                "输出方式为写入对象属性时，至少要绑定一个输出参数", HttpStatus.BAD_REQUEST);
    }

    /**
     * 写入对象属性与绑定关系互斥：两者都是行为的落库目标，同时配置会既写属性又落关系边。
     * <p>出参全部为「仅输出结果」时才允许选择关系，关系本身是可选填的。</p>
     */
    private void checkOutputLinkExclusive(ActionCreateOrUpdateParam param, ActionDefinition definition) {
        var hasWriteProperty = definition.mappings.stream()
                .anyMatch(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY);
        PreconditionUtils.checkArgument(!hasWriteProperty
                        || param.getLinkMapping() == null
                        || StringUtils.isEmpty(param.getLinkMapping().getOntologyLinkUniqIdentifier()),
                "输出参数写入对象属性时，不能再绑定关系", HttpStatus.BAD_REQUEST);
    }

    /** 属性值行为：对某个存储分组（物理表）做新增/修改/删除，不绑算子 */
    private void checkPropertyValueAction(ActionCreateOrUpdateParam param, ActionDefinition definition) {
        PreconditionUtils.checkArgument(StringUtils.isEmpty(param.getFunctionApi()), "属性值行为不需要绑定算子", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getLinkMapping() == null
                        || StringUtils.isEmpty(param.getLinkMapping().getOntologyLinkUniqIdentifier()),
                "属性值行为不需要关联关系", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkNotNull(param.getOperation(), "属性值行为必须选择基础操作", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(param.getStorageGroup()), "属性值行为必须选择存储分组", HttpStatus.BAD_REQUEST);
        // main 分组只支持修改，不支持新增和删除（新增/删除走实体接口）
        if (MAIN_STORAGE_GROUP.equals(param.getStorageGroup())) {
            PreconditionUtils.checkArgument(param.getOperation() == ActionOperationEnum.UPDATE,
                    "属性值行为的 main 分组只支持修改，不支持新增和删除", HttpStatus.BAD_REQUEST);
        }

        var groupPropMap = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier()))
                .stream()
                .filter(p -> param.getStorageGroup().equals(p.getStorageGroup()))
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, v -> v, (existingValue, newValue) -> existingValue));
        PreconditionUtils.checkArgument(!groupPropMap.isEmpty(), "该对象不存在存储分组：" + param.getStorageGroup(), HttpStatus.BAD_REQUEST);
        // 主键由分组表自己的序列生成，不参与绑定
        var bindableProps = groupPropMap.entrySet().stream()
                .filter(entry -> entry.getValue().getIsPrimaryKey() == 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        checkPropertyMappings(param, definition, bindableProps);
        if (param.getOperation() == ActionOperationEnum.CREATE) {
            // 新增要求分组下属性全部绑定，漏一个就会写出结构不完整的行
            var boundIds = definition.mappings.stream()
                    .map(OntologyActionMappingIn::getPropertyUniqueIdentifier)
                    .collect(Collectors.toSet());
            var missing = bindableProps.entrySet().stream()
                    .filter(entry -> !boundIds.contains(entry.getKey()))
                    .map(entry -> entry.getValue().getDisplayName())
                    .collect(Collectors.joining("、"));
            PreconditionUtils.checkArgument(StringUtils.isEmpty(missing),
                    "属性值-新增必须绑定分组[" + param.getStorageGroup() + "]下的全部属性，缺少：" + missing, HttpStatus.BAD_REQUEST);
        }
    }

    /** 不绑算子的行为：每个参数行对应一个目标属性，取值来源缺省按外部输入 */
    private void checkPropertyMappings(ActionCreateOrUpdateParam param, ActionDefinition definition,
                                       Map<String, OntologyProperty> bindableProps) {
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(param.getMappingIns()), "请选择要操作的属性", HttpStatus.BAD_REQUEST);
        var boundIds = new HashSet<String>();
        for (var mapping : param.getMappingIns()) {
            var propertyId = mapping.getPropertyUniqueIdentifier();
            var property = bindableProps.get(propertyId);
            PreconditionUtils.checkNotNull(property, "无效的属性：" + propertyId, HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(boundIds.add(propertyId), "属性重复绑定：" + propertyId, HttpStatus.BAD_REQUEST);
            var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.EXTERNAL : mapping.getSourceType();
            // 对象/List 来源是把值组装进算子入参，属性值行为没有算子，定义存下来也无处取值
            PreconditionUtils.checkArgument(sourceType != ActionParamSourceEnum.OBJECT && sourceType != ActionParamSourceEnum.LIST,
                    "属性值行为的取值来源不支持" + sourceType.getName() + "，请用对象属性、固定值或外部输入：" + property.getDisplayName(),
                    HttpStatus.BAD_REQUEST);
            definition.mappings.add(OntologyActionMappingIn.builder()
                    .parameterName(StringUtils.isEmpty(mapping.getParameterName()) ? property.getApiName() : mapping.getParameterName())
                    .propertyUniqueIdentifier(propertyId)
                    .sourceType(sourceType)
                    .sourceConfig(mapping.getSourceConfig())
                    .functionParamExpression(mapping.getFunctionParamExpression())
                    .build());
        }
    }

    /** 实例关系行为：对某条关系做新增/修改/删除，不绑算子；修改的目标状态在行为定义里固定 */
    private void checkLinkAction(ActionCreateOrUpdateParam param, ActionDefinition definition) {
        PreconditionUtils.checkArgument(StringUtils.isEmpty(param.getFunctionApi()), "实例关系行为不需要绑定算子", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isEmpty(param.getStorageGroup()), "实例关系行为不需要存储分组", HttpStatus.BAD_REQUEST);
        var operation = param.getOperation();
        PreconditionUtils.checkNotNull(operation, "实例关系行为必须选择基础操作", HttpStatus.BAD_REQUEST);
        checkActionLink(param, definition, true);
        var visibilityWindows = param.getLinkMapping().getVisibilityWindows();
        if (operation == ActionOperationEnum.DELETE) {
            // 删除执行物理删边，状态和时段都不取定义里的值
            definition.actionLink.setTargetStatus(null);
            definition.actionLink.setTimeWindows(null);
        } else {
            checkVisibilityWindows(visibilityWindows);
            var targetStatus = definition.actionLink.getTargetStatus();
            if (operation == ActionOperationEnum.UPDATE) {
                PreconditionUtils.checkArgument(targetStatus != null && targetStatus != Status.DELETE,
                        "实例关系-修改必须指定目标状态（启用/停用）", HttpStatus.BAD_REQUEST);
            } else {
                // 新增允许把边建成 DELETE 状态位（不生效但存在），物理删除只由基础操作 DELETE 触发
                PreconditionUtils.checkNotNull(targetStatus, "实例关系-新增必须指定目标状态", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(visibilityWindows),
                        "实例关系-新增必须填写持续时段", HttpStatus.BAD_REQUEST);
            }
        }
        // 实例关系行为只按定义里的目标状态与持续时段改边，边上没有可写参数值的地方，创建时就不收
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(param.getMappingIns()),
                "实例关系行为不支持绑定参数，请只配置状态与持续时段", HttpStatus.BAD_REQUEST);
    }

    /**
     * 校验并收集行为关联的关系。
     * <p>
     * 同一条关系可以被多个行为关联（新增、修改、删除各是一个行为），不再限制一关系一行为。
     */
    private void checkActionLink(ActionCreateOrUpdateParam param, ActionDefinition definition, boolean required) {
        var linkMapping = param.getLinkMapping();
        var linkId = linkMapping == null ? null : linkMapping.getOntologyLinkUniqIdentifier();
        if (StringUtils.isEmpty(linkId)) {
            PreconditionUtils.checkArgument(!required, "该行为必须绑定一条对象关系", HttpStatus.BAD_REQUEST);
            return;
        }
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkId));
        PreconditionUtils.checkArgument(link != null, "无效的对象关系：" + linkId, HttpStatus.BAD_REQUEST);
        // 应用行为可不传 ontologyIdentifier（前端不选对象），此时跳过端点归属校验
        var ontologyId = param.getOntologyIdentifier();
        if (StringUtils.isNotEmpty(ontologyId)) {
            PreconditionUtils.checkArgument(ontologyId.equals(link.getOntologyUniqueIdentifierFrom())
                            || ontologyId.equals(link.getOntologyUniqueIdentifierTo()),
                    "无效的对象关系：" + linkId, HttpStatus.BAD_REQUEST);
        }
        definition.actionLink = OntologyActionLink.builder()
                .ontologyLinkUniqueIdentifier(linkId)
                .ontologyLinkParamExpression(linkMapping.getOntologyLinkFunctionParamExpression())
                .targetStatus(linkMapping.getTargetStatus())
                .timeWindows(writeTimeWindows(linkMapping.getVisibilityWindows()))
                .build();
    }

    /** 持续时段按 JSON 存在 ontology_action_link.time_windows，空列表与不填等价 */
    @SneakyThrows
    private String writeTimeWindows(List<VisibilityWindow> windows) {
        return CollectionUtils.isEmpty(windows) ? null : jsonMapper.writeValueAsString(windows);
    }

    /** 读回行为定义里的持续时段，未配置时返回空列表 */
    @SneakyThrows
    private List<VisibilityWindow> readTimeWindows(String timeWindows) {
        if (StringUtils.isEmpty(timeWindows)) {
            return Lists.newArrayList();
        }
        return jsonMapper.readValue(timeWindows, new TypeReference<List<VisibilityWindow>>() {
        });
    }

    /** 时段执行时原样写到图库的边上，要求每段起止齐全且结束晚于开始 */
    private void checkVisibilityWindows(List<VisibilityWindow> windows) {
        if (CollectionUtils.isEmpty(windows)) {
            return;
        }
        var invalid = windows.stream().anyMatch(w -> w.getStartTime() == null || w.getEndTime() == null
                || !w.getEndTime().after(w.getStartTime()));
        PreconditionUtils.checkArgument(!invalid, "持续时段需填写开始与结束时间，且结束时间晚于开始时间", HttpStatus.BAD_REQUEST);
    }

    private void saveActionChildren(Long actionId, ActionDefinition definition) {
        if (definition.actionLink != null) {
            definition.actionLink.setOntologyActionId(actionId);
            actionLinkMapper.insert(definition.actionLink);
        }
        if (CollectionUtils.isNotEmpty(definition.mappings)) {
            definition.mappings.forEach(mapping -> mapping.setOntologyActionId(actionId));
            ontologyActionMappingInService.saveBatch(definition.mappings);
        }
    }

    /**
     * 行为定义校验的产物：待落库的关联关系、参数绑定，以及本次绑定的算子上下文。
     */
    private static class ActionDefinition {
        private Function func;
        private List<FunctionParamPO> funcParams = new ArrayList<>();
        private OntologyActionLink actionLink;
        private final List<OntologyActionMappingIn> mappings = new ArrayList<>();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteActionByApi(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        // 调度归属行为靠 action_handle_rule.action_id / action_handle_task.action_id，
        // 这两列已在 alter_table_20260909.sql 中删除，按 id 反查会直接报错，
        // 待确认该列是否为误删后再恢复「行为在调度中不可删除」的校验
        var referencingTrees = findTreesReferencingAction(action.getOntologySpaceId(), actionApi);
        PreconditionUtils.checkArgument(referencingTrees.isEmpty(),
                "该行为被行为树引用，不能直接删除：" + String.join(", ", referencingTrees), HttpStatus.BAD_REQUEST);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, action.getId()));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, action.getId()));
        if (action.getActionKind() == ActionKindEnum.TREE) {
            behaviorTreeCooldownMapper.deleteByTree(action.getOntologySpaceId(), actionApi);
        }
        btTreeManager.invalidate(action.getOntologySpaceId(), actionApi);
    }

    @Override
    public Page<OntologyActionInfoVO> pageGetActionByOntologyId(Integer ontologySpaceId, String ontologyId, Integer categoryId,
                                                                String keyword, Integer status, List<ActionKindEnum> actionKinds,
                                                                Integer pageNum, Integer pageSize) {
        Page<OntologyActionInfoVO> result = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<OntologyAction> wrapper = new LambdaQueryWrapper<>();
        if (ontologySpaceId != null) {
            wrapper.eq(OntologyAction::getOntologySpaceId, ontologySpaceId);
        }
        if (StringUtils.isNotEmpty(ontologyId)) {
            wrapper.eq(OntologyAction::getOntologyUniqueIdentifier, ontologyId);
        }
        // 类别筛选：不传查全部（行为在前、树在后，靠这个参数分流）
        if (CollectionUtils.isNotEmpty(actionKinds)) {
            wrapper.in(OntologyAction::getActionKind, actionKinds);
        }
        if (categoryId != null) {
            wrapper.eq(OntologyAction::getActionCategoryId, categoryId);
        }
        // 关键词：行为名称、算子 api、描述
        if (StringUtils.isNotEmpty(keyword)) {
            wrapper.and(w -> w.like(OntologyAction::getDisplayName, keyword)
                    .or().like(OntologyAction::getFunctionApi, keyword)
                    .or().like(OntologyAction::getDescription, keyword));
        }
        // 状态过滤
        if (status != null) {
            wrapper.eq(OntologyAction::getStatus, status);
        }
        // 最新创建的排在前面。按自增主键倒序而不是 create_time 倒序：
        // 一是等价于创建顺序，二是 create_time 允许为空，PostgreSQL 的 DESC 默认 NULLS FIRST 会把老数据顶到最前
        wrapper.orderByDesc(OntologyAction::getId);
        var pageResult = page(new Page<>(pageNum, pageSize), wrapper);
        // 算子 api 只在空间内唯一，描述必须按行为所在空间取；不传空间筛选时一页可能跨多个空间，按空间分组各查一次。
        // 早期行为的空间列可能为空，用 HashMap 手动分组（groupingBy 遇到 null 键会抛 NPE）
        var apisBySpace = new HashMap<Integer, List<String>>();
        for (var action : pageResult.getRecords()) {
            if (StringUtils.isNotEmpty(action.getFunctionApi())) {
                apisBySpace.computeIfAbsent(action.getOntologySpaceId(), k -> new ArrayList<>()).add(action.getFunctionApi());
            }
        }
        var functionDescMap = new HashMap<String, String>();
        apisBySpace.forEach((spaceId, apis) -> functionService.mapDescriptionByApi(spaceId, apis)
                .forEach((api, desc) -> functionDescMap.put(functionDescKey(spaceId, api), desc)));
        List<OntologyActionInfoVO> records = pageResult.getRecords().stream()
                .map(v -> OntologyActionInfoVO.builder()
                        .id(v.getId())
                        .actionApi(v.getApi())
                        .description(v.getDescription())
                        .ontologyUniqIdentifier(v.getOntologyUniqueIdentifier())
                        .displayName(v.getDisplayName())
                        .icon(v.getIcon())
                        .functionApi(v.getFunctionApi())
                        .functionDescription(functionDescMap.get(functionDescKey(v.getOntologySpaceId(), v.getFunctionApi())))
                        .categoryId(v.getActionCategoryId())
                        .status(v.getStatus())
                        .actionKind(v.getActionKind())
                        .subjectType(v.getSubjectType())
                        .operation(v.getActionOperation())
                        .storageGroup(v.getStorageGroup())
                        .outputMode(v.getOutputMode())
                        .createTime(v.getCreateTime())
                        .updateTime(v.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
        result.setRecords(records).setTotal(pageResult.getTotal());
        return result;
    }

    /** 算子描述按「空间 + api」定位，跨空间的同名 api 各是各的算子 */
    private String functionDescKey(Integer spaceId, String functionApi) {
        return spaceId + ":" + functionApi;
    }

    @Override
    public OntologyActionDetailVO getActionByApi(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        var detailVO = OntologyActionDetailVO
                .builder()
                .id(action.getId())
                .ontologySpaceId(action.getOntologySpaceId())
                .functionApi(action.getFunctionApi())
                .functionDescription(functionService
                        .mapDescriptionByApi(action.getOntologySpaceId(), Collections.singletonList(action.getFunctionApi()))
                        .get(action.getFunctionApi()))
                .actionApi(action.getApi())
                .description(action.getDescription())
                .displayName(action.getDisplayName())
                .ontologyUniqIdentifier(action.getOntologyUniqueIdentifier())
                .icon(action.getIcon())
                .categoryId(action.getActionCategoryId())
                .status(action.getStatus())
                .actionKind(action.getActionKind())
                .subjectType(action.getSubjectType())
                .operation(action.getActionOperation())
                .storageGroup(action.getStorageGroup())
                .outputMode(action.getOutputMode())
                .createTime(action.getCreateTime())
                .updateTime(action.getUpdateTime())
                .build();
        // 行为树没有算子、关系和参数绑定，读回定义即可
        if (action.getActionKind() == ActionKindEnum.TREE) {
            detailVO.setTreeDefinition(readTreeDefinition(action.getDefinition()));
            return detailVO;
        }
        var link = actionLinkMapper.selectOne(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        if (link != null) {
            detailVO.setLinkMapping(ActionLinkMappingParam.builder()
                    .ontologyLinkFunctionParamExpression(link.getOntologyLinkParamExpression())
                    .ontologyLinkUniqIdentifier(link.getOntologyLinkUniqueIdentifier())
                    .targetStatus(link.getTargetStatus())
                    .visibilityWindows(readTimeWindows(link.getTimeWindows()))
                    .build());
        }
        var mappings = ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getOntologyActionId, action.getId()));

        // 函数参数定义（入参 + 出参），先取到以便给 mappingIns 每行填 category
        Map<Long, FunctionParamCategoryEnum> funcParamCategoryMap = new HashMap<>();
        if (StringUtils.isNotEmpty(action.getFunctionApi())) {
            var func = functionMapper.selectList(new LambdaQueryWrapper<Function>()
                            .eq(Function::getOntologySpaceId, action.getOntologySpaceId())
                            .eq(Function::getApi, action.getFunctionApi()))
                    .stream()
                    .max(Comparator.comparing((Function f) -> f.getVersion() == null ? "" : f.getVersion(),
                                    FunctionServiceImpl::compareVersion)
                            .thenComparing(Function::getId))
                    .orElse(null);
            if (func != null) {
                var funcParams = functionParamMapper.selectList(new LambdaQueryWrapper<FunctionParamPO>()
                        .eq(FunctionParamPO::getFunctionId, func.getId()));
                funcParams.forEach(p -> funcParamCategoryMap.put(p.getId(), p.getCategory()));
                detailVO.setFunctionParams(funcParams.stream().map(p -> FunctionParamVO.builder()
                        .paramId(p.getId())
                        .paramName(p.getParamName())
                        .paramType(p.getParamType())
                        .category(p.getCategory())
                        .paramOrder(p.getParamOrder())
                        .description(p.getDescription())
                        .referenceType(p.getTypeReferenceName())
                        .build()).collect(Collectors.toList()));
            }
        }

        if (CollectionUtils.isNotEmpty(mappings)) {

            var propUniqIds = mappings.stream().map(v -> v.getPropertyUniqueIdentifier()).collect(Collectors.toList());
            var props = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>().in(OntologyProperty::getUniqueIdentifier, propUniqIds));
            var propMap = props.stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v));

            var mappingVOS = mappings.stream().map(v -> {
                        var prop = propMap.get(v.getPropertyUniqueIdentifier());
                        return ActionParamMappingVO.builder()
                                .functionParamExpression(v.getFunctionParamExpression())
                                .functionParamId(v.getFunctionParamId())
                                .category(funcParamCategoryMap.get(v.getFunctionParamId()))
                                .parameterName(v.getParameterName())
                                .sourceType(v.getSourceType())
                                .sourceConfig(v.getSourceConfig())
                                .paramPath(v.getParamPath())
                                .propertyUniqueIdentifier(v.getPropertyUniqueIdentifier())
                                .propertyType(prop == null ? null : prop.getPropertyType())
                                .displayName(prop == null ? null : prop.getDisplayName())
                                .ontologyUniqueIdentifier(StringUtils.isNotEmpty(v.getOntologyUniqueIdentifier())
                                        ? v.getOntologyUniqueIdentifier() : (prop == null ? null : prop.getOntologyUniqueIdentifier()))
                                .outputMode(v.getOutputMode())
                                .build();
                    })
                    .collect(Collectors.toList());
            detailVO.setMappingIns(mappingVOS);
        }
        return detailVO;
    }

    @SneakyThrows
    @Override
    public String executeAction(OntologyActionExecuteParam param) {
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyUniqueIdentifier()));
        PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyUniqueIdentifier(), HttpStatus.BAD_REQUEST);

        var contextInfoDTO = entityService.initActionContextInfoDTO(EntityActionExecuteParam.builder()
                .actionApi(param.getActionApi())
                .ontologySpaceId(meta.getOntologySpaceId())
                .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                .build());
        // 校验行为属于该空间
        PreconditionUtils.checkArgument(contextInfoDTO.getActionDetailVO() != null
                && meta.getOntologySpaceId().equals(contextInfoDTO.getActionDetailVO().getOntologySpaceId()),
                "行为不属于该对象所在的空间", HttpStatus.BAD_REQUEST);
        checkBulkExecutable(contextInfoDTO.getActionDetailVO());
        //获取实体主键列表
        var primaryProperty = contextInfoDTO.getOntologyProperties().stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst().orElse(null);
        //主键数据源未绑定
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceColumnName())) {
            return "";
        }
        //查询对象的实体主键
        var hasDeletedField = tableMetadataMapper.isColumnExist(primaryProperty.getDatasourceSchema(), primaryProperty.getDatasourceId(), "is_deleted");
        var records = entityMapper.pageQuery(
                primaryProperty.getDatasourceSchema(),
                primaryProperty.getDatasourceId(),
                Lists.newArrayList(primaryProperty.getDatasourceColumnName()),
                null,
                null,
                Integer.MAX_VALUE,
                0,
                hasDeletedField);
        //执行每个实体的行为
        //获取行为关联关系下的对象的所有实体详情
        List<List<EntityPropertyDetailVO>> linkedEntities = entityService.getLinkedEntities(contextInfoDTO);
        //执行日志
        List<String> logMsg = Lists.newArrayList();
        var shardTotal = param.getShardTotal();
        var shardIndex = param.getShardIndex();
        for (int i = 0; i < records.size(); i++) {
            if ((shardIndex == null || shardTotal == null)
                    || (i % shardTotal == shardIndex)) {
                var record = records.get(i);
                var entityPrimaryKey = record.get(primaryProperty.getDatasourceColumnName());
                var entityActionExecuteParam = EntityActionExecuteParam.builder()
                        .actionApi(param.getActionApi())
                        .ontologySpaceId(meta.getOntologySpaceId())
                        .entityPrimaryKey(entityPrimaryKey)
                        .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                        .build();
                try {
                    entityService.executeEntityAction(entityActionExecuteParam, contextInfoDTO, linkedEntities);
                    var msg = "执行行为" + param.getActionApi() + "成功, OntologyUniqueIdentifier:" + param.getOntologyUniqueIdentifier() + ", entityPrimaryKey:" + entityPrimaryKey;
                    logMsg.add(msg);
                    log.info(msg);
                    XxlJobHelper.log(msg);
                } catch (Exception e) {
                    var errMsg = "执行行为" + param.getActionApi() + "失败, OntologyUniqueIdentifier:" + param.getOntologyUniqueIdentifier() + ", entityPrimaryKey:" + entityPrimaryKey;
                    log.error(errMsg, e);
                    XxlJobHelper.log(errMsg);
                    logMsg.add(errMsg);
                }
            }
        }
        return jsonMapper.writeValueAsString(logMsg);
    }

    /**
     * 按对象下全部实例批量执行时，每个实例只带得出实体主键，
     * 因此只放行取值不依赖分组数据主键的属性值操作；实例关系行为还必须指定对端实体。
     */
    private void checkBulkExecutable(OntologyActionDetailVO detail) {
        if (StringUtils.isNotEmpty(detail.getFunctionApi())) {
            return;
        }
        if (detail.getSubjectType() == ActionSubjectTypeEnum.LINK) {
            throw new BusinessException("实例关系行为要指定对端实体，不支持按对象下全部实例执行，请用单实体执行或行为测试："
                    + detail.getActionApi(), HttpStatus.BAD_REQUEST);
        }
        var operation = detail.getOperation();
        // main 分组的行主键就是实体主键，批量执行能定位到行；非 main 分组的修改和删除都需要分组数据主键
        var determinable = operation == ActionOperationEnum.CREATE
                || (operation == ActionOperationEnum.UPDATE && MAIN_STORAGE_GROUP.equals(detail.getStorageGroup()));
        PreconditionUtils.checkArgument(determinable,
                "属性值-" + (operation == null ? "" : operation.getName())
                        + "要指定分组数据主键，不支持按对象下全部实例执行，请用单实体执行或行为测试：" + detail.getActionApi(),
                HttpStatus.BAD_REQUEST);
    }

    /**
     * 行为测试：用一条真实数据把行为定义跑一遍并落库。
     * <p>
     * 不加事务：属性值行为写数据湖、实例关系行为写图库、算子行为跨两者，
     * 且发现必填属性缺失时的失效标记必须独立提交，不能被随后的异常回滚掉。
     */
    @Override
    public ActionTestResultVO testAction(ActionTestParam param) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, param.getOntologySpaceId()));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        // 草稿可以试跑（发布前正是要拿它验证定义）
        // 行为树编排的是已有行为，没有单条行为的入参绑定，走行为测试只会得到"算子不存在"这类误导提示
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.TREE,
                "行为树不支持按单个行为测试", HttpStatus.BAD_REQUEST);
        // 属性值/实例关系行为按行为所属对象定位实例；绑算子的行为按映射涉及对象从 entityPrimaryKeys 取，缺实例在执行前校验
        if (action.getActionKind() == ActionKindEnum.BASE
                && (action.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE
                || action.getSubjectType() == ActionSubjectTypeEnum.LINK)) {
            PreconditionUtils.checkArgument(param.getEntityPrimaryKey() != null, "行为测试必须提供实体主键", HttpStatus.BAD_REQUEST);
        }

        var detail = getActionByApi(param.getOntologySpaceId(), param.getActionApi());
        if (action.getActionKind() == ActionKindEnum.BASE) {
            if (action.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE) {
                return testPropertyValueAction(action, detail, param);
            }
            if (action.getSubjectType() == ActionSubjectTypeEnum.LINK) {
                return testLinkAction(action, detail, param);
            }
        }
        return testFunctionAction(action, detail, param);
    }

    /**
     * 执行不绑算子的基础行为：属性值行为对分组表增删改一行，实例关系行为改图库一条边的状态与时段。
     * 行为测试与实例执行共用这套执行体，差别只在外部输入取值是否由调用方给。
     */
    @Override
    public String executeBaseAction(EntityActionExecuteParam param, ActionContextInfoDTO infoDTO) {
        var detail = infoDTO.getActionDetailVO();
        var action = getById(detail.getId());
        PreconditionUtils.checkNotNull(action, "行为不存在：" + detail.getActionApi(), HttpStatus.BAD_REQUEST);
        return runBaseAction(action, detail, param, infoDTO.getExternalParams());
    }

    /** 按基础行为类型分发执行体 */
    private String runBaseAction(OntologyAction action, OntologyActionDetailVO detail,
                                 EntityActionExecuteParam param, Map<String, Object> externalParams) {
        var subjectType = action.getSubjectType();
        if (subjectType == ActionSubjectTypeEnum.PROPERTY_VALUE) {
            return runPropertyValueAction(action, detail.getMappingIns(), param, externalParams);
        }
        if (subjectType == ActionSubjectTypeEnum.LINK) {
            return runLinkAction(action, detail.getLinkMapping(), param);
        }
        throw new BusinessException("该行为需要绑定算子后才能执行：" + action.getApi(), HttpStatus.BAD_REQUEST);
    }

    /** 行为测试的入参形状与执行不同，这里把测试给的键包成执行用的单实体参数 */
    private EntityActionExecuteParam baseExecuteParam(OntologyAction action, Object entityPrimaryKey,
                                                      Object dataPrimaryKey, Object linkedEntityPrimaryKey) {
        return EntityActionExecuteParam.builder()
                .actionApi(action.getApi())
                .ontologySpaceId(action.getOntologySpaceId())
                .ontologyUniqueIdentifier(action.getOntologyUniqueIdentifier())
                .entityPrimaryKey(entityPrimaryKey)
                .dataPrimaryKey(dataPrimaryKey)
                .linkedEntityPrimaryKey(linkedEntityPrimaryKey)
                .build();
    }

    /** 属性值行为测试：对目标存储分组对应的物理表真实增删改一行 */
    private ActionTestResultVO testPropertyValueAction(OntologyAction action, OntologyActionDetailVO detail, ActionTestParam param) {
        var executeParam = baseExecuteParam(action, param.getEntityPrimaryKey(), param.getDataPrimaryKey(), null);
        return testResult(action, runPropertyValueAction(action, detail.getMappingIns(), executeParam, param.getParams()));
    }

    /** 属性值行为执行体：对 action 的存储分组按基础操作增删改一行 */
    private String runPropertyValueAction(OntologyAction action, List<ActionParamMappingVO> mappings,
                                          EntityActionExecuteParam param, Map<String, Object> externalParams) {
        var ontologyIdentifier = action.getOntologyUniqueIdentifier();
        var storageGroup = action.getStorageGroup();
        var operation = action.getActionOperation();
        var entityPrimaryKey = param.getEntityPrimaryKey();

        var groupProps = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier))
                .stream()
                .filter(v -> storageGroup.equals(v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(groupProps), "存储分组" + storageGroup + "未绑定数据源", HttpStatus.BAD_REQUEST);
        var propMap = groupProps.stream().collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, v -> v));

        if (operation == ActionOperationEnum.DELETE) {
            PreconditionUtils.checkNotNull(param.getDataPrimaryKey(), "属性值-删除必须提供分组数据主键", HttpStatus.BAD_REQUEST);
            entityService.deleteEntityProperty(EntityPropertyDeleteParam.builder()
                    .ontologyIdentifier(ontologyIdentifier)
                    .entityPrimaryKey(entityPrimaryKey)
                    .storageGroup(storageGroup)
                    .dataPrimaryKey(param.getDataPrimaryKey())
                    .build());
            return "已删除存储分组[" + storageGroup + "]中数据主键为 " + param.getDataPrimaryKey() + " 的属性行";
        }

        var values = resolvePropertyValues(mappings, ontologyIdentifier, entityPrimaryKey, externalParams);
        // 属性后来被挪出该分组时行为已跑不通，给明确提示而不是空指针
        var movedOut = values.keySet().stream().filter(id -> !propMap.containsKey(id))
                .collect(Collectors.joining("、"));
        PreconditionUtils.checkArgument(StringUtils.isEmpty(movedOut),
                "行为绑定的属性已不在存储分组[" + storageGroup + "]中，请重新编辑发布：" + movedOut, HttpStatus.BAD_REQUEST);
        List<EntityCreateParam.PropertyInfo> props = values.entrySet().stream()
                .map(entry -> EntityCreateParam.PropertyInfo.builder()
                        .propertyApiName(propMap.get(entry.getKey()).getApiName())
                        .propertyValue(entry.getValue())
                        .build())
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(!props.isEmpty(), "该行为没有绑定任何属性，无法执行", HttpStatus.BAD_REQUEST);

        if (operation == ActionOperationEnum.CREATE) {
            checkUnboundNotNullColumns(action, groupProps, values.keySet());
            entityService.insertEntityProperty(EntityPropertyInsertParam.builder()
                    .ontologyIdentifier(ontologyIdentifier)
                    .entityPrimaryKey(entityPrimaryKey)
                    .entityProperties(Collections.singletonList(EntityCreateParam.EntityProperty.builder()
                            .storageGroup(storageGroup)
                            .props(Collections.singletonList(props))
                            .build()))
                    .build());
            return "已向存储分组[" + storageGroup + "]新增 1 行属性数据，实体主键 " + entityPrimaryKey;
        }

        // main 分组的行主键就是实体主键，批量执行不传数据主键时按实体主键定位
        var dataPrimaryKey = param.getDataPrimaryKey() == null && MAIN_STORAGE_GROUP.equals(storageGroup)
                ? entityPrimaryKey : param.getDataPrimaryKey();
        PreconditionUtils.checkNotNull(dataPrimaryKey, "属性值-修改必须提供分组数据主键", HttpStatus.BAD_REQUEST);
        entityService.updateEntityProperty(EntityPropertyUpdateParam.builder()
                .ontologyIdentifier(ontologyIdentifier)
                .entityPrimaryKey(entityPrimaryKey)
                .storageGroup(storageGroup)
                .dataPrimaryKey(dataPrimaryKey)
                .props(props)
                .build());
        return "已更新存储分组[" + storageGroup + "]中数据主键 " + dataPrimaryKey + " 的 " + props.size() + " 个属性值";
    }

    /**
     * 行为定义之后分组里新增的必填属性，行为测试时会被写成 null；
     * 一旦命中非空约束，这个行为往后也跑不通，直接提示重新编辑发布。
     *
     * @param boundPropertyIds 行为定义里已绑定取值的属性
     */
    private void checkUnboundNotNullColumns(OntologyAction action, List<OntologyProperty> groupProps, Set<String> boundPropertyIds) {
        var notNullColumns = tableMetadataMapper.queryNotNullColumns(groupProps.get(0).getDatasourceSchema(), groupProps.get(0).getDatasourceId());
        if (CollectionUtils.isEmpty(notNullColumns)) {
            return;
        }
        var blocking = groupProps.stream()
                .filter(v -> v.getIsPrimaryKey() == 0)
                .filter(v -> !boundPropertyIds.contains(v.getUniqueIdentifier()))
                .filter(v -> notNullColumns.contains(v.getDatasourceColumnName()))
                .map(OntologyProperty::getDisplayName)
                .collect(Collectors.joining("、"));
        if (StringUtils.isEmpty(blocking)) {
            return;
        }
        throw new BusinessException("存储分组[" + action.getStorageGroup() + "]的必填属性未绑定取值："
                + blocking, HttpStatus.BAD_REQUEST);
    }

    /** 按参数绑定的来源取属性值：对象属性读实例当前值，固定值解析字面量，外部输入取调用方传入的参数 */
    private Map<String, Object> resolvePropertyValues(List<ActionParamMappingVO> mappings, String ontologyIdentifier,
                                                      Object entityPrimaryKey, Map<String, Object> externalParams) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(mappings)) {
            return values;
        }
        Map<String, List<Object>> entityDetail = null;
        for (var mapping : mappings) {
            var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.EXTERNAL : mapping.getSourceType();
            Object value;
            if (sourceType == ActionParamSourceEnum.PROPERTY) {
                if (entityDetail == null) {
                    entityDetail = entityService.getEntityDetail(ontologyIdentifier, entityPrimaryKey).stream()
                            .collect(Collectors.toMap(EntityPropertyDetailVO::getPropertyUniqIdentifier, EntityPropertyDetailVO::getPropertyValues));
                }
                var currentValues = entityDetail.get(mapping.getPropertyUniqueIdentifier());
                value = CollectionUtils.isEmpty(currentValues) ? null : currentValues.get(0);
            } else if (sourceType == ActionParamSourceEnum.FIXED) {
                value = parseFixedValue(mapping.getSourceConfig());
            } else if (sourceType == ActionParamSourceEnum.EXTERNAL) {
                value = externalParams == null ? null : externalParams.get(mapping.getParameterName());
            } else {
                throw new BusinessException("参数[" + mapping.getParameterName() + "]的输入来源暂不支持执行："
                        + sourceType.getName(), HttpStatus.BAD_REQUEST);
            }
            values.put(mapping.getPropertyUniqueIdentifier(), value);
        }
        return values;
    }

    /** 固定值按 JSON 字面量解析，不是合法 JSON 时按原文本返回 */
    private Object parseFixedValue(String sourceConfig) {
        if (StringUtils.isEmpty(sourceConfig)) {
            return null;
        }
        try {
            return jsonMapper.readValue(sourceConfig, Object.class);
        } catch (Exception e) {
            return sourceConfig;
        }
    }

    /** 实例关系行为测试：真实写入/改状态/失效图库的一条边 */
    private ActionTestResultVO testLinkAction(OntologyAction action, OntologyActionDetailVO detail, ActionTestParam param) {
        var executeParam = baseExecuteParam(action, param.getEntityPrimaryKey(), null, param.getLinkEntityPrimaryKey());
        return testResult(action, runLinkAction(action, detail.getLinkMapping(), executeParam));
    }

    /** 实例关系行为执行体：按定义的目标状态与持续时段改图库里的那条边 */
    private String runLinkAction(OntologyAction action, ActionLinkMappingParam linkMapping, EntityActionExecuteParam param) {
        PreconditionUtils.checkArgument(linkMapping != null, "行为没有绑定关系，无法执行", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getLinkedEntityPrimaryKey() != null, "实例关系行为必须提供对端实体主键", HttpStatus.BAD_REQUEST);
        var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkMapping.getOntologyLinkUniqIdentifier()));
        PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + linkMapping.getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);

        // 行为挂在对象的一侧，执行给的实体主键就落在关系的那一端
        var selfIsFrom = action.getOntologyUniqueIdentifier().equals(linkGroup.getOntologyUniqueIdentifierFrom());
        var primaryKeyFrom = selfIsFrom ? param.getEntityPrimaryKey() : param.getLinkedEntityPrimaryKey();
        var primaryKeyTo = selfIsFrom ? param.getLinkedEntityPrimaryKey() : param.getEntityPrimaryKey();
        var linkId = linkMapping.getOntologyLinkUniqIdentifier();
        var operation = action.getActionOperation();

        if (operation == ActionOperationEnum.DELETE) {
            entityService.deleteEntityRelation(linkId, primaryKeyFrom, primaryKeyTo);
            return "已删除实例关系[" + linkId + "]";
        }
        // 新增按定义写状态与时段；修改只改定义里填了的项，没填的保留边上原值
        var definedStatus = linkMapping.getTargetStatus();
        // 改造前建的新增行为状态被强制留空，这里按当时的固定值 ENABLE 处理，不让老行为执行结果发生变化
        var status = operation == ActionOperationEnum.UPDATE ? definedStatus
                : (definedStatus == null ? Status.ENABLE : definedStatus);
        entityService.updateEntityRelation(EntityRelationUpdateParam.builder()
                .linkUniqIdentifier(linkId)
                .entityPrimaryKeyFrom(primaryKeyFrom)
                .entityPrimaryKeyTo(primaryKeyTo)
                .visibilityWindows(linkMapping.getVisibilityWindows())
                .status(status)
                .partialUpdate(operation == ActionOperationEnum.UPDATE)
                .build());
        return operation == ActionOperationEnum.UPDATE
                ? "已将实例关系[" + linkId + "]状态改为 " + status.getName()
                : "已写入实例关系[" + linkId + "]（状态 " + status.getName() + "）";
    }

    /** 算子类行为测试：走既有执行链路，算子输出按输出方式真实回写 */
    @SneakyThrows
    private ActionTestResultVO testFunctionAction(OntologyAction action, OntologyActionDetailVO detail, ActionTestParam param) {
        var executeParamBuilder = EntityActionExecuteParam.builder()
                .actionApi(action.getApi())
                .ontologySpaceId(action.getOntologySpaceId())
                .ontologyUniqueIdentifier(action.getOntologyUniqueIdentifier())
                .entityPrimaryKey(param.getEntityPrimaryKey())
                .entityPrimaryKeys(param.getEntityPrimaryKeys());
        // 指定了对端实例时只跑这一对，否则会遍历关系下的全部实体
        if (detail.getLinkMapping() != null && param.getLinkEntityPrimaryKey() != null) {
            var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getUniqueIdentifier, detail.getLinkMapping().getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + detail.getLinkMapping().getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
            // 对端是关系的另一端，不是固定的 To：行为可能挂在关系的 To 侧
            executeParamBuilder.linkedOntologyUniqueIdentifier(entityService.counterpartOntology(linkGroup, action.getOntologyUniqueIdentifier()))
                    .linkedEntityPrimaryKey(param.getLinkEntityPrimaryKey());
        }
        var executeParam = executeParamBuilder.build();

        var contextInfoDTO = entityService.initActionContextInfoDTO(executeParam);
        contextInfoDTO.setExternalParams(param.getParams());
        var result = entityService.executeEntityAction(executeParam, contextInfoDTO, entityService.getLinkedEntities(contextInfoDTO));
        // 出参可能是 WRITE_PROPERTY 落库，也可能只是 RESULT_ONLY 返回，提示不描述落库
        return testResult(action, "行为执行成功").setResult(result);
    }

    private ActionTestResultVO testResult(OntologyAction action, String message) {
        return ActionTestResultVO.builder()
                .actionApi(action.getApi())
                .message(message)
                .build();
    }

    @Override
    @Transactional
    public Long createScheduling(ActionSchedulingCreateParam param) {
        //todo 目前只考虑定时调度
        var schedulingType = param.getType();
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
        PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, meta.getOntologySpaceId()));
        PreconditionUtils.checkNotNull(action, "action不存在：" + param.getActionApi());
        // 属性值、实例关系行为没有算子，执行时会对对象下全部实例逐个落库，定时跑会造成重复数据，不支持调度
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(action.getFunctionApi()),
                "不绑定算子的行为不支持配置调度：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        Long id = null;
        //允许同一个行为配置多个调度策略
        if (schedulingType.equals(ActionSchedulingTypeEnum.TASK)) {
            var taskParam = param.getTask();
            var actionHandleTask = ActionHandleTask.builder()
                    .actionId(action.getId())
                    .cron(taskParam.getTaskCronExpression())
                    .name(param.getName())
                    .description(param.getDescription())
                    .status(ScheduleStatus.STOP)
                    .build();
            actionHandleTaskService.save(actionHandleTask);
            id = actionHandleTask.getId();
            try {
                var jonInfoId = xxlJobClient.createJobInfo(param.getName(),
                        taskParam.getTaskCronExpression(),
                        XxlJobActionExecuteParam.builder()
                                .actionApi(param.getActionApi())
                                .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                                .scheduleId(id)
                                .build());
                actionHandleTaskService.updateById(actionHandleTask.setRemark(jonInfoId));
            } catch (Exception e) {
                log.error("createScheduling failed", e);
                throw new BusinessException("远程调用xxl-job创建jobinfo失败");
            }
        }
        return id;
    }

    @Override
    @Transactional
    public void updateScheduling(ActionSchedulingUpdateParam param) {
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
        PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, meta.getOntologySpaceId()));
        PreconditionUtils.checkNotNull(action, "action不存在：" + param.getActionApi());
        //todo 目前只考虑定时调度
        var schedulingType = param.getType();
        if (schedulingType.equals(ActionSchedulingTypeEnum.TASK)) {
            var handleTask = actionHandleTaskService.getById(param.getId());
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + param.getId());
            handleTask.setActionId(action.getId())
                    .setDescription(param.getDescription())
                    .setCron(param.getTask().getTaskCronExpression())
                    .setName(param.getName());
            actionHandleTaskService.updateById(handleTask);
            try {
                xxlJobClient.updateJobInfo(
                        handleTask.getRemark(),
                        param.getName(),
                        param.getTask().getTaskCronExpression(),
                        XxlJobActionExecuteParam.builder()
                                .scheduleId(param.getId())
                                .actionApi(param.getActionApi())
                                .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                                .build());
            } catch (Exception e) {
                log.error("updateJobInfo failed", e);
                throw new BusinessException("远程调用xxl-job更新jobinfo失败");
            }
        }
    }

    @Override
    @Transactional
    public void removeScheduling(ActionSchedulingTypeEnum type, Long id) {
        //todo 只考虑定时任务
        if (ActionSchedulingTypeEnum.TASK.equals(type)) {
            var handleTask = actionHandleTaskService.getById(id);
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + id);
            actionHandleTaskService.removeById(handleTask.getId());
            try {
                xxlJobClient.removeJob(handleTask.getRemark());
            } catch (Exception e) {
                log.error("removeScheduling failed", e);
                throw new BusinessException("远程调用xxl-job删除任务失败");
            }
        }
    }

    @Override
    @Transactional
    public void startScheduling(ActionSchedulingTypeEnum type, Long id) {
        //todo 只考虑定时任务
        if (ActionSchedulingTypeEnum.TASK.equals(type)) {
            var handleTask = actionHandleTaskService.getById(id);
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + id);
            actionHandleTaskService.updateById(handleTask.setStatus(ScheduleStatus.START));
            try {
                xxlJobClient.startJob(handleTask.getRemark());
            } catch (Exception e) {
                log.error("startScheduling failed", e);
                throw new BusinessException("远程调用xxl-job启动任务失败");
            }
        }
    }

    @Override
    @Transactional
    public void stopScheduling(ActionSchedulingTypeEnum type, Long id) {
        //todo 只考虑定时任务
        if (ActionSchedulingTypeEnum.TASK.equals(type)) {
            var handleTask = actionHandleTaskService.getById(id);
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + id);
            actionHandleTaskService.updateById(handleTask.setStatus(ScheduleStatus.STOP));
            try {
                xxlJobClient.stopJob(handleTask.getRemark());
            } catch (Exception e) {
                log.error("stopScheduling failed", e);
                throw new BusinessException("远程调用xxl-job暂停任务失败");
            }
        }

    }

    @Override
    public ActionSchedulingDetailVO getSchedulingDetailById(Long id, ActionSchedulingTypeEnum type) {
        //todo 只考虑定时任务
        if (ActionSchedulingTypeEnum.TASK.equals(type)) {
            var handleTask = actionHandleTaskService.getById(id);
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + id);
            var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getId, handleTask.getActionId()));
            var ontology = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, action.getOntologyUniqueIdentifier()));
            return ActionSchedulingDetailVO.builder()
                    .actionApi(action.getApi())
                    .actionName(action.getDisplayName())
                    .ontologyName(ontology.getDisplayName())
                    .schedulingName(handleTask.getName())
                    .id(handleTask.getId())
                    .description(handleTask.getDescription())
                    .type(ActionSchedulingTypeEnum.TASK)
                    .ontologyIdentifier(action.getOntologyUniqueIdentifier())
                    .taskVO(ActionHandleTaskInfoVO.builder().taskCronExpression(handleTask.getCron()).build())
                    .status(handleTask.getStatus())
                    .build();
        }
        return null;
    }

    @Override
    public Page<ActionSchedulingInfoVO> listScheduling(Integer pageNum, Integer pageSize) {
        //todo 只考虑定时任务
        var pageInfo = new Page<ActionHandleTask>(pageNum, pageSize).addOrder(OrderItem.desc("id"));
        var pages = actionHandleTaskService.page(pageInfo);
        var records = pages.getRecords();

        if (CollectionUtils.isEmpty(records)) {
            return new Page<ActionSchedulingInfoVO>(pageNum, pageSize);
        }

        var actionIds = records.stream().map(v -> v.getActionId()).collect(Collectors.toList());
        var actionMap = actionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, actionIds))
                .stream().collect(Collectors.toMap(v -> v.getId(), v -> v, (existingValue, newValue) -> existingValue));

        var ontologyUniqIds = actionMap.values().stream().map(v -> v.getOntologyUniqueIdentifier()).collect(Collectors.toList());
        var ontologyMap = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, ontologyUniqIds))
                .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v, (existingValue, newValue) -> existingValue));


        var vo = records.stream().<ActionSchedulingInfoVO>map(v -> {
            var actionId = v.getActionId();
            var action = actionMap.get(actionId);
            var ontologyMeta = ontologyMap.get(action.getOntologyUniqueIdentifier());
            return ActionSchedulingInfoVO.builder()
                    .actionApi(action.getApi())
                    .actionName(action.getDisplayName())
                    .ontologyName(ontologyMeta.getDisplayName())
                    .schedulingName(v.getName())
                    .id(v.getId())
                    .description(v.getDescription())
                    .type(ActionSchedulingTypeEnum.TASK)
                    .ontologyIdentifier(ontologyMeta.getUniqueIdentifier())
                    .status(v.getStatus())
                    .build();

        }).collect(Collectors.toList());


        return new Page<ActionSchedulingInfoVO>(pageNum, pageSize)
                .setTotal(pages.getTotal())
                .setRecords(vo);

    }

    @Override
    public Page<SchedulingResultVO> getSchedulingResult(Long id, ActionSchedulingTypeEnum type, Integer pageNum, Integer pageSize) {
        //todo 只考虑定时任务
        var pageInfo = new Page<ActionHandleLog>(pageNum, pageSize).addOrder(OrderItem.desc("id"));
        var pages = actionHandleLogMapper.selectPage(pageInfo, new LambdaQueryWrapper<ActionHandleLog>()
                .eq(ActionHandleLog::getActionHandleId, id)
                .eq(ActionHandleLog::getActionHandleType, ActionSchedulingTypeEnum.TASK));
        var records = pages.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            return new Page<>(pageNum, pageSize);
        }
        var vo = records.stream().map(v -> SchedulingResultVO.builder()
                        .msg(v.getMsg())
                        .taskStatus(v.getTaskStatus())
                        .requestParam(v.getRequestParam())
                        .triggerTime(v.getTriggerTime())
                        .completeTime(v.getCompleteTime())
                        .build())
                .collect(Collectors.toList());

        return new Page<SchedulingResultVO>(pageNum, pageSize)
                .setTotal(pages.getTotal())
                .setRecords(vo);
    }


}
