package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.client.XxlJobClient;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.enums.*;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.*;
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
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /** 黑板是 Map，表达式按黑板键取值靠它；禁写，防止表达式把黑板当变量池改 */
    private static final MapAccessor TREE_MAP_ACCESSOR = new MapAccessor(false);

    /** 行为树里能做大小、等于比较的数值类型 */
    private static final Set<FunctionParamTypeEnum> TREE_NUMBER_TYPES =
            EnumSet.of(FunctionParamTypeEnum.INTEGER, FunctionParamTypeEnum.LONG,
                    FunctionParamTypeEnum.FLOAT, FunctionParamTypeEnum.DOUBLE);


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


    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public void removeByOntologyIdentifier(String ontologyIdentifier) {
        var actions = list(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, ontologyIdentifier));
        if (CollectionUtils.isNotEmpty(actions)) {
            var actionIds = actions.stream().map(v -> v.getId()).collect(Collectors.toList());
            actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().in(OntologyActionLink::getOntologyActionId, actionIds));
            remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, actionIds));
            ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, actionIds));
            actionHandleRuleService.remove(new LambdaQueryWrapper<ActionHandleRule>().in(ActionHandleRule::getActionId, actionIds));
            //删除定时调度任务
            var tasks = actionHandleTaskService.list(new LambdaQueryWrapper<ActionHandleTask>().in(ActionHandleTask::getActionId, actionIds));
            tasks.forEach(t -> removeScheduling(ActionSchedulingTypeEnum.TASK, t.getId()));
            actionHandleTaskService.remove(new LambdaQueryWrapper<ActionHandleTask>().in(ActionHandleTask::getActionId, actionIds));
        }
    }


    @Override
    @Transactional(value = "mainTransactionManager")
    public void createAction(ActionCreateOrUpdateParam param) {
        var exist = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, param.getActionApi()));
        PreconditionUtils.checkArgument(exist == null, "action api 已存在：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        var action = new OntologyAction();
        var definition = checkActionDefinition(action, param);
        save(action);
        saveActionChildren(action.getId(), definition);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateAction(ActionCreateOrUpdateParam param) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, param.getActionApi()));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        var definition = checkActionDefinition(action, param);
        // 原地更新：行为 id 被关联关系、参数绑定和调度任务引用，删除重建会让它们失去引用
        updateById(action);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getOntologyActionId, action.getId()));
        saveActionChildren(action.getId(), definition);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void publishTree(String actionApi, Integer status) {
        PreconditionUtils.checkArgument(status != null
                        && (status == Status.ENABLE.getValue() || status == Status.DISABLE.getValue()),
                "状态只能传 1 发布或 0 下线", HttpStatus.BAD_REQUEST);
        var action = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, actionApi));
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
        }
        action.setStatus(status);
        updateById(action);
    }

    /**
     * 校验行为定义：校验通过的类别写入 action，待落库的关联关系与参数绑定收集进返回值。
     */
    private ActionDefinition checkActionDefinition(OntologyAction action, ActionCreateOrUpdateParam param) {
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
        // 原来由参数基类的 @NotBlank 兜底，共用入口后移到 service，提示文案保持一致
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(param.getOntologyIdentifier()), "ontologyIdentifier is empty", HttpStatus.BAD_REQUEST);
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
        PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
        // 归属的行为分类必须存在，且与对象同属一个对象空间
        var category = actionCategoryMapper.selectById(param.getCategoryId());
        PreconditionUtils.checkArgument(category != null, "无效的行为分类：" + param.getCategoryId(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(category.getOntologySpaceId().equals(meta.getOntologySpaceId()),
                "行为分类不属于该对象所在的空间：" + category.getName(), HttpStatus.BAD_REQUEST);

        // 类别、输出方式必须由调用方明确传入：猜错会把行为建成另一种，执行时才暴露很难定位
        PreconditionUtils.checkNotNull(param.getActionKind(), "必须选择行为类别：基础行为/应用行为", HttpStatus.BAD_REQUEST);
        var kind = param.getActionKind();
        var definition = new ActionDefinition();
        if (kind == ActionKindEnum.APPLICATION) {
            PreconditionUtils.checkArgument(param.getSubjectType() == null && param.getOperation() == null
                    && StringUtils.isEmpty(param.getStorageGroup()), "应用行为不需要行为类型、基础操作和存储分组", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkNotNull(param.getOutputMode(), "应用行为必须选择输出方式", HttpStatus.BAD_REQUEST);
            checkFunctionAction(param, definition, APPLICATION_FUNCTION_TYPES, true, meta.getOntologySpaceId());
            if (param.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY) {
                checkWritePropertyOutput(definition);
            }
            action.setOutputMode(param.getOutputMode());
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
                        false, meta.getOntologySpaceId());
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
        action.setOntologySpaceId(meta.getOntologySpaceId());
        action.setActionCategoryId(param.getCategoryId());
        action.setActionKind(kind);
        action.setSubjectType(param.getSubjectType());
        action.setActionOperation(param.getOperation());
        action.setStorageGroup(param.getStorageGroup());
        // 编辑即重新发布：因目标表新增必填属性而失效的行为，改完恢复可用
        action.setStatus(Status.ENABLE.getValue());
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
            action.setStatus(Status.DISABLE.getValue());
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
                }
            }
            case UNTIL_SUCCESS, UNTIL_FAILURE -> {
                var config = readTreeConfig(node, BehaviorUntilConfig.class);
                checkTreePositive(config.getMaxTimes(), "节点 " + nodeId + " 的最大重试次数");
                PreconditionUtils.checkNotNull(config.getFinishState(), "节点 " + nodeId + " 必须选择次数耗尽后的收尾状态", HttpStatus.BAD_REQUEST);
            }
            case COOLDOWN -> {
                var config = readTreeConfig(node, BehaviorCooldownConfig.class);
                PreconditionUtils.checkNotNull(config.getIntervalSeconds(), "节点 " + nodeId + " 必须填写冷却时长", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getIntervalSeconds(), "节点 " + nodeId + " 的冷却时长");
                PreconditionUtils.checkNotNull(config.getHitResult(), "节点 " + nodeId + " 必须选择冷却期内的结果状态", HttpStatus.BAD_REQUEST);
            }
            case TIME_LIMIT -> {
                var config = readTreeConfig(node, BehaviorTimeLimitConfig.class);
                PreconditionUtils.checkNotNull(config.getLimitSeconds(), "节点 " + nodeId + " 必须填写限时时长", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getLimitSeconds(), "节点 " + nodeId + " 的限时时长");
                PreconditionUtils.checkNotNull(config.getTimeoutState(), "节点 " + nodeId + " 必须选择超时后的状态", HttpStatus.BAD_REQUEST);
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
                checkTreePositive(config.getTimeoutSeconds(), "行为节点 " + nodeId + " 的超时时长");
                checkTreePositive(config.getRetryTimes(), "行为节点 " + nodeId + " 的重试次数");
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
     */
    private void checkTreeBoundAction(String nodeId, String boundApi, Integer spaceId) {
        var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, boundApi)
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(bound != null, "行为节点 " + nodeId + " 绑定的行为在本空间不存在：" + boundApi, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(bound.getActionKind() != ActionKindEnum.TREE,
                "行为节点只能绑定行为，不能嵌套行为树：" + boundApi, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(bound.getStatus(), Status.ENABLE.getValue()),
                "行为节点 " + nodeId + " 绑定的行为未发布：" + boundApi, HttpStatus.BAD_REQUEST);
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

    @Override
    public TreeExecuteResultVO executeTree(TreeExecuteParam param) {
        var tree = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, param.getTreeApi()));
        PreconditionUtils.checkArgument(tree != null, "无效的action api", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(tree.getActionKind() == ActionKindEnum.TREE,
                "按行为执行的接口不能跑行为树：" + param.getTreeApi(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(tree.getStatus(), Status.ENABLE.getValue()),
                "行为树未发布，不能执行：" + param.getTreeApi(), HttpStatus.BAD_REQUEST);
        var definition = readTreeDefinition(tree.getDefinition());
        PreconditionUtils.checkArgument(definition.getRoots().size() == 1,
                "行为树定义不完整，根节点数量不是 1", HttpStatus.BAD_REQUEST);
        var context = initTreeContext(definition, param, tree.getOntologySpaceId());
        var rootResult = evaluateTreeNode(definition.getRoots().get(0), context);
        return TreeExecuteResultVO.builder()
                .treeApi(tree.getApi())
                .state(rootResult.getState())
                .blackboard(context.blackboard)
                .nodes(Collections.singletonList(rootResult))
                .build();
    }

    /**
     * 初始化黑板：变量默认值打底，执行入参覆盖；黑板是节点唯一的数据来源。
     */
    private TreeExecuteContext initTreeContext(BehaviorTreeDefinition definition, TreeExecuteParam param, Integer spaceId) {
        var context = new TreeExecuteContext();
        context.spaceId = spaceId;
        context.entityPrimaryKey = param.getEntityPrimaryKey();
        definition.getBlackboard().forEach(item -> context.blackboardItems.put(item.getKey(), item));
        definition.getVariables().forEach(variable -> context.blackboard.put(variable.getName(), variable.getDefaultValue()));
        var inputs = param.getInputs();
        if (inputs == null) {
            return context;
        }
        for (var entry : inputs.entrySet()) {
            PreconditionUtils.checkArgument(context.blackboard.containsKey(entry.getKey()),
                    "执行入参 " + entry.getKey() + " 不是本树声明的黑板键", HttpStatus.BAD_REQUEST);
            var item = context.blackboardItems.get(entry.getKey());
            // 绑了对象属性的键由行为写入，再接受外部入参会出现「传了但马上被覆盖」的假象
            PreconditionUtils.checkArgument(item == null || StringUtils.isBlank(item.getBindPropertyUniqueIdentifier()),
                    "黑板键 " + entry.getKey() + " 绑的是对象属性，值由行为写入，不接受执行入参", HttpStatus.BAD_REQUEST);
            context.blackboard.put(entry.getKey(), entry.getValue());
        }
        return context;
    }

    /**
     * 单轮求值：每个节点只求值一次，不产生 RUNNING，跨 Tick 的循环/冷却/限时按「跑一遍子节点」降级。
     */
    private BehaviorNodeResultVO evaluateTreeNode(BehaviorTreeNode node, TreeExecuteContext context) {
        var result = BehaviorNodeResultVO.builder()
                .nodeId(node.getId())
                .kind(node.getKind())
                .displayName(node.getDisplayName())
                .state(BehaviorStateEnum.SUCCESS)
                .children(new ArrayList<>())
                .build();
        if (!node.isEnabled()) {
            return result.setSkipped(true).setMessage("节点未启用，按跳过处理");
        }
        if (StringUtils.isNotBlank(node.getSkipIf())) {
            var message = new String[1];
            if (evalTreeCondition(node.getSkipIf(), context, message)) {
                return result.setSkipped(true).setMessage(message[0] == null ? "跳过条件成立" : message[0]);
            }
            if (message[0] != null) {
                return result.setState(BehaviorStateEnum.FAILURE).setMessage(message[0]);
            }
        }
        switch (node.getKind().getCategory()) {
            case COMPOSITE -> evaluateTreeComposite(node, context, result);
            case DECORATOR -> evaluateTreeDecorator(node, context, result);
            default -> evaluateTreeLeaf(node, context, result);
        }
        return result;
    }

    private void evaluateTreeComposite(BehaviorTreeNode node, TreeExecuteContext context, BehaviorNodeResultVO result) {
        var children = node.getChildren();
        if (node.getKind() == BehaviorNodeKindEnum.PARALLEL) {
            var config = readTreeConfig(node, BehaviorParallelConfig.class);
            var successCount = 0;
            var failureCount = 0;
            for (var child : children) {
                var childResult = evaluateTreeNode(child, context);
                result.getChildren().add(childResult);
                if (childResult.getState() == BehaviorStateEnum.SUCCESS) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
            var allSuccess = failureCount == 0;
            var anySuccess = successCount > 0;
            var allFailure = successCount == 0;
            var anyFailure = failureCount > 0;
            var hit = switch (config.getPolicy()) {
                case ALL_SUCCESS -> allSuccess;
                case ANY_SUCCESS -> anySuccess;
                case ALL_FAILURE -> allFailure;
                case ANY_FAILURE -> anyFailure;
            };
            result.setState(hit ? BehaviorStateEnum.SUCCESS : BehaviorStateEnum.FAILURE);
            return;
        }
        // 选择与顺序按短路语义：定论出现就不再跑后面的兄弟节点
        var requireSuccess = node.getKind() == BehaviorNodeKindEnum.SEQUENCE;
        for (var child : children) {
            var childResult = evaluateTreeNode(child, context);
            result.getChildren().add(childResult);
            var childSuccess = childResult.getState() == BehaviorStateEnum.SUCCESS;
            // 顺序碰到失败就定论，选择碰到成功就定论
            if (requireSuccess != childSuccess) {
                result.setState(childSuccess ? BehaviorStateEnum.SUCCESS : BehaviorStateEnum.FAILURE);
                return;
            }
        }
        // 顺序全成功即成功；选择全部失败即失败
        result.setState(requireSuccess ? BehaviorStateEnum.SUCCESS : BehaviorStateEnum.FAILURE);
    }

    private void evaluateTreeDecorator(BehaviorTreeNode node, TreeExecuteContext context, BehaviorNodeResultVO result) {
        var childResult = evaluateTreeNode(node.getChildren().get(0), context);
        result.getChildren().add(childResult);
        var childState = childResult.getState();
        switch (node.getKind()) {
            case INVERTER -> result.setState(childState == BehaviorStateEnum.SUCCESS
                    ? BehaviorStateEnum.FAILURE : BehaviorStateEnum.SUCCESS);
            case FORCE_SUCCESS -> result.setState(childState == BehaviorStateEnum.FAILURE
                    ? BehaviorStateEnum.SUCCESS : childState);
            case UNTIL_SUCCESS -> {
                var config = readTreeConfig(node, BehaviorUntilConfig.class);
                result.setState(childState == BehaviorStateEnum.SUCCESS
                        ? BehaviorStateEnum.SUCCESS : config.getFinishState());
                result.setMessage(singleRoundNote(childState == BehaviorStateEnum.SUCCESS
                        ? null : "单轮只尝试一次，未达成即按收尾状态上报"));
            }
            case UNTIL_FAILURE -> {
                var config = readTreeConfig(node, BehaviorUntilConfig.class);
                result.setState(childState == BehaviorStateEnum.FAILURE
                        ? BehaviorStateEnum.SUCCESS : config.getFinishState());
                result.setMessage(singleRoundNote(childState == BehaviorStateEnum.FAILURE
                        ? null : "单轮只尝试一次，未达成即按收尾状态上报"));
            }
            case REPEATER -> {
                result.setState(childState);
                result.setMessage(singleRoundNote(null));
            }
            case COOLDOWN -> {
                result.setState(childState);
                // 冷却窗口要记住上次执行时间，而变量只有 RUNTIME 作用域、不跨执行持久，这里只能视为未命中
                result.setMessage(singleRoundNote("变量不跨执行持久，冷却期无法记住上次执行时间，按未命中处理"));
            }
            case TIME_LIMIT -> {
                result.setState(childState);
                result.setMessage(singleRoundNote("单轮执行为同步串行，限时不生效"));
            }
            default -> result.setState(childState);
        }
    }

    private String singleRoundNote(String extra) {
        return extra == null ? "单轮执行：本节点的跨 Tick 配置未生效" : "单轮执行：" + extra;
    }

    private void evaluateTreeLeaf(BehaviorTreeNode node, TreeExecuteContext context, BehaviorNodeResultVO result) {
        switch (node.getKind()) {
            case CONDITION -> {
                var config = readTreeConfig(node, BehaviorConditionConfig.class);
                var message = new String[1];
                var hit = evalTreeCondition(config.getExpression(), context, message);
                if (message[0] != null) {
                    result.setState(BehaviorStateEnum.FAILURE).setMessage(message[0]);
                } else {
                    result.setState(hit ? BehaviorStateEnum.SUCCESS : BehaviorStateEnum.FAILURE);
                }
            }
            case BLACKBOARD_CONDITION -> evaluateTreeBlackboardCondition(node, context, result);
            default -> executeTreeNodeAction(node, context, result);
        }
    }

    private void evaluateTreeBlackboardCondition(BehaviorTreeNode node, TreeExecuteContext context, BehaviorNodeResultVO result) {
        var config = readTreeConfig(node, BehaviorBlackboardConditionConfig.class);
        var actual = context.blackboard.get(config.getKey());
        var reason = new String[1];
        var hit = compareTreeValues(config.getOp(), actual, config.getValue(), reason);
        if (reason[0] != null) {
            result.setState(BehaviorStateEnum.FAILURE).setMessage(reason[0]);
            return;
        }
        result.setState(hit ? BehaviorStateEnum.SUCCESS : BehaviorStateEnum.FAILURE);
        if (!hit) {
            result.setMessage("黑板条件不成立：" + config.getKey() + " " + config.getOp().getName() + " " + config.getValue());
        }
    }

    /**
     * 按比较方式比黑板值与配置值；类型对不上只把原因写进 messageBox 并返回不成立，由调用方定节点状态，不抛异常打断整棵树。
     */
    private boolean compareTreeValues(BehaviorCompareOpEnum op, Object actual, Object expect, String[] reason) {
        if (op == BehaviorCompareOpEnum.IS_EMPTY) {
            return isTreeEmptyValue(actual);
        }
        if (op == BehaviorCompareOpEnum.IS_NOT_EMPTY) {
            return !isTreeEmptyValue(actual);
        }
        if (actual == null) {
            reason[0] = "黑板值为空，无法参与 " + op.getName() + " 比较";
            return false;
        }
        if (op == BehaviorCompareOpEnum.CONTAINS || op == BehaviorCompareOpEnum.NOT_CONTAINS) {
            var contained = isTreeContained(actual, expect);
            return op == BehaviorCompareOpEnum.CONTAINS ? contained : !contained;
        }
        if (op == BehaviorCompareOpEnum.EQ || op == BehaviorCompareOpEnum.NE) {
            var equal = String.valueOf(actual).equals(String.valueOf(expect));
            if (!equal) {
                try {
                    equal = Double.compare(Double.parseDouble(String.valueOf(actual)), Double.parseDouble(String.valueOf(expect))) == 0;
                } catch (NumberFormatException ignored) {
                    // 两边都不是数字，按字符串比过就够了
                }
            }
            return op == BehaviorCompareOpEnum.EQ ? equal : !equal;
        }
        double left;
        double right;
        try {
            left = Double.parseDouble(String.valueOf(actual));
            right = Double.parseDouble(String.valueOf(expect));
        } catch (NumberFormatException e) {
            reason[0] = "黑板值 " + actual + " 不是数值，无法按 " + op.getName() + " 比较";
            return false;
        }
        var compared = Double.compare(left, right);
        return switch (op) {
            case GT -> compared > 0;
            case GE -> compared >= 0;
            case LT -> compared < 0;
            case LE -> compared <= 0;
            default -> false;
        };
    }

    private boolean isTreeEmptyValue(Object value) {
        return value == null || StringUtils.isBlank(String.valueOf(value));
    }

    private boolean isTreeContained(Object actual, Object expect) {
        if (actual instanceof List<?> list) {
            return list.stream().anyMatch(item -> Objects.equals(String.valueOf(item), String.valueOf(expect)));
        }
        return String.valueOf(actual).contains(String.valueOf(expect));
    }

    /**
     * 表达式求值：黑板作根对象、只读属性访问，不给类型构造和方法调用开口（用户在 UI 里写表达式）。
     *
     * @param messageBox 求值失败时把原因写进第 0 位，由调用方决定节点状态
     */
    private boolean evalTreeCondition(String expression, TreeExecuteContext context, String[] messageBox) {
        try {
            var evaluationContext = SimpleEvaluationContext
                    .forPropertyAccessors(TREE_MAP_ACCESSOR, DataBindingPropertyAccessor.forReadOnlyAccess())
                    .withRootObject(context.blackboard)
                    .build();
            return Boolean.TRUE.equals(TREE_EXPRESSION_PARSER.parseExpression(expression)
                    .getValue(evaluationContext));
        } catch (Exception e) {
            messageBox[0] = "表达式求值失败：" + expression;
            log.warn("行为树表达式求值失败, expression={}", expression, e);
            return false;
        }
    }

    /**
     * 行为节点：一次树执行共用一个实例主键，外部输入参数按参数名从黑板取，跑完再回填绑定了属性的黑板键。
     */
    private void executeTreeNodeAction(BehaviorTreeNode node, TreeExecuteContext context, BehaviorNodeResultVO result) {
        var config = readTreeConfig(node, BehaviorActionConfig.class);
        var boundApi = config.getActionApi();
        var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, boundApi)
                .eq(OntologyAction::getOntologySpaceId, context.spaceId));
        if (bound == null || bound.getActionKind() == ActionKindEnum.TREE
                || !Objects.equals(bound.getStatus(), Status.ENABLE.getValue())) {
            result.setState(BehaviorStateEnum.FAILURE).setMessage("绑定的行为不可用（不存在、未发布或是树）：" + boundApi);
            return;
        }
        var testParam = ActionTestParam.builder()
                .actionApi(boundApi)
                .entityPrimaryKey(context.entityPrimaryKey)
                .dataPrimaryKey(StringUtils.isBlank(config.getDataPrimaryKeyKey())
                        ? null : context.blackboard.get(config.getDataPrimaryKeyKey()))
                .linkEntityPrimaryKey(StringUtils.isBlank(config.getLinkEntityPrimaryKeyKey())
                        ? null : context.blackboard.get(config.getLinkEntityPrimaryKeyKey()))
                .params(treeExternalParams(bound, context))
                .build();
        try {
            var testResult = testAction(testParam);
            var note = config.getTimeoutSeconds() == null && config.getRetryTimes() == null ? null
                    : "单轮执行：行为是真实落库的，重试会重复写一条数据，超时与重试配置不生效";
            var message = testResult.getMessage();
            result.setMessage(StringUtils.isBlank(note) ? message
                    : (StringUtils.isBlank(message) ? note : message + "；" + note));
            refreshTreeBlackboard(bound, context);
        } catch (Exception e) {
            result.setState(BehaviorStateEnum.FAILURE)
                    .setMessage("行为执行失败：" + (e instanceof BusinessException business ? business.getMessage() : e.getMessage()));
            log.warn("行为树节点执行失败, nodeId={}, actionApi={}", node.getId(), boundApi, e);
        }
    }

    /**
     * 行为声明的外部输入参数按参数名从黑板取值；黑板里没有的键不传，与行为测试少传一个参数等价。
     */
    private Map<String, Object> treeExternalParams(OntologyAction bound, TreeExecuteContext context) {
        var mappings = ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>()
                .eq(OntologyActionMappingIn::getOntologyActionId, bound.getId()));
        Map<String, Object> params = new LinkedHashMap<>();
        mappings.stream()
                // 存量的来源为空按外部输入处理，与行为测试取值时的口径一致
                .filter(mapping -> mapping.getSourceType() == null || mapping.getSourceType() == ActionParamSourceEnum.EXTERNAL)
                .filter(mapping -> StringUtils.isNotBlank(mapping.getParameterName()))
                .filter(mapping -> context.blackboard.containsKey(mapping.getParameterName()))
                .forEach(mapping -> params.put(mapping.getParameterName(), context.blackboard.get(mapping.getParameterName())));
        return params;
    }

    /**
     * 写后替换：行为把值写进属性之后，用行上的最新值替换绑定了该属性的黑板键，之后节点读黑板就拿到的新值。
     * 只回填「本棵树绑了这个对象属性」的键，其余黑板键不动。
     */
    private void refreshTreeBlackboard(OntologyAction bound, TreeExecuteContext context) {
        var writesProperty = bound.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE
                || (bound.getActionKind() == ActionKindEnum.APPLICATION
                && bound.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY);
        if (!writesProperty || StringUtils.isBlank(bound.getOntologyUniqueIdentifier())) {
            return;
        }
        var boundProps = context.blackboardItems.values().stream()
                .map(BehaviorBlackboardItem::getBindPropertyUniqueIdentifier)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (boundProps.isEmpty()) {
            return;
        }
        var detail = entityService.getEntityPropertyRowDetail(EntityPropertyRowQueryParam.builder()
                .ontologyUniqueIdentifier(bound.getOntologyUniqueIdentifier())
                .entityPrimaryKey(context.entityPrimaryKey)
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
        context.blackboardItems.forEach((key, item) -> {
            var bind = item.getBindPropertyUniqueIdentifier();
            if (StringUtils.isNotBlank(bind) && latestValues.containsKey(bind)) {
                context.blackboard.put(key, latestValues.get(bind));
            }
        });
    }

    /**
     * 一次树执行的上下文：黑板值 + 黑板项声明 + 执行的那条实例数据。
     */
    private static class TreeExecuteContext {
        /** 黑板键名 -> 当前值，节点唯一的数据来源 */
        private final Map<String, Object> blackboard = new LinkedHashMap<>();
        /** 黑板键名 -> 黑板项声明，写后替换时按 bindPropertyUniqueIdentifier 找 */
        private final Map<String, BehaviorBlackboardItem> blackboardItems = new LinkedHashMap<>();
        private Integer spaceId;
        private Object entityPrimaryKey;
    }



    /**
     * 绑定算子的行为（应用行为、基础算子行为）：校验算子类型、关联关系和入参绑定。
     *
     * @param allowedTypes 该类别允许的算子类型
     * @param linkRequired 应用行为必须绑定一条关系，执行时据此补一条实例关系
     * @param spaceId      行为所属空间，算子 api 仅在该空间内唯一
     */
    private void checkFunctionAction(ActionCreateOrUpdateParam param, ActionDefinition definition,
                                     List<FunctionTypeEnum> allowedTypes, boolean linkRequired, Integer spaceId) {
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
        checkActionLink(param, definition, linkRequired);

        var paramMap = definition.funcParams.stream()
                .collect(Collectors.toMap(FunctionParamPO::getId, v -> v, (existingValue, newValue) -> existingValue));
        if (CollectionUtils.isEmpty(param.getMappingIns())) {
            return;
        }
        for (var mapping : param.getMappingIns()) {
            var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
            OntologyProperty property = null;
            if (StringUtils.isNotEmpty(mapping.getPropertyUniqueIdentifier())) {
                property = ontologyPropertyService.getOne(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getUniqueIdentifier, mapping.getPropertyUniqueIdentifier()));
            }
            // 固定值和外部输入没有来源属性，其余来源必须指向一个真实属性
            var needProperty = sourceType != ActionParamSourceEnum.FIXED && sourceType != ActionParamSourceEnum.EXTERNAL;
            PreconditionUtils.checkArgument(!needProperty || property != null,
                    "无效的行为参数：" + mapping.getPropertyUniqueIdentifier(), HttpStatus.BAD_REQUEST);
            var funcParam = paramMap.get(mapping.getFunctionParamId());
            PreconditionUtils.checkArgument(funcParam != null, "无效的函数参数：" + mapping.getFunctionParamId(), HttpStatus.BAD_REQUEST);
            var paramPath = normalizeParamPath(funcParam, mapping.getParamPath());
            if (property != null && funcParam.getCategory().equals(FunctionParamCategoryEnum.INPUT)) {
                var dataType = OntologyDataTypeEnum.valueOfDataType(resolveInputParamType(funcParam, paramPath));
                // 对象/自定义类参数没有对应的属性数据类型，整包绑定时不做标量比对
                PreconditionUtils.checkArgument(dataType == null || dataType.equals(OntologyDataTypeEnum.Array)
                                || property.getPropertyType().equals(dataType),
                        "行为属性类型与函数参数类型不匹配：", HttpStatus.BAD_REQUEST);
            }
            // 出参写回落库时只在本对象自己的属性表里找列，绑到别的对象上执行时会取不到属性，必须在这里挡住
            if (funcParam.getCategory().equals(FunctionParamCategoryEnum.OUTPUT)
                    && param.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY) {
                PreconditionUtils.checkArgument(property != null
                                && param.getOntologyIdentifier().equals(property.getOntologyUniqueIdentifier()),
                        "出参写回的属性必须属于该行为所属的对象：" + mapping.getPropertyUniqueIdentifier(), HttpStatus.BAD_REQUEST);
            }
            definition.mappings.add(OntologyActionMappingIn.builder()
                    .functionParamId(funcParam.getId())
                    .functionParamExpression(mapping.getFunctionParamExpression())
                    .parameterName(StringUtils.isEmpty(mapping.getParameterName()) ? funcParam.getParamName() : mapping.getParameterName())
                    .propertyUniqueIdentifier(mapping.getPropertyUniqueIdentifier())
                    .sourceType(sourceType)
                    .sourceConfig(mapping.getSourceConfig())
                    .paramPath(paramPath)
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

    /** 写入对象属性要求算子有出参，且定义里至少把一个出参绑到了属性 */
    private void checkWritePropertyOutput(ActionDefinition definition) {
        var outputParamIds = definition.funcParams.stream()
                .filter(p -> p.getCategory().equals(FunctionParamCategoryEnum.OUTPUT))
                .map(FunctionParamPO::getId)
                .collect(Collectors.toSet());
        PreconditionUtils.checkArgument(!outputParamIds.isEmpty(),
                "算子没有输出参数，无法写入对象属性：" + definition.func.getApi(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(definition.mappings.stream().anyMatch(m -> outputParamIds.contains(m.getFunctionParamId())),
                "输出方式为写入对象属性时，至少要绑定一个输出参数", HttpStatus.BAD_REQUEST);
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
            definition.mappings.add(OntologyActionMappingIn.builder()
                    .parameterName(StringUtils.isEmpty(mapping.getParameterName()) ? property.getApiName() : mapping.getParameterName())
                    .propertyUniqueIdentifier(propertyId)
                    .sourceType(mapping.getSourceType() == null ? ActionParamSourceEnum.EXTERNAL : mapping.getSourceType())
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
            // 删除只是把边置为删除态，状态和时段都不取定义里的值
            definition.actionLink.setTargetStatus(null);
            definition.actionLink.setTimeWindows(null);
        } else {
            checkVisibilityWindows(visibilityWindows);
            var targetStatus = definition.actionLink.getTargetStatus();
            if (operation == ActionOperationEnum.UPDATE) {
                PreconditionUtils.checkArgument(targetStatus != null && targetStatus != Status.DELETE,
                        "实例关系-修改必须指定目标状态（启用/停用）", HttpStatus.BAD_REQUEST);
            } else {
                // 新增允许直接建成删除态的边：删除态只是不生效，不是消失，语义交给使用者定
                PreconditionUtils.checkNotNull(targetStatus, "实例关系-新增必须指定目标状态", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(visibilityWindows),
                        "实例关系-新增必须填写持续时段", HttpStatus.BAD_REQUEST);
            }
        }
        // 关系两端主键等输入项没有属性可指向，用参数名标识，取值来自固定值或外部输入
        if (CollectionUtils.isEmpty(param.getMappingIns())) {
            return;
        }
        for (var mapping : param.getMappingIns()) {
            PreconditionUtils.checkArgument(StringUtils.isNotEmpty(mapping.getParameterName()),
                    "实例关系行为的参数必须填写参数名", HttpStatus.BAD_REQUEST);
            definition.mappings.add(OntologyActionMappingIn.builder()
                    .parameterName(mapping.getParameterName())
                    .propertyUniqueIdentifier(mapping.getPropertyUniqueIdentifier())
                    .sourceType(mapping.getSourceType() == null ? ActionParamSourceEnum.EXTERNAL : mapping.getSourceType())
                    .sourceConfig(mapping.getSourceConfig())
                    .functionParamExpression(mapping.getFunctionParamExpression())
                    .build());
        }
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
        var ontologyId = param.getOntologyIdentifier();
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkId));
        PreconditionUtils.checkArgument(link != null && (ontologyId.equals(link.getOntologyUniqueIdentifierFrom())
                        || ontologyId.equals(link.getOntologyUniqueIdentifierTo())),
                "无效的对象关系：" + linkId, HttpStatus.BAD_REQUEST);
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
    public void deleteActionByApi(String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, actionApi));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        var actionHandleRule = actionHandleRuleService.getOne(new LambdaQueryWrapper<ActionHandleRule>()
                .eq(ActionHandleRule::getActionId, action.getId()));
        var actionHandleTask = actionHandleTaskService.getOne(new LambdaQueryWrapper<ActionHandleTask>()
                .eq(ActionHandleTask::getActionId, action.getId()));
        PreconditionUtils.checkArgument(actionHandleRule == null && actionHandleTask == null, "该行为在调度中，不能直接删除", HttpStatus.BAD_REQUEST);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, action.getId()));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, action.getId()));
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
        var pageResult = page(new Page<>(pageNum, pageSize), wrapper);
        // 一次批量取函数描述，避免逐条查函数表
        var functionDescMap = functionService.mapDescriptionByApi(pageResult.getRecords().stream()
                .map(OntologyAction::getFunctionApi)
                .collect(Collectors.toList()));
        List<OntologyActionInfoVO> records = pageResult.getRecords().stream()
                .map(v -> OntologyActionInfoVO.builder()
                        .id(v.getId())
                        .actionApi(v.getApi())
                        .description(v.getDescription())
                        .ontologyUniqIdentifier(v.getOntologyUniqueIdentifier())
                        .displayName(v.getDisplayName())
                        .icon(v.getIcon())
                        .functionApi(v.getFunctionApi())
                        .functionDescription(functionDescMap.get(v.getFunctionApi()))
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

    @Override
    public OntologyActionDetailVO getActionByApi(String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, actionApi));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        var detailVO = OntologyActionDetailVO
                .builder()
                .id(action.getId())
                .ontologySpaceId(action.getOntologySpaceId())
                .functionApi(action.getFunctionApi())
                .functionDescription(functionService
                        .mapDescriptionByApi(Collections.singletonList(action.getFunctionApi()))
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

        if (CollectionUtils.isNotEmpty(mappings)) {

            var propUniqIds = mappings.stream().map(v -> v.getPropertyUniqueIdentifier()).collect(Collectors.toList());
            var props = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>().in(OntologyProperty::getUniqueIdentifier, propUniqIds));
            var propMap = props.stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v.getOntologyUniqueIdentifier()));

            var mappingVOS = mappings.stream().map(v -> ActionParamMappingVO.builder()
                            .functionParamExpression(v.getFunctionParamExpression())
                            .functionParamId(v.getFunctionParamId())
                            .parameterName(v.getParameterName())
                            .sourceType(v.getSourceType())
                            .sourceConfig(v.getSourceConfig())
                            .paramPath(v.getParamPath())
                            .propertyUniqueIdentifier(v.getPropertyUniqueIdentifier())
                            .ontologyUniqueIdentifier(propMap.get(v.getPropertyUniqueIdentifier()))
                            .build())
                    .collect(Collectors.toList());
            detailVO.setMappingIns(mappingVOS);
        }
        // 函数参数定义（入参 + 出参），前端绑定 UI 用；算子 api 跨空间允许重名，必须按本行为所属空间取
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
        return detailVO;
    }

    @SneakyThrows
    @Override
    public String executeAction(OntologyActionExecuteParam param) {

        var contextInfoDTO = entityService.initActionContextInfoDTO(EntityActionExecuteParam.builder()
                .actionApi(param.getActionApi())
                .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                .build());
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
            if ((shardIndex == null || shardIndex == null)
                    || (i % shardTotal == shardIndex)) {
                var record = records.get(i);
                var entityPrimaryKey = record.get(primaryProperty.getDatasourceColumnName());
                var entityActionExecuteParam = EntityActionExecuteParam.builder()
                        .actionApi(param.getActionApi())
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
     * 行为测试：用一条真实数据把行为定义跑一遍并落库。
     * <p>
     * 不加事务：属性值行为写数据湖、实例关系行为写图库、算子行为跨两者，
     * 且发现必填属性缺失时的失效标记必须独立提交，不能被随后的异常回滚掉。
     */
    @Override
    public ActionTestResultVO testAction(ActionTestParam param) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, param.getActionApi()));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(action.getStatus(), Status.ENABLE.getValue()),
                "行为已失效，请重新编辑发布后再进行行为测试", HttpStatus.BAD_REQUEST);
        // 行为树编排的是已有行为，没有单条行为的入参绑定，走行为测试只会得到"算子不存在"这类误导提示
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.TREE,
                "行为树不支持按单个行为测试", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getEntityPrimaryKey() != null, "行为测试必须提供实体主键", HttpStatus.BAD_REQUEST);

        var detail = getActionByApi(param.getActionApi());
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

    /** 属性值行为测试：对目标存储分组对应的物理表真实增删改一行 */
    private ActionTestResultVO testPropertyValueAction(OntologyAction action, OntologyActionDetailVO detail, ActionTestParam param) {
        var ontologyIdentifier = action.getOntologyUniqueIdentifier();
        var storageGroup = action.getStorageGroup();
        var operation = action.getActionOperation();

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
                    .entityPrimaryKey(param.getEntityPrimaryKey())
                    .storageGroup(storageGroup)
                    .dataPrimaryKey(param.getDataPrimaryKey())
                    .build());
            return testResult(action, "已删除存储分组[" + storageGroup + "]中数据主键为 " + param.getDataPrimaryKey() + " 的属性行");
        }

        var values = resolvePropertyValues(detail.getMappingIns(), ontologyIdentifier, param);
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
        PreconditionUtils.checkArgument(!props.isEmpty(), "该行为没有绑定任何属性，无法进行行为测试", HttpStatus.BAD_REQUEST);

        if (operation == ActionOperationEnum.CREATE) {
            checkUnboundNotNullColumns(action, groupProps, values.keySet());
            entityService.insertEntityProperty(EntityPropertyInsertParam.builder()
                    .ontologyIdentifier(ontologyIdentifier)
                    .entityPrimaryKey(param.getEntityPrimaryKey())
                    .entityProperties(Collections.singletonList(EntityCreateParam.EntityProperty.builder()
                            .storageGroup(storageGroup)
                            .props(Collections.singletonList(props))
                            .build()))
                    .build());
            return testResult(action, "已向存储分组[" + storageGroup + "]新增 1 行属性数据，实体主键 " + param.getEntityPrimaryKey());
        }

        PreconditionUtils.checkNotNull(param.getDataPrimaryKey(), "属性值-修改必须提供分组数据主键", HttpStatus.BAD_REQUEST);
        entityService.updateEntityProperty(EntityPropertyUpdateParam.builder()
                .ontologyIdentifier(ontologyIdentifier)
                .entityPrimaryKey(param.getEntityPrimaryKey())
                .storageGroup(storageGroup)
                .dataPrimaryKey(param.getDataPrimaryKey())
                .props(props)
                .build());
        return testResult(action, "已更新存储分组[" + storageGroup + "]中数据主键 " + param.getDataPrimaryKey() + " 的 " + props.size() + " 个属性值");
    }

    /**
     * 行为定义之后分组里新增的必填属性，行为测试时会被写成 null；
     * 一旦命中非空约束，这个行为往后也跑不通，直接置为失效并提示重新编辑发布。
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
        action.setStatus(Status.DISABLE.getValue());
        updateById(action);
        throw new BusinessException("存储分组[" + action.getStorageGroup() + "]的必填属性未绑定取值，行为已置为失效，请重新编辑发布："
                + blocking, HttpStatus.BAD_REQUEST);
    }

    /** 按参数绑定的来源取属性值：对象属性读实例当前值，固定值解析字面量，外部输入取行为测试入参 */
    private Map<String, Object> resolvePropertyValues(List<ActionParamMappingVO> mappings, String ontologyIdentifier, ActionTestParam param) {
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
                    entityDetail = entityService.getEntityDetail(ontologyIdentifier, param.getEntityPrimaryKey()).stream()
                            .collect(Collectors.toMap(EntityPropertyDetailVO::getPropertyUniqIdentifier, EntityPropertyDetailVO::getPropertyValues));
                }
                var currentValues = entityDetail.get(mapping.getPropertyUniqueIdentifier());
                value = CollectionUtils.isEmpty(currentValues) ? null : currentValues.get(0);
            } else if (sourceType == ActionParamSourceEnum.FIXED) {
                value = parseFixedValue(mapping.getSourceConfig());
            } else if (sourceType == ActionParamSourceEnum.EXTERNAL) {
                value = param.getParams() == null ? null : param.getParams().get(mapping.getParameterName());
            } else {
                throw new BusinessException("参数[" + mapping.getParameterName() + "]的输入来源暂不支持行为测试："
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
        var linkMapping = detail.getLinkMapping();
        PreconditionUtils.checkArgument(linkMapping != null, "行为没有绑定关系，无法进行行为测试", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getLinkEntityPrimaryKey() != null, "实例关系行为测试必须提供对端实体主键", HttpStatus.BAD_REQUEST);
        var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkMapping.getOntologyLinkUniqIdentifier()));
        PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + linkMapping.getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);

        // 行为挂在对象的一侧，行为测试给的实体主键就落在关系的那一端
        var selfIsFrom = action.getOntologyUniqueIdentifier().equals(linkGroup.getOntologyUniqueIdentifierFrom());
        var primaryKeyFrom = selfIsFrom ? param.getEntityPrimaryKey() : param.getLinkEntityPrimaryKey();
        var primaryKeyTo = selfIsFrom ? param.getLinkEntityPrimaryKey() : param.getEntityPrimaryKey();
        var linkId = linkMapping.getOntologyLinkUniqIdentifier();
        var operation = action.getActionOperation();

        if (operation == ActionOperationEnum.DELETE) {
            entityService.deleteEntityRelation(linkId, primaryKeyFrom, primaryKeyTo);
            return testResult(action, "已将实例关系[" + linkId + "]置为删除状态");
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
        var msg = operation == ActionOperationEnum.UPDATE
                ? "已将实例关系[" + linkId + "]状态改为 " + status.getName()
                : "已写入实例关系[" + linkId + "]（状态 " + status.getName() + "）";
        return testResult(action, msg);
    }

    /** 算子类行为测试：走既有执行链路，算子输出按输出方式真实回写 */
    @SneakyThrows
    private ActionTestResultVO testFunctionAction(OntologyAction action, OntologyActionDetailVO detail, ActionTestParam param) {
        var executeParamBuilder = EntityActionExecuteParam.builder()
                .actionApi(action.getApi())
                .ontologyUniqueIdentifier(action.getOntologyUniqueIdentifier())
                .entityPrimaryKey(param.getEntityPrimaryKey());
        // 指定了对端实例时只跑这一对，否则会遍历关系下的全部实体
        if (detail.getLinkMapping() != null && param.getLinkEntityPrimaryKey() != null) {
            var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getUniqueIdentifier, detail.getLinkMapping().getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + detail.getLinkMapping().getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
            executeParamBuilder.linkedOntologyUniqueIdentifier(linkGroup.getOntologyUniqueIdentifierTo())
                    .linkedEntityPrimaryKey(param.getLinkEntityPrimaryKey());
        }
        var executeParam = executeParamBuilder.build();

        var contextInfoDTO = entityService.initActionContextInfoDTO(executeParam);
        contextInfoDTO.setExternalParams(param.getParams());
        var result = entityService.executeEntityAction(executeParam, contextInfoDTO, entityService.getLinkedEntities(contextInfoDTO));
        return testResult(action, "已按行为定义执行算子并完成落库").setResult(result);
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
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, param.getActionApi()));
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
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, param.getActionApi()));
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
