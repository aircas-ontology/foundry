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
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.DataLakeObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.*;
import com.aircas.ptr.foundry.ontology.service.action.BasicFunctionActionExecutor;
import com.aircas.ptr.foundry.ontology.service.behavior.BtTreeManager;
import com.aircas.ptr.foundry.ontology.service.behavior.CompiledBehaviorTree;
import com.aircas.ptr.foundry.ontology.service.behavior.TreeActionInvoker;
import com.aircas.ptr.foundry.ontology.service.behavior.TreeBranchRunner;
import com.aircas.ptr.foundry.ontology.service.behavior.TreeCooldownTracker;
import com.aircas.ptr.foundry.ontology.service.behavior.TreeCooldownRefunder;
import com.aircas.ptr.foundry.ontology.service.behavior.TreeExecutionRuntime;
import com.aircas.ptr.foundry.ontology.utils.FunctionOutputValueReader;
import com.aircas.ptr.foundry.ontology.utils.ScriptParamSchemaUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Lists;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class OntologyActionServiceImpl extends ServiceImpl<OntologyActionMapper, OntologyAction> implements OntologyActionService {

    /** 属性值行为的主存储分组：main 行的增删改就是实体本身，由实体接口负责，不允许行为直接操作 */
    private static final String MAIN_STORAGE_GROUP = "main";

    /** 行为测试 entityPrimaryKeys 里表示关系两端的保留键，与按对象 id 传的属性入参实例区分 */
    private static final String TEST_LINK_FROM_KEY = "from";
    private static final String TEST_LINK_TO_KEY = "to";

    /** 挂载对象列存逗号分隔的对象id，两端补上分隔符后整段匹配，避免前缀/子串误命中 */
    private static final String OBJECT_IDENTIFIER_CONTAINS =
            "strpos(',' || ontology_unique_identifier || ',', concat(',', {0}, ',')) > 0";

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

    /** 批量执行一页读取的实例数：实例主键分页拉取，避免一次把整表主键读进内存 */
    private static final int TREE_BATCH_PAGE_SIZE = 500;

    /** 批量行为测试一次最多展开的组合数 */
    private static final int MAX_TEST_COMBOS = 50;

    /** 批量行为测试可以传数组的字段，与 TestAxis#field 对应 */
    private static final String TEST_AXIS_FIELD_ENTITY_PRIMARY_KEYS = "entityPrimaryKeys";
    private static final String TEST_AXIS_FIELD_INPUT_PARAMS = "inputParams";


    @Resource
    private ActionHandleLogMapper actionHandleLogMapper;

    @Resource
    private ManualExecuteLogService manualExecuteLogService;

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
    private OntologyPropertyMapper propertyMapper;

    @Resource
    private ActionCategoryMapper actionCategoryMapper;

    @Resource
    private DataLakeObjectMapper entityMapper;

    @Resource
    private TableMetadataMapper tableMetadataMapper;

    @Resource
    private OntologyActionMappingInService ontologyActionMappingInService;

    @Lazy
    @Resource
    private OntologyPropertyService ontologyPropertyService;

    @Resource
    private ActionHandleRuleService actionHandleRuleService;

    @Lazy
    @Resource
    private ActionRuleTriggerService actionRuleTriggerService;

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

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    @Resource
    private BasicFunctionActionExecutor basicFunctionActionExecutor;

    /** 冷却表缺失只提示一次，避免每个冷却节点、每次保存都刷日志 */
    private final AtomicBoolean behaviorTreeCooldownWarned = new AtomicBoolean();


    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 仅用于解析固定值字面量：默认 mapper 读到第一个 JSON token 就返回，
     * TLE 两行根数这类「数字开头+空格+文字」会被静默截成前导数字，故要求整串都是 JSON。
     */
    private final com.fasterxml.jackson.databind.ObjectMapper fixedValueMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override
    public void removeByOntologyIdentifier(String ontologyIdentifier) {
        var matched = list(new LambdaQueryWrapper<OntologyAction>()
                .apply(OBJECT_IDENTIFIER_CONTAINS, ontologyIdentifier));
        if (CollectionUtils.isEmpty(matched)) {
            return;
        }
        // 合并后一个行为可以挂多个对象：还挂在别的对象上的只摘掉本对象，行为本身留着；
        // 关系行为绑的边、基础函数的查询对象都写死在本对象上，随对象一起删
        var behaviors = matched.stream()
                .filter(action -> action.getActionKind() != ActionKindEnum.TREE)
                .collect(Collectors.toList());
        var removed = behaviors.stream()
                .filter(action -> !keepsAfterDetach(action, ontologyIdentifier))
                .collect(Collectors.toList());
        var detached = behaviors.stream()
                .filter(action -> keepsAfterDetach(action, ontologyIdentifier))
                .collect(Collectors.toList());

        // 删对象会连带删行为，与单个删除保持同一口径：被行为树引用的行为先拦下，
        // 否则树里只剩一个永远解析不到的 api，要等到执行才暴露
        var referenced = new ArrayList<String>();
        var spaces = new LinkedHashSet<Integer>();
        behaviors.forEach(action -> {
            if (action.getOntologySpaceId() != null) {
                spaces.add(action.getOntologySpaceId());
            }
        });
        for (var spaceId : spaces) {
            var spaceApis = removed.stream()
                    .filter(action -> Objects.equals(action.getOntologySpaceId(), spaceId))
                    .map(OntologyAction::getApi)
                    .collect(Collectors.toSet());
            if (!spaceApis.isEmpty()) {
                referenced.addAll(findTreesReferencingActions(spaceId, spaceApis));
            }
        }
        PreconditionUtils.checkArgument(referenced.isEmpty(),
                "对象下的行为被行为树引用，不能删除对象：" + String.join("；", referenced), HttpStatus.BAD_REQUEST);

        var actionIds = removed.stream().map(OntologyAction::getId).collect(Collectors.toList());
        if (!actionIds.isEmpty()) {
            actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().in(OntologyActionLink::getOntologyActionId, actionIds));
            remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, actionIds));
            ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, actionIds));
        }
        detached.forEach(action -> {
            action.setOntologyUniqueIdentifier(withoutObject(action, ontologyIdentifier));
            updateById(action);
        });
        // 树的挂载列表是各节点绑定行为的并集，行为摘掉本对象后树也要重算
        matched.stream()
                .filter(action -> action.getActionKind() == ActionKindEnum.TREE)
                .forEach(tree -> {
                    tree.setOntologyUniqueIdentifier(new ArrayList<>(treeObjectIdentifiers(
                            collectTreeActionApis(readTreeDefinition(tree.getDefinition()).getRoots(), new HashSet<>()),
                            tree.getOntologySpaceId())));
                    updateById(tree);
                });
        // 调度行靠 action_id 关联行为，该列已在 alter_table_20260909.sql 中删除，此处无法再按行为清理调度；
        // 遗留的调度任务会在下次触发时因取不到行为而失败，待确认该列是否为误删后补回
        spaces.forEach(btTreeManager::invalidateSpace);
    }

    /** 摘掉这个对象后行为还能不能跑：关系行为绑的边、基础函数的查询对象都随对象一起没了 */
    private boolean keepsAfterDetach(OntologyAction action, String ontologyIdentifier) {
        if (withoutObject(action, ontologyIdentifier).isEmpty()) {
            return false;
        }
        return action.getSubjectType() != ActionSubjectTypeEnum.LINK
                && action.getSubjectType() != ActionSubjectTypeEnum.BASIC_FUNCTION;
    }

    private List<String> withoutObject(OntologyAction action, String ontologyIdentifier) {
        if (CollectionUtils.isEmpty(action.getOntologyUniqueIdentifier())) {
            return new ArrayList<>();
        }
        return action.getOntologyUniqueIdentifier().stream()
                .filter(identifier -> !StringUtils.equals(identifier, ontologyIdentifier))
                .distinct()
                .collect(Collectors.toList());
    }

    /** 树里各行为节点绑定的行为 api；读不出来的节点跳过，与删除判定口径一致 */
    private Set<String> collectTreeActionApis(List<BehaviorTreeNode> nodes, Set<String> apis) {
        if (CollectionUtils.isEmpty(nodes)) {
            return apis;
        }
        for (var node : nodes) {
            if (node.getKind() == BehaviorNodeKindEnum.ACTION && node.getConfig() != null) {
                try {
                    var config = jsonMapper.convertValue(node.getConfig(), BehaviorActionConfig.class);
                    if (config != null && StringUtils.isNotBlank(config.getActionApi())) {
                        apis.add(config.getActionApi());
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("行为树节点配置解析失败, nodeId={}", node.getId());
                }
            }
            var children = node.getChildren();
            if (CollectionUtils.isNotEmpty(children)) {
                collectTreeActionApis(children, apis);
            }
        }
        return apis;
    }

    /**
     * 按空间批量判定哪些树引用了给定行为：树的定义只解析一次，
     * 同一空间删一批行为时不用逐条去扫全量树。
     */
    private List<String> findTreesReferencingActions(Integer ontologySpaceId, Set<String> actionApis) {
        var trees = list(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId)
                .eq(OntologyAction::getActionKind, ActionKindEnum.TREE)
                .select(OntologyAction::getApi, OntologyAction::getDefinition));
        var references = new ArrayList<String>();
        for (var tree : trees) {
            // treeReferencesAction 对读不出来的节点配置是跳过的，草稿树有问题也不影响删除判定
            var roots = readTreeDefinition(tree.getDefinition()).getRoots();
            var hit = actionApis.stream()
                    .filter(api -> treeReferencesAction(roots, api))
                    .sorted()
                    .collect(Collectors.toList());
            if (!hit.isEmpty()) {
                references.add("行为 " + String.join("、", hit) + " 被行为树 " + tree.getApi() + " 引用");
            }
        }
        return references;
    }


    /**
     * 行为归属的对象空间：按创建时选的对象反查；基础函数没有顶层对象，按配置里的第一个对象反查；
     * 两者都没有（应用行为、行为树）退回行为分类所在空间。
     */
    private Integer resolveActionSpaceId(ActionCreateOrUpdateParam param) {
        if (StringUtils.isNotEmpty(param.getOntologyIdentifier())) {
            var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
            PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
            return meta.getOntologySpaceId();
        }
        if (param.getBasicFunction() != null) {
            var identifiers = basicFunctionActionExecutor.configOntologyIdentifiers(param.getBasicFunction());
            if (!identifiers.isEmpty()) {
                var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getUniqueIdentifier, identifiers.get(0)));
                PreconditionUtils.checkNotNull(meta, "无效的对象：" + identifiers.get(0), HttpStatus.BAD_REQUEST);
                return meta.getOntologySpaceId();
            }
        }
        var category = actionCategoryMapper.selectById(param.getCategoryId());
        PreconditionUtils.checkNotNull(category, "无效的行为分类：" + param.getCategoryId(), HttpStatus.BAD_REQUEST);
        return category.getOntologySpaceId();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void createAction(ActionCreateOrUpdateParam param) {
        var spaceId = resolveActionSpaceId(param);
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
        var spaceId = resolveActionSpaceId(param);
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        if (action == null) {
            // 基础函数行为按配置首个对象反推空间：编辑时换了对象/对象挪了空间，推导值会偏离落库空间。
            // api 全局只找到一条时以落库行为准；多条时无法定位，要求显式传空间。
            var candidates = list(new LambdaQueryWrapper<OntologyAction>()
                    .eq(OntologyAction::getApi, param.getActionApi()));
            PreconditionUtils.checkArgument(candidates.size() <= 1,
                    "推导空间内没有行为 " + param.getActionApi() + "，且该 api 存在于多个空间，请传 ontologySpaceId 定位",
                    HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(!candidates.isEmpty(), "行为不存在", HttpStatus.BAD_REQUEST);
            action = candidates.get(0);
            spaceId = action.getOntologySpaceId();
        }
        PreconditionUtils.checkArgument(!Objects.equals(action.getStatus(), ActionStatusEnum.PUBLISHED.getValue()),
                "该行为已发布，不能编辑，请先撤回发布（行为树请先下线）", HttpStatus.BAD_REQUEST);
        checkNoRunningScheduling(action.getOntologySpaceId(), action.getApi(),
                "该行为有启动中的调度正在执行，请先暂停调度再编辑");
        // 编辑不改状态：草稿还是草稿、已发布还是已发布
        var originalStatus = action.getStatus();
        var definition = checkActionDefinition(action, param, spaceId);
        action.setStatus(originalStatus);
        // 原地更新：行为 id 被关联关系、参数绑定和调度任务引用，删除重建会让它们失去引用
        updateById(action);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getOntologyActionId, action.getId()));
        saveActionChildren(action.getId(), definition);
        if (action.getActionKind() == ActionKindEnum.TREE && cooldownTableReady()) {
            // 定义改了，已经不存在的节点不该再留着冷却记录：节点标识被其他节点复用时会把旧冷却带过来
            behaviorTreeCooldownMapper.deleteByTreeExceptNodes(spaceId, action.getApi(),
                    collectTreeNodeIds(readTreeDefinition(action.getDefinition()).getRoots()));
        }
        btTreeManager.invalidate(action.getOntologySpaceId(), action.getApi());
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void publishAction(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        var tree = action.getActionKind() == ActionKindEnum.TREE;
        if (tree) {
            publishTreeDefinition(action);
        } else {
            checkBoundFunctionPublished(action);
        }
        action.setStatus(ActionStatusEnum.PUBLISHED.getValue());
        updateById(action);
        if (tree) {
            btTreeManager.invalidate(action.getOntologySpaceId(), action.getApi());
        }
    }

    /**
     * 树没有版本，状态就是唯一开关，发布前用库里的定义再校验一遍：
     * 引用的行为可能已被删除或下架，悬空要在这里拦住，不能等执行时才发现。
     */
    private void publishTreeDefinition(OntologyAction action) {
        var param = ActionCreateOrUpdateParam.builder()
                .actionApi(action.getApi())
                .displayName(action.getDisplayName())
                .categoryId(action.getActionCategoryId())
                .ontologySpaceId(action.getOntologySpaceId())
                .actionKind(ActionKindEnum.TREE)
                .treeDefinition(readTreeDefinition(action.getDefinition()))
                .build();
        checkTreeDefinition(new OntologyAction(), param);
        // 校验会按绑定行为把缺的外部参数补成黑板键，发布时把归一后的定义写回库里
        action.setDefinition(writeTreeDefinition(param.getTreeDefinition()));
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void unpublishAction(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(action.getStatus(), ActionStatusEnum.PUBLISHED.getValue()),
                "该行为未在发布状态，无需撤回", HttpStatus.BAD_REQUEST);
        var tree = action.getActionKind() == ActionKindEnum.TREE;
        // 行为树里的行为节点按 api 引用，撤回发布后已发布的树会在执行时失败，先让调用方处理引用它的树；
        // 树节点不能绑树，所以树自身不会被任何定义引用，不用扫
        if (!tree) {
            var referencingTrees = findTreesReferencingAction(ontologySpaceId, actionApi);
            PreconditionUtils.checkArgument(referencingTrees.isEmpty(),
                    "该行为被行为树引用，不能撤回发布：" + String.join("、", referencingTrees), HttpStatus.BAD_REQUEST);
        }
        action.setStatus(ActionStatusEnum.DRAFT.getValue());
        updateById(action);
        // 下线要同时失效编译缓存，否则缓存里的树仍会被执行
        if (tree) {
            btTreeManager.invalidate(action.getOntologySpaceId(), action.getApi());
        }
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
        // 只有应用行为可以脱离对象挂在空间上；基础行为都作用在某个对象的实例上，缺对象标识会建出执行不了的记录。
        // 基础函数例外：它作用的对象写在 basicFunction 配置里（可以一次选多个），顶层对象id 不使用
        if (kind == ActionKindEnum.BASE
                && param.getSubjectType() != ActionSubjectTypeEnum.BASIC_FUNCTION) {
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
        // 创建行为时选的对象也是挂载对象之一：绑定里一个对象都认不出时（全固定值入参）行为仍归属它
        if (StringUtils.isNotBlank(param.getOntologyIdentifier())) {
            definition.objectIdentifiers.add(param.getOntologyIdentifier());
        }
        if (kind == ActionKindEnum.APPLICATION) {
            PreconditionUtils.checkArgument(param.getSubjectType() == null && param.getOperation() == null
                    && StringUtils.isEmpty(param.getStorageGroup()), "应用行为不需要行为类型、基础操作和存储分组", HttpStatus.BAD_REQUEST);
            // outputMode 下沉到 mappingIns 每行：每个出参独立选 RESULT_ONLY / WRITE_PROPERTY
            // 关系可选：绑了才在测试/执行时按两端落关系边，没绑只回写属性
            checkFunctionAction(param, definition, APPLICATION_FUNCTION_TYPES, spaceId, false);
            checkWritePropertyOutput(definition);
            // 子表分组属性不要求绑全：没绑的列新增行时按空值补齐（EntityServiceImpl.fillUnwrittenColumns）
            // checkSubtableFullWrite(definition);
            // 数组出参写子表：路径按参数形状补 [*]，补完再校验通配分组，校验的就是最终入库的路径
            fillListWildcardExpressions(definition);
            checkWildcardOutputGroups(definition);
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
            } else if (subjectType == ActionSubjectTypeEnum.BASIC_FUNCTION) {
                checkBasicFunctionAction(param, definition, spaceId);
            } else if (subjectType == ActionSubjectTypeEnum.BASIC_OPERATOR) {
                // 基础算子已下线：不删代码也不动基础查询算子，存量行为照常回显和执行，只是不再接受新的创建和编辑
                throw new BusinessException("基础算子行为已停止创建和编辑，请改用基础函数行为", HttpStatus.BAD_REQUEST);
            } else {
                throw new BusinessException("不支持的基础行为类型：" + subjectType.name(), HttpStatus.BAD_REQUEST);
            }
            // 属性值、实例关系的输出即落库本身，没有回写方式；基础函数只有配了结果回写才落库，否则仍只返回结果
            action.setOutputMode(subjectType == ActionSubjectTypeEnum.BASIC_FUNCTION
                    ? (basicFunctionActionExecutor.hasWriteBack(param.getBasicFunction())
                    ? ActionOutputModeEnum.WRITE_PROPERTY : ActionOutputModeEnum.RESULT_ONLY)
                    : null);
        }

        action.setApi(param.getActionApi());
        action.setDisplayName(param.getDisplayName());
        action.setDescription(param.getDescription());
        action.setIcon(param.getIcon());
        action.setFunctionApi(param.getFunctionApi());
        action.setOntologySpaceId(spaceId);
        action.setActionCategoryId(param.getCategoryId());
        action.setActionKind(kind);
        action.setSubjectType(param.getSubjectType());
        action.setActionOperation(param.getOperation());
        action.setStorageGroup(param.getStorageGroup());
        // 只有基础函数那一类会填这份配置，其他类别拿到的是 null，编辑改成别的类别时旧配置跟着清掉
        action.setBasicFunctionConfig(definition.basicFunctionConfig);
        action.setOntologyUniqueIdentifier(new ArrayList<>(definition.objectIdentifiers));
        action.setPropertyUniqueIdentifier(new ArrayList<>(definition.propertyIdentifiers));
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

        // 黑板键只声明条件用的全局变量（行为节点配了绑定时也按它取/回传值）；行为外部入参不自动补成黑板键
        var blackboardTypes = checkTreeBlackboard(tree, spaceId);
        checkTreeVariables(tree, blackboardTypes);
        var roots = tree.getRoots();
        PreconditionUtils.checkArgument(roots.size() == 1, "行为树必须且只能有一个根节点，当前 " + roots.size() + " 个", HttpStatus.BAD_REQUEST);
        var boundApis = new HashSet<String>();
        checkTreeNode(roots.get(0), spaceId, blackboardTypes, new HashSet<>(), boundApis);
        checkTreeDataFlows(tree, spaceId);

        action.setApi(param.getActionApi());
        action.setDisplayName(param.getDisplayName());
        action.setDescription(param.getDescription());
        action.setIcon(param.getIcon());
        action.setActionCategoryId(param.getCategoryId());
        action.setActionKind(ActionKindEnum.TREE);
        action.setOntologySpaceId(spaceId);
        action.setFunctionApi(null);
        action.setSubjectType(null);
        action.setActionOperation(null);
        action.setStorageGroup(null);
        action.setOutputMode(null);
        action.setBasicFunctionConfig(null);
        action.setDefinition(writeTreeDefinition(tree));
        // 树自己不指向对象，它的挂载对象就是各节点绑定的行为的挂载对象
        action.setOntologyUniqueIdentifier(new ArrayList<>(treeObjectIdentifiers(boundApis, spaceId)));
        // 黑板按对象属性取值的键直接引用属性：节点绑定的行为自己会记，这里只补树定义里的引用
        action.setPropertyUniqueIdentifier(new ArrayList<>(treePropertyIdentifiers(tree)));
        // 新建的树是未发布状态，编辑不改状态：发布/下线是单独动作，不像行为那样「编辑即重新发布」
        if (action.getStatus() == null) {
            action.setStatus(ActionStatusEnum.DRAFT.getValue());
        }
    }

    /**
     * 黑板声明条件用的全局变量：键名、类型、取值来源（固定值/外部输入/对象属性）。
     * 返回值是 键名 → 声明类型，后面变量和条件节点都按它校验。
     * 历史定义没有 source 时按原字段推导并写回，保证详情和执行期看到同一份口径。
     * <p>黑板不是必填：一棵只做行为调用、不做条件判断的树可以一个键都不声明；引用未声明键的节点仍会被单独拒绝。
     * 三种来源各自带值的地方不同，只有固定值来源用本项的 value，其余来源填了直接拒绝，免得配了不生效。</p>
     */
    private Map<String, FunctionParamTypeEnum> checkTreeBlackboard(BehaviorTreeDefinition tree, Integer spaceId) {
        Map<String, OntologyProperty> boundProperties = boundTreeProperties(tree);
        Map<String, FunctionParamTypeEnum> types = new LinkedHashMap<>();
        for (var item : tree.getBlackboard()) {
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(item.getKey()), "黑板键名不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(!types.containsKey(item.getKey()), "黑板键名重复：" + item.getKey(), HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkNotNull(item.getValueType(), "黑板键 " + item.getKey() + " 必须选择值类型", HttpStatus.BAD_REQUEST);
            var source = item.resolvedSource();
            item.setSource(source);
            switch (source) {
                case FIXED -> {
                    PreconditionUtils.checkArgument(StringUtils.isBlank(item.getBindPropertyUniqueIdentifier()),
                            "黑板键 " + item.getKey() + " 的值来源是固定值，不能再绑定对象属性", HttpStatus.BAD_REQUEST);
                    PreconditionUtils.checkArgument(StringUtils.isBlank(item.getBindObjectIdentifier()),
                            "黑板键 " + item.getKey() + " 的值来源是固定值，只有对象属性来源才填所属对象", HttpStatus.BAD_REQUEST);
                    PreconditionUtils.checkNotNull(item.getValue(),
                            "黑板键 " + item.getKey() + " 的值来源是固定值，必须填写固定值", HttpStatus.BAD_REQUEST);
                    PreconditionUtils.checkArgument(matchesTreeValueType(item.getValue(), item.getValueType()),
                            "黑板键 " + item.getKey() + " 的固定值与声明类型 " + item.getValueType().name() + " 不匹配",
                            HttpStatus.BAD_REQUEST);
                }
                case EXTERNAL -> {
                    PreconditionUtils.checkArgument(
                            StringUtils.isBlank(item.getBindPropertyUniqueIdentifier()),
                            "黑板键 " + item.getKey() + " 的值来源是外部输入，不能再绑定对象属性", HttpStatus.BAD_REQUEST);
                    PreconditionUtils.checkArgument(StringUtils.isBlank(item.getBindObjectIdentifier()),
                            "黑板键 " + item.getKey() + " 的值来源是外部输入，只有对象属性来源才填所属对象", HttpStatus.BAD_REQUEST);
                    checkTreeNoFixedValue(item);
                }
                case PROPERTY -> {
                    checkTreeNoFixedValue(item);
                    checkTreeBlackboardProperty(item, spaceId,
                            boundProperties.get(item.getBindPropertyUniqueIdentifier()));
                }
            }
            types.put(item.getKey(), item.getValueType());
        }
        return types;
    }

    /** 固定值只属于固定值来源：别的来源填了要么被执行入参顶掉、要么被库里回读覆盖，留着只会误导 */
    private void checkTreeNoFixedValue(BehaviorBlackboardItem item) {
        PreconditionUtils.checkArgument(item.getValue() == null,
                "黑板键 " + item.getKey() + " 的值来源是" + item.resolvedSource().getName() + "，取值不由固定值给，不要填固定值",
                HttpStatus.BAD_REQUEST);
    }

    /**
     * 对象属性来源：必须选属性，且给出属性所属对象、两者对得上，属性在本空间、类型与黑板键一致。
     * 否则执行时要么读不到值，要么拿字符串和数值比较。
     * <p>取值仍按属性实际所属对象定位实例，填的对象标识只用于前端回显和选实例，写错直接拒绝，不留一个不生效的值。</p>
     */
    private void checkTreeBlackboardProperty(BehaviorBlackboardItem item, Integer spaceId, OntologyProperty property) {
        var bind = item.getBindPropertyUniqueIdentifier();
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(bind),
                "黑板键 " + item.getKey() + " 的值来源是对象属性，必须选择属性", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(property != null,
                "黑板键 " + item.getKey() + " 绑定的对象属性不存在：" + bind, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(item.getBindObjectIdentifier()),
                "黑板键 " + item.getKey() + " 绑定属性 " + bind + " 时还要给出它所属的对象", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.equals(StringUtils.trimToEmpty(item.getBindObjectIdentifier()),
                        property.getOntologyUniqueIdentifier()),
                "黑板键 " + item.getKey() + " 填的所属对象和属性 " + bind + " 实际所属的对象不是同一个，请按属性重新选对象",
                HttpStatus.BAD_REQUEST);
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, property.getOntologyUniqueIdentifier()));
        PreconditionUtils.checkArgument(meta != null,
                "黑板键 " + item.getKey() + " 绑定的属性所属对象不存在：" + property.getOntologyUniqueIdentifier(),
                HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(spaceId.equals(meta.getOntologySpaceId()),
                "黑板键 " + item.getKey() + " 绑定的属性不属于本空间的对象：" + meta.getUniqueIdentifier(),
                HttpStatus.BAD_REQUEST);
        var propertyType = toBlackboardValueType(property.getPropertyType());
        PreconditionUtils.checkArgument(propertyType == item.getValueType(),
                "黑板键 " + item.getKey() + " 的类型与绑定属性 " + bind + " 的类型不一致："
                        + item.getValueType() + " != " + propertyType, HttpStatus.BAD_REQUEST);
    }

    /** 一次查出黑板里对象属性来源引用的属性，供保存期校验和实例要求复用 */
    private Map<String, OntologyProperty> boundTreeProperties(BehaviorTreeDefinition tree) {
        var propertyIds = tree.getBlackboard().stream()
                .filter(item -> item.resolvedSource() == BehaviorBlackboardSourceEnum.PROPERTY)
                .map(BehaviorBlackboardItem::getBindPropertyUniqueIdentifier)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        return ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .in(OntologyProperty::getUniqueIdentifier, propertyIds)
                        .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()))
                .stream()
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, property -> property,
                        (left, right) -> left));
    }

    /**
     * 来源是对象属性的黑板键按所属对象归组：对象标识 -> 用到它的黑板键。
     * 执行前要按对象选实例，这些键才读得到库里的值。
     */
    private Map<String, List<String>> treeBlackboardPropertyObjects(BehaviorTreeDefinition tree) {
        var properties = boundTreeProperties(tree);
        Map<String, List<String>> keysByObject = new LinkedHashMap<>();
        for (var item : tree.getBlackboard()) {
            if (item.resolvedSource() != BehaviorBlackboardSourceEnum.PROPERTY
                    || StringUtils.isBlank(item.getKey())) {
                continue;
            }
            var property = properties.get(item.getBindPropertyUniqueIdentifier());
            if (property == null || StringUtils.isBlank(property.getOntologyUniqueIdentifier())) {
                continue;
            }
            keysByObject.computeIfAbsent(property.getOntologyUniqueIdentifier(), ignored -> new ArrayList<>())
                    .add(item.getKey());
        }
        return keysByObject;
    }

    /**
     * 对象属性来源的黑板键里属性不在主分组的，按 对象 → 存储分组 → 黑板键 归类。
     * 主分组一行就是实例本身，不用再选行；其他分组一个实例下有多行，执行前要再选该分组的一行，条件求值前从这一行回读。
     */
    private Map<String, Map<String, List<String>>> treeBlackboardPropertyRowGroups(BehaviorTreeDefinition tree) {
        var properties = boundTreeProperties(tree);
        Map<String, Map<String, List<String>>> groups = new LinkedHashMap<>();
        for (var item : tree.getBlackboard()) {
            if (item.resolvedSource() != BehaviorBlackboardSourceEnum.PROPERTY
                    || StringUtils.isBlank(item.getKey())) {
                continue;
            }
            var property = properties.get(item.getBindPropertyUniqueIdentifier());
            if (property == null || StringUtils.isBlank(property.getOntologyUniqueIdentifier())) {
                continue;
            }
            var group = StringUtils.isBlank(property.getStorageGroup())
                    ? MAIN_STORAGE_GROUP : property.getStorageGroup();
            if (MAIN_STORAGE_GROUP.equals(group)) {
                continue;
            }
            groups.computeIfAbsent(property.getOntologyUniqueIdentifier(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(group, ignored -> new ArrayList<>())
                    .add(item.getKey());
        }
        return groups;
    }

    /**
     * 变量给黑板键提供兜底默认值，只校验给了的那些：键名必须已声明、类型必须一致，否则执行期读到 null 或读错类型。
     * <p>只有外部输入来源的黑板键用变量默认值兜底：固定值取黑板项自己的 value，对象属性每次条件求值按实例重读，
     * 给它们配默认值都读不到，保存期直接拒绝。也不要求覆盖每个键——外部输入可以没有兜底值，不传时该键就是空。</p>
     */
    private void checkTreeVariables(BehaviorTreeDefinition tree, Map<String, FunctionParamTypeEnum> blackboardTypes) {
        Map<String, BehaviorBlackboardSourceEnum> sources = new LinkedHashMap<>();
        tree.getBlackboard().forEach(item -> sources.putIfAbsent(item.getKey(), item.resolvedSource()));
        var names = new HashSet<String>();
        for (var item : tree.getVariables()) {
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(item.getName()), "变量名不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(names.add(item.getName()), "变量名重复：" + item.getName(), HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(blackboardTypes.containsKey(item.getName()),
                    "变量 " + item.getName() + " 没有同名黑板键，运行时读不到", HttpStatus.BAD_REQUEST);
            var source = sources.get(item.getName());
            PreconditionUtils.checkArgument(source == BehaviorBlackboardSourceEnum.EXTERNAL,
                    "变量 " + item.getName() + " 对应的黑板键来源是" + source.getName()
                            + "，只有外部输入来源的黑板键才用变量默认值", HttpStatus.BAD_REQUEST);
            var declared = blackboardTypes.get(item.getName());
            PreconditionUtils.checkArgument(item.getValueType() == declared,
                    "变量 " + item.getName() + " 的类型与黑板键不一致：" + item.getValueType() + " != " + declared, HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(item.getScope() == null || item.getScope() == BehaviorVariableScopeEnum.RUNTIME,
                    "变量作用域目前只支持 RUNTIME：" + item.getName(), HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(matchesTreeValueType(item.getDefaultValue(), declared),
                    "变量 " + item.getName() + " 的默认值与声明类型 " + declared.name() + " 不匹配", HttpStatus.BAD_REQUEST);
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
        var label = treeNodeLabel(node);
        PreconditionUtils.checkNotNull(kind, "节点 " + label + " 必须选择节点类型", HttpStatus.BAD_REQUEST);
        checkTreeExpression(node.getSkipIf(), "节点 " + label + " 的跳过条件");
        checkTreeExpression(node.getWhileCondition(), "节点 " + label + " 的持续执行条件");

        var children = node.getChildren();
        switch (kind.getCategory()) {
            case COMPOSITE -> PreconditionUtils.checkArgument(!children.isEmpty(),
                    kind.getName() + " " + label + " 至少要有子节点", HttpStatus.BAD_REQUEST);
            case DECORATOR -> PreconditionUtils.checkArgument(children.size() == 1,
                    kind.getName() + " " + label + " 只能有一个子节点，当前 " + children.size() + " 个", HttpStatus.BAD_REQUEST);
            default -> PreconditionUtils.checkArgument(children.isEmpty(),
                    kind.getName() + " " + label + " 是叶子节点，不能有子节点", HttpStatus.BAD_REQUEST);
        }
        checkTreeNodeConfig(node, spaceId, blackboardTypes, actionApis);
        children.forEach(child -> checkTreeNode(child, spaceId, blackboardTypes, visitedIds, actionApis));
    }

    /** 提示文案里给用户的节点称呼：优先界面上的节点名称，没配名称才退回标识 */
    private static String treeNodeLabel(BehaviorTreeNode node) {
        return StringUtils.defaultIfBlank(node.getDisplayName(), node.getId());
    }

    /**
     * 按节点类型校验专属配置：类型下拉、必填项、取值范围都在这里，config 里出现该类型不认识的字段直接拒绝。
     */
    private void checkTreeNodeConfig(BehaviorTreeNode node, Integer spaceId,
                                     Map<String, FunctionParamTypeEnum> blackboardTypes, Set<String> actionApis) {
        // 提示里报名称不报标识：用户在界面上认识的是节点名称
        var label = treeNodeLabel(node);
        switch (node.getKind()) {
            case SELECTOR, SEQUENCE -> {
                var config = readTreeConfig(node, BehaviorGroupConfig.class);
                PreconditionUtils.checkNotNull(config.getRunMode(), "节点 " + label + " 必须选择执行模式", HttpStatus.BAD_REQUEST);
            }
            case PARALLEL -> {
                var config = readTreeConfig(node, BehaviorParallelConfig.class);
                PreconditionUtils.checkNotNull(config.getPolicy(), "节点 " + label + " 必须选择成败判定策略", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getMaxConcurrency(), "节点 " + label + " 的最大并发数");
            }
            case REPEATER -> {
                var config = readTreeConfig(node, BehaviorRepeaterConfig.class);
                PreconditionUtils.checkNotNull(config.getLoopMode(), "节点 " + label + " 必须选择循环模式", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getTimes(), "节点 " + label + " 的循环次数");
                if (config.getLoopMode() == BehaviorLoopModeEnum.FIXED_TIMES) {
                    PreconditionUtils.checkNotNull(config.getTimes(), "节点 " + label + " 选择固定次数时必须填写次数", HttpStatus.BAD_REQUEST);
                } else {
                    // 按条件与无限循环都靠持续执行条件结束，不填就等于没有终止条件
                    PreconditionUtils.checkArgument(StringUtils.isNotBlank(node.getWhileCondition()),
                            "节点 " + label + " 选择" + config.getLoopMode().getName() + "时必须填写持续执行条件", HttpStatus.BAD_REQUEST);
                }
            }
            case UNTIL_SUCCESS, UNTIL_FAILURE -> {
                var config = readTreeConfig(node, BehaviorUntilConfig.class);
                checkTreeNonNegative(config.getMaxTimes(), "节点 " + label + " 的最大重试次数");
                PreconditionUtils.checkNotNull(config.getFinishState(), "节点 " + label + " 必须选择次数耗尽后的收尾状态", HttpStatus.BAD_REQUEST);
                checkTreeResultState(config.getFinishState(), "节点 " + label + " 的收尾状态");
            }
            case COOLDOWN -> {
                var config = readTreeConfig(node, BehaviorCooldownConfig.class);
                PreconditionUtils.checkNotNull(config.getIntervalSeconds(), "节点 " + label + " 必须填写冷却时长", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getIntervalSeconds(), "节点 " + label + " 的冷却时长");
                PreconditionUtils.checkNotNull(config.getHitResult(), "节点 " + label + " 必须选择冷却期内的结果状态", HttpStatus.BAD_REQUEST);
                checkTreeResultState(config.getHitResult(), "节点 " + label + " 的冷却期内结果状态");
                if (StringUtils.isNotBlank(config.getEntityPrimaryKeyKey())) {
                    PreconditionUtils.checkArgument(blackboardTypes.containsKey(config.getEntityPrimaryKeyKey()),
                            "冷却节点 " + label + " 引用了未声明的黑板键：" + config.getEntityPrimaryKeyKey(), HttpStatus.BAD_REQUEST);
                }
            }
            case TIME_LIMIT -> {
                var config = readTreeConfig(node, BehaviorTimeLimitConfig.class);
                PreconditionUtils.checkNotNull(config.getLimitSeconds(), "节点 " + label + " 必须填写限时时长", HttpStatus.BAD_REQUEST);
                checkTreePositive(config.getLimitSeconds(), "节点 " + label + " 的限时时长");
                PreconditionUtils.checkNotNull(config.getTimeoutState(), "节点 " + label + " 必须选择超时后的状态", HttpStatus.BAD_REQUEST);
                checkTreeResultState(config.getTimeoutState(), "节点 " + label + " 的超时状态");
            }
            case INVERTER, FORCE_SUCCESS -> PreconditionUtils.checkArgument(node.getConfig() == null,
                    "节点 " + label + " 的" + node.getKind().getName() + "不需要额外配置", HttpStatus.BAD_REQUEST);
            case BLACKBOARD_CONDITION -> {
                var config = readTreeConfig(node, BehaviorBlackboardConditionConfig.class);
                PreconditionUtils.checkArgument(StringUtils.isNotBlank(config.getKey()), "节点 " + label + " 必须选择黑板键", HttpStatus.BAD_REQUEST);
                var declared = blackboardTypes.get(config.getKey());
                PreconditionUtils.checkArgument(declared != null, "节点 " + label + " 的黑板键未声明：" + config.getKey(), HttpStatus.BAD_REQUEST);
                var op = config.getOp();
                PreconditionUtils.checkNotNull(op, "节点 " + label + " 必须选择比较方式", HttpStatus.BAD_REQUEST);
                checkTreeCompareOpType(label, op, declared, "黑板键类型");
                if (op == BehaviorCompareOpEnum.IS_EMPTY || op == BehaviorCompareOpEnum.IS_NOT_EMPTY) {
                    PreconditionUtils.checkArgument(config.getValue() == null,
                            "节点 " + label + " 的" + op.getName() + "不需要比较值", HttpStatus.BAD_REQUEST);
                } else {
                    PreconditionUtils.checkArgument(config.getValue() != null, "节点 " + label + " 必须填写比较值", HttpStatus.BAD_REQUEST);
                    PreconditionUtils.checkArgument(matchesTreeValueType(config.getValue(), declared),
                            "节点 " + label + " 的比较值与黑板键 " + config.getKey() + " 的类型 " + declared.name() + " 不匹配", HttpStatus.BAD_REQUEST);
                }
            }
            case CONDITION -> {
                var config = readTreeConfig(node, BehaviorConditionConfig.class);
                PreconditionUtils.checkArgument(StringUtils.isNotBlank(config.getExpression()), "节点 " + label + " 必须填写条件表达式", HttpStatus.BAD_REQUEST);
                checkTreeExpression(config.getExpression(), "节点 " + label + " 的条件表达式");
            }
            case ACTION -> {
                var config = readTreeConfig(node, BehaviorActionConfig.class);
                var boundApi = config.getActionApi();
                PreconditionUtils.checkArgument(StringUtils.isNotBlank(boundApi), "行为节点 " + label + " 必须绑定行为", HttpStatus.BAD_REQUEST);
                // 行为在单轮里同步执行并落库，无法安全中断已经开始的调用，超时没有可执行的语义；
                // 0 或空表示「不限制」，沿用原配置口径，只有真配了正数才拒绝
                PreconditionUtils.checkArgument(config.getTimeoutSeconds() == null || config.getTimeoutSeconds() <= 0,
                        "行为节点 " + label + " 不支持超时时长：单轮同步执行无法中断已经开始的行为，"
                                + "不需要超时就传 0 或不传，需要限制后续步骤请改用限时节点",
                        HttpStatus.BAD_REQUEST);
                checkTreeNonNegative(config.getRetryTimes(), "行为节点 " + label + " 的重试次数");
                if (actionApis.add(boundApi)) {
                    checkTreeBoundAction(label, boundApi, spaceId);
                }
                checkActionSuccessCondition(label, config);
            }
        }
    }

    /** 行为节点的执行成功条件：可选，传了就要有一条 SpEL 表达式，这里只校验语法（字段存不存在要到执行期才知道） */
    private void checkActionSuccessCondition(String label, BehaviorActionConfig config) {
        var condition = config.getSuccessCondition();
        if (condition == null) {
            return;
        }
        var expression = StringUtils.trimToNull(condition.getExpression());
        PreconditionUtils.checkArgument(expression != null,
                "行为节点 " + label + " 的执行成功条件是空的：写一条 SpEL 表达式，不需要判定就整个不传",
                HttpStatus.BAD_REQUEST);
        checkTreeExpression(expression, "行为节点 " + label + " 的执行成功条件表达式");
    }

    /**
     * 行为执行时要从实例取数或往实例写回的对象类型。
     * <p>口径与 EntityServiceImpl 收集实例对象一致：入参来源是属性/对象/List，或出参写入对象属性；
     * 属性值与实例关系行为还要算上行为自己作用的对象。映射行没写对象标识的存量数据回退到行为唯一的挂载对象。</p>
     */
    private Set<String> treeActionInstanceObjects(OntologyAction bound) {
        Set<String> objects = new LinkedHashSet<>();
        var ownObjectRequired = bound.getActionKind() == ActionKindEnum.BASE
                && (bound.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE
                || bound.getSubjectType() == ActionSubjectTypeEnum.LINK);
        if (ownObjectRequired) {
            var ownObject = ownTreeObject(bound);
            if (StringUtils.isNotBlank(ownObject)) {
                objects.add(ownObject);
            }
        }
        var mappings = ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>()
                .eq(OntologyActionMappingIn::getOntologyActionId, bound.getId()));
        if (CollectionUtils.isEmpty(mappings)) {
            return objects;
        }
        Set<Long> funcParamIds = mappings.stream()
                .map(OntologyActionMappingIn::getFunctionParamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, FunctionParamPO> funcParams = funcParamIds.isEmpty() ? Map.of()
                : functionParamMapper.selectBatchIds(funcParamIds).stream()
                .collect(Collectors.toMap(FunctionParamPO::getId, param -> param, (existing, ignored) -> existing));
        for (var mapping : mappings) {
            var funcParam = mapping.getFunctionParamId() == null ? null : funcParams.get(mapping.getFunctionParamId());
            var isInput = funcParam == null || funcParam.getCategory() == FunctionParamCategoryEnum.INPUT;
            boolean needsInstance;
            if (isInput) {
                var source = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
                needsInstance = source == ActionParamSourceEnum.PROPERTY || source == ActionParamSourceEnum.OBJECT
                        || source == ActionParamSourceEnum.LIST;
            } else {
                needsInstance = mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY;
            }
            if (!needsInstance) {
                continue;
            }
            var objectId = StringUtils.isNotBlank(mapping.getOntologyUniqueIdentifier())
                    ? mapping.getOntologyUniqueIdentifier() : bound.primaryOntologyIdentifier();
            if (StringUtils.isNotBlank(objectId)) {
                objects.add(objectId);
            }
        }
        return objects;
    }

    /**
     * 需要调用方给实例主键的对象：关系对端不算，它由对端主键参数指定或整段遍历，不用在树里选实例。
     */
    private Set<String> treeActionKeyRequiredObjects(OntologyAction bound) {
        var objects = treeActionInstanceObjects(bound);
        var counterpart = treeActionCounterpartObject(bound);
        if (StringUtils.isNotBlank(counterpart)) {
            objects.remove(counterpart);
        }
        return objects;
    }

    /** 行为绑了关系时的对端对象类型，没绑关系返回 null */
    private String treeActionCounterpartObject(OntologyAction bound) {
        var linkEnds = treeActionLinkEnds(bound);
        return linkEnds == null ? null : linkEnds.counterpartOf(ownTreeObject(bound));
    }

    /** 行为绑定的关系的两端对象；没绑关系、关系定义取不到时返回 null */
    private TreeLinkEnds treeActionLinkEnds(OntologyAction bound) {
        var link = actionLinkMapper.selectOne(new LambdaQueryWrapper<OntologyActionLink>()
                .eq(OntologyActionLink::getOntologyActionId, bound.getId()));
        if (link == null || StringUtils.isBlank(link.getOntologyLinkUniqueIdentifier())) {
            return null;
        }
        var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, link.getOntologyLinkUniqueIdentifier()));
        if (linkGroup == null) {
            return null;
        }
        return new TreeLinkEnds(linkGroup.getOntologyUniqueIdentifierFrom(), linkGroup.getOntologyUniqueIdentifierTo());
    }

    /** 关系两端对象；自关系时 from、to 是同一个对象，实例仍要按两个不同主键传 */
    private record TreeLinkEnds(String fromObject, String toObject) {

        private boolean selfRelation() {
            return StringUtils.equals(fromObject, toObject);
        }

        /** 另一端：认不出本端时按 From 处理 */
        private String counterpartOf(String ownObject) {
            return StringUtils.equals(ownObject, toObject) ? fromObject : toObject;
        }
    }

    /**
     * 行为节点绑定的行为必须已经建好、在本空间内且已发布；树不能套树，否则执行时形不成单轮语义。
     * <p>行为发布后它绑的算子仍可能被下架，发布树时一并穿透校验，否则树发布成功、执行时才失败。</p>
     */
    private void checkTreeBoundAction(String nodeLabel, String boundApi, Integer spaceId) {
        var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, boundApi)
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(bound != null, "行为节点 " + nodeLabel + " 绑定的行为在本空间不存在：" + boundApi, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(bound.getActionKind() != ActionKindEnum.TREE,
                "行为节点只能绑定行为，不能嵌套行为树：" + boundApi, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(bound.getStatus(), ActionStatusEnum.PUBLISHED.getValue()),
                "行为节点 " + nodeLabel + " 绑定的行为未发布：" + boundApi, HttpStatus.BAD_REQUEST);
        checkBoundFunctionPublished(bound);
    }

    /**
     * 校验节点间的数据绑定：入参从哪来、出参回到哪个黑板键。
     * <p>能被引用的上游出参要求「必然执行且必然成功」：只有顺序节点能保证排在它前面的都跑过且都成功
     * （有一个失败就轮不到本节点）。选择、并行节点的兄弟分支会不会执行都不定，装饰器的子节点也可能带着
     * 失败上报成功，所以这些容器里的产出不作数——跨分支、跨轮传值请改用黑板变量。</p>
     */
    private void checkTreeDataFlows(BehaviorTreeDefinition tree, Integer spaceId) {
        Map<String, BehaviorBlackboardItem> blackboardItems = new LinkedHashMap<>();
        tree.getBlackboard().forEach(item -> blackboardItems.putIfAbsent(item.getKey(), item));
        var problems = new LinkedHashSet<String>();
        walkTreeDataFlows(tree.getRoots(), false, Map.of(), spaceId, blackboardItems, problems,
                new HashMap<>(), new HashMap<>());
        PreconditionUtils.checkArgument(problems.isEmpty(),
                "行为节点的数据绑定有问题：" + String.join("；", problems), HttpStatus.BAD_REQUEST);
    }

    /**
     * @param ordered   这一层兄弟是不是顺序执行：只有顺序节点才把前面兄弟的产出交给后面的节点
     * @param available 执行到这一层时已经必然成功的行为节点：节点标识 -> 节点
     */
    private void walkTreeDataFlows(List<BehaviorTreeNode> siblings, boolean ordered,
                                   Map<String, BehaviorTreeNode> available, Integer spaceId,
                                   Map<String, BehaviorBlackboardItem> blackboardItems, Set<String> problems,
                                   Map<String, OntologyAction> boundCache, Map<String, List<TreeOutputRow>> rowCache) {
        Map<String, BehaviorTreeNode> seen = new LinkedHashMap<>(available);
        for (var node : siblings) {
            if (node.getKind() == BehaviorNodeKindEnum.ACTION) {
                checkTreeNodeBindings(node, seen, spaceId, blackboardItems, problems, boundCache, rowCache);
            } else {
                walkTreeDataFlows(node.getChildren(), node.getKind() == BehaviorNodeKindEnum.SEQUENCE, seen,
                        spaceId, blackboardItems, problems, boundCache, rowCache);
            }
            if (ordered) {
                guaranteedTreeActions(node).forEach(seen::put);
            }
        }
    }

    /** 该节点上报成功时必然已成功的行为节点：行为节点算自己，顺序节点算全部子树，其余容器与装饰器都不作数 */
    private Map<String, BehaviorTreeNode> guaranteedTreeActions(BehaviorTreeNode node) {
        Map<String, BehaviorTreeNode> guaranteed = new LinkedHashMap<>();
        collectGuaranteedTreeActions(node, guaranteed);
        return guaranteed;
    }

    private void collectGuaranteedTreeActions(BehaviorTreeNode node, Map<String, BehaviorTreeNode> collector) {
        if (node.getKind() == BehaviorNodeKindEnum.ACTION) {
            collector.put(node.getId(), node);
            return;
        }
        if (node.getKind() != BehaviorNodeKindEnum.SEQUENCE) {
            return;
        }
        node.getChildren().forEach(child -> collectGuaranteedTreeActions(child, collector));
    }

    /**
     * 校验一个行为节点上的入参取值绑定与出参回传绑定。
     * <p>结构、命名、类型都在这里对上：执行期只按绑定取值，取不到就是节点失败，不再回头猜。</p>
     */
    private void checkTreeNodeBindings(BehaviorTreeNode node, Map<String, BehaviorTreeNode> available, Integer spaceId,
                                       Map<String, BehaviorBlackboardItem> blackboardItems, Set<String> problems,
                                       Map<String, OntologyAction> boundCache, Map<String, List<TreeOutputRow>> rowCache) {
        var config = readTreeConfig(node, BehaviorActionConfig.class);
        var inputBindings = MapUtils.emptyIfNull(config.getInputBindings());
        var returns = MapUtils.emptyIfNull(config.getOutputBindings());
        if (inputBindings.isEmpty() && returns.isEmpty()) {
            return;
        }
        var nodeLabel = treeNodeLabel(node);
        var bound = treeBoundActionCached(config.getActionApi(), spaceId, boundCache);
        if (!returns.isEmpty() && bound.getActionKind() == ActionKindEnum.BASE) {
            problems.add("节点 " + nodeLabel + " 绑的是属性值/实例关系行为，它不绑算子、执行没有返回体，出参不能回传黑板；"
                    + "要把结果传给后面的节点，请在这条行为之后用绑算子的行为");
        }
        var inputParams = treeInputParamDefs(bound);
        for (var entry : inputBindings.entrySet()) {
            var paramName = StringUtils.trimToEmpty(entry.getKey());
            var target = checkTreeBindingParam(nodeLabel, bound.getApi(), paramName, inputParams, problems);
            if (target == null) {
                continue;
            }
            var binding = entry.getValue();
            var blackboardKey = binding == null ? "" : StringUtils.trimToEmpty(binding.getBlackboardKey());
            var fromNodeId = binding == null ? "" : StringUtils.trimToEmpty(binding.getNodeId());
            var fromOutput = binding == null ? "" : StringUtils.trimToEmpty(binding.getOutput());
            var hasBlackboard = StringUtils.isNotBlank(blackboardKey);
            var hasNodeOutput = StringUtils.isNotBlank(fromNodeId) || StringUtils.isNotBlank(fromOutput);
            if (hasBlackboard && hasNodeOutput) {
                problems.add("节点 " + nodeLabel + " 的入参 " + paramName
                        + " 同时配了黑板键和上游节点出参，一个入参只能有一种取值来源");
                continue;
            }
            if (!hasBlackboard && !hasNodeOutput) {
                problems.add("节点 " + nodeLabel + " 的入参 " + paramName
                        + " 没配取值来源：要么给 blackboardKey，要么给上游节点和出参，两者只给一个也不行");
                continue;
            }
            if (hasBlackboard) {
                checkTreeInputFromBlackboard(nodeLabel, paramName, blackboardKey, target.type(), blackboardItems, problems);
                continue;
            }
            if (StringUtils.isBlank(fromNodeId) || StringUtils.isBlank(fromOutput)) {
                problems.add("节点 " + nodeLabel + " 的入参 " + paramName
                        + " 要取上游节点的出参，上游节点和出参得一起给");
                continue;
            }
            checkTreeInputFromActionOutput(nodeLabel, paramName, target.type(), binding, available, spaceId, problems,
                    boundCache, rowCache);
        }
        for (var entry : returns.entrySet()) {
            var outputKey = StringUtils.trimToEmpty(entry.getKey());
            var outputLabel = StringUtils.defaultIfBlank(outputKey, "（未填写）");
            var key = entry.getValue();
            var item = StringUtils.isBlank(key) ? null : blackboardItems.get(key);
            if (item == null) {
                problems.add("节点 " + nodeLabel + " 的出参 " + outputLabel + " 回传目标不是本树声明的黑板键："
                        + StringUtils.defaultIfBlank(key, "（未选择）"));
                continue;
            }
            var matched = matchTreeOutputRows(
                    treeOutputRowsCached(bound.getApi(), spaceId, boundCache, rowCache), outputKey);
            var row = singleTreeOutputRow(matched, "节点 " + nodeLabel + " 的出参 " + outputLabel, problems);
            if (row == null) {
                continue;
            }
            if (StringUtils.isBlank(row.expression())) {
                problems.add("节点 " + nodeLabel + " 要回传的出参 " + row.displayName() + " 在行为 " + bound.getApi()
                        + " 上没配取值路径，回传取不到值");
                continue;
            }
            // 写回表和回传黑板不冲突：出参照常写回表，黑板键再拿这份值。对象属性来源的键要求
            // 绑定属性就是该出参写回的属性，这样每次按实例回读到的也正是回传值，不会被回读盖掉
            var source = item.resolvedSource();
            if (source == BehaviorBlackboardSourceEnum.PROPERTY) {
                if (StringUtils.isBlank(row.propertyUniqueIdentifier())) {
                    problems.add("节点 " + nodeLabel + " 的出参 " + row.displayName() + " 没有写回属性，"
                            + "不能回传对象属性来源的黑板键 " + key + "：回传值会被该键按实例的属性回读盖掉，"
                            + "要传给后面的节点请改用外部输入来源的键");
                    continue;
                }
                if (!StringUtils.equals(row.propertyUniqueIdentifier(), item.getBindPropertyUniqueIdentifier())) {
                    problems.add("节点 " + nodeLabel + " 的出参 " + row.displayName() + " 写回的属性与黑板键 " + key
                            + " 绑定的属性不是同一个，回传值会被该键按实例的属性回读盖掉；"
                            + "请改绑同一属性的键，或改用外部输入来源的键");
                    continue;
                }
            } else if (source != BehaviorBlackboardSourceEnum.EXTERNAL) {
                problems.add("节点 " + nodeLabel + " 想把出参 " + outputLabel + " 回传黑板键 " + key + "，但它来源是"
                        + source.getName() + "：固定值以声明为准，回传只能选外部输入或写回同一属性的对象属性来源的键");
                continue;
            }
            // LIST 黑板键什么形状都收：通配行回传整批列、数组行整包、单值行按只有一个元素的集合，
            // 形状收口在执行侧按入参声明类型做
            if (item.getValueType() != FunctionParamTypeEnum.LIST
                    && !treeTypesCompatible(row.type(), item.getValueType())) {
                problems.add("节点 " + nodeLabel + " 的出参 " + row.displayName() + "（" + row.type() + "）回传黑板键 "
                        + key + "（" + item.getValueType().name() + "），类型对不上");
            }
        }
    }

    /**
     * inputBindings 的键指向的入参能不能用：得是行为声明的入参。
     * <p>参数名就是绑定自己的键，同一个入参配不成两条。</p>
     */
    private TreeInputParamDef checkTreeBindingParam(String nodeLabel, String actionApi, String paramName,
                                                    List<TreeInputParamDef> inputParams, Set<String> problems) {
        var label = StringUtils.defaultIfBlank(paramName, "（未填写）");
        var target = inputParams.stream().filter(param -> StringUtils.equals(param.name(), paramName))
                .findFirst().orElse(null);
        if (target == null) {
            problems.add("节点 " + nodeLabel + " 的入参 " + label + " 不是行为 " + actionApi + " 声明的入参");
        }
        return target;
    }

    private void checkTreeInputFromBlackboard(String nodeLabel, String paramName, String key,
                                                 FunctionParamTypeEnum paramType,
                                                 Map<String, BehaviorBlackboardItem> blackboardItems,
                                                 Set<String> problems) {
        var item = StringUtils.isBlank(key) ? null : blackboardItems.get(key);
        if (item == null) {
            problems.add("节点 " + nodeLabel + " 的入参 " + paramName + " 引用了未声明的黑板键："
                    + StringUtils.defaultIfBlank(key, "（未选择）"));
            return;
        }
        // LIST 入参取标量黑板键也放行：执行侧按只有一个元素的集合传
        if (paramType != FunctionParamTypeEnum.LIST && !treeTypesCompatible(item.getValueType(), paramType)) {
            problems.add("节点 " + nodeLabel + " 的入参 " + paramName + "（" + paramType + "）取黑板键 " + key + "（"
                    + item.getValueType().name() + "），类型对不上");
        }
    }

    private void checkTreeInputFromActionOutput(String nodeLabel, String paramName, FunctionParamTypeEnum paramType,
                                                     BehaviorInputBinding binding,
                                                     Map<String, BehaviorTreeNode> available, Integer spaceId,
                                                     Set<String> problems, Map<String, OntologyAction> boundCache,
                                                     Map<String, List<TreeOutputRow>> rowCache) {
        var upstream = available.get(binding.getNodeId());
        var fromOutput = StringUtils.defaultIfBlank(binding.getOutput(), "（未选择）");
        if (upstream == null) {
            problems.add("节点 " + nodeLabel + " 的入参 " + paramName
                    + " 引用的上游节点不是它的必然前序：只能引用同一顺序节点里排在它前面的行为节点，选择、并行、循环节点里的产出"
                    + "不保证产生过，跨分支或跨轮传值请改用黑板变量");
            return;
        }
        var upstreamApi = readTreeConfig(upstream, BehaviorActionConfig.class).getActionApi();
        var upstreamAction = treeBoundActionCached(upstreamApi, spaceId, boundCache);
        if (upstreamAction.getActionKind() == ActionKindEnum.BASE) {
            problems.add("节点 " + nodeLabel + " 的入参 " + paramName + " 引用的节点「" + treeNodeLabel(upstream)
                    + "」绑的是属性值/实例关系行为，它不绑算子、执行没有返回体，出参无从取值；"
                    + "请改引用绑算子的行为，或用黑板变量传值");
            return;
        }
        var matched = matchTreeOutputRows(treeOutputRowsCached(upstreamApi, spaceId, boundCache, rowCache),
                StringUtils.trimToEmpty(binding.getOutput()));
        var label = "节点 " + nodeLabel + " 的入参 " + paramName + " 引用的出参 " + fromOutput;
        var row = singleTreeOutputRow(matched, label, problems);
        if (row == null) {
            return;
        }
        if (StringUtils.isBlank(row.expression())) {
            problems.add(label + "（来自节点「" + treeNodeLabel(upstream) + "」的 " + row.displayName() + "）没配取值路径，取不到值");
            return;
        }
        // LIST 入参什么形状都收：通配行取整批列、数组行整包、单值行按只有一个元素的集合，
        // 形状收口在执行侧按声明类型做，这里不做单值类型比对
        if (paramType != FunctionParamTypeEnum.LIST && !treeTypesCompatible(row.type(), paramType)) {
            problems.add(label + "（" + row.type() + "）传给入参 " + paramName + "（" + paramType + "），类型对不上");
        }
    }

    /** 命中一条才可用：一条没有、按字段名命中多条都算绑定写错了，报错时把可点名的写法说清楚 */
    private TreeOutputRow singleTreeOutputRow(List<TreeOutputRow> matched, String label, Set<String> problems) {
        if (matched.isEmpty()) {
            problems.add(label + " 在该行为上不存在，可点名的写法是：属性唯一标识 > 出参字段名 > 顶层算子出参名");
            return null;
        }
        if (matched.size() > 1) {
            problems.add(label + " 命中该行为 " + matched.size() + " 条出参绑定行，请用属性唯一标识点名其中一条");
            return null;
        }
        return matched.get(0);
    }

    /**
     * 出参绑定行的可点名标识匹配，三档优先级与执行侧 outputParams 定位写回行同一种口径。
     * <p>同一份写法在创建、执行、展示三处共用，别各自再实现一套。</p>
     */
    private List<TreeOutputRow> matchTreeOutputRows(List<TreeOutputRow> rows, String key) {
        if (StringUtils.isBlank(key)) {
            return List.of();
        }
        var byProperty = rows.stream().filter(row -> StringUtils.isNotBlank(row.propertyUniqueIdentifier())
                && StringUtils.equals(key, row.propertyUniqueIdentifier())).collect(Collectors.toList());
        if (!byProperty.isEmpty()) {
            return byProperty;
        }
        var byField = rows.stream().filter(row -> StringUtils.isNotBlank(row.parameterName())
                && StringUtils.equals(key, row.parameterName())).collect(Collectors.toList());
        if (!byField.isEmpty()) {
            return byField;
        }
        return rows.stream().filter(row -> StringUtils.isNotBlank(row.functionParamName())
                && StringUtils.equals(key, row.functionParamName())).collect(Collectors.toList());
    }

    /** 出参绑定行对外给出的点名标识：与 {@link #matchTreeOutputRows} 的优先级一致，界面拿它填 fromOutput/output */
    private String treeOutputKey(TreeOutputRow row) {
        return StringUtils.defaultIfBlank(row.propertyUniqueIdentifier(),
                StringUtils.defaultIfBlank(row.parameterName(), row.functionParamName()));
    }

    /** 数值类型之间放行，其余要求一致；认不准的类型宁可在保存期拒绝，也不留到执行期静默传错值 */
    private boolean treeTypesCompatible(FunctionParamTypeEnum from, FunctionParamTypeEnum to) {
        if (from == null || to == null || from == to) {
            return true;
        }
        return TREE_NUMBER_TYPES.contains(from) && TREE_NUMBER_TYPES.contains(to);
    }

    private OntologyAction treeBoundActionCached(String actionApi, Integer spaceId, Map<String, OntologyAction> boundCache) {
        return boundCache.computeIfAbsent(actionApi, api -> getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, api)
                .eq(OntologyAction::getOntologySpaceId, spaceId)));
    }

    private List<TreeOutputRow> treeOutputRowsCached(String actionApi, Integer spaceId,
                                                     Map<String, OntologyAction> boundCache,
                                                     Map<String, List<TreeOutputRow>> rowCache) {
        return rowCache.computeIfAbsent(actionApi,
                api -> treeOutputRows(treeBoundActionCached(api, spaceId, boundCache)));
    }

    /**
     * 行为声明的出参绑定行，带出可点名的三种标识与取值路径。
     * <p>出参行只存属性标识和取值路径，出参名要从算子参数表补；不绑算子的行为没有出参行，
     * 基础函数行为按自己的结果形状声明（聚合是一个统一值一条 / 每个查询字段一条 + 总行数）。</p>
     */
    private List<TreeOutputRow> treeOutputRows(OntologyAction bound) {
        var detail = getActionByApi(bound.getOntologySpaceId(), bound.getApi());
        if (detail == null) {
            return List.of();
        }
        if (isBasicFunctionAction(bound)) {
            return basicFunctionOutputRows(detail.getBasicFunction());
        }
        if (CollectionUtils.isEmpty(detail.getMappingIns())) {
            return List.of();
        }
        var outputRows = detail.getMappingIns().stream()
                .filter(row -> row.getCategory() == FunctionParamCategoryEnum.OUTPUT)
                .collect(Collectors.toList());
        if (outputRows.isEmpty()) {
            return List.of();
        }
        var funcParamIds = outputRows.stream().map(ActionParamMappingVO::getFunctionParamId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, FunctionParamPO> funcParams = funcParamIds.isEmpty() ? Map.of()
                : functionParamMapper.selectBatchIds(funcParamIds).stream()
                .collect(Collectors.toMap(FunctionParamPO::getId, param -> param, (existing, ignored) -> existing));
        return outputRows.stream().map(row -> {
            var funcParam = row.getFunctionParamId() == null ? null : funcParams.get(row.getFunctionParamId());
            // 类型按出参落到的属性给（算子出参常是整批 LIST，展示和兼容校验都该看单条值本身的类型）；
            // 没有属性时按取值路径找到子字段自身的类型，认不出才退回算子出参类型
            var valueType = row.getPropertyType() != null ? toBlackboardValueType(row.getPropertyType())
                    : resolveOutputRowType(funcParam, row.getParameterName(), row.getFunctionParamExpression());
            return new TreeOutputRow(row.getPropertyUniqueIdentifier(), row.getParameterName(),
                    funcParam == null ? null : funcParam.getParamName(), row.getFunctionParamExpression(),
                    valueType,
                    row.getPropertyType(),
                    StringUtils.defaultIfBlank(row.getDisplayName(), row.getParameterName()), row.getOutputMode(),
                    false);
        }).collect(Collectors.toList());
    }

    /**
     * 基础函数行为的出参行：没有算子也没有写回属性，出参就是这次查询结果里的取值位置。
     * <p>点名标识走 parameterName（属性唯一标识那一级没有属性可指），取值路径由执行器按结果 JSON 的形状给，
     * 与 {@code run} 逐项产出的顺序一致。</p>
     */
    private List<TreeOutputRow> basicFunctionOutputRows(ActionBasicFunctionConfig config) {
        return basicFunctionActionExecutor.declareOutputs(config).stream()
                .map(output -> new TreeOutputRow(null, output.key(), null, output.expression(),
                        output.valueType() == null ? FunctionParamTypeEnum.STRING : toBlackboardValueType(output.valueType()),
                        output.valueType(), output.displayName(), ActionOutputModeEnum.RESULT_ONLY, true))
                .collect(Collectors.toList());
    }

    /**
     * 基础函数多对象公共字段：前端选了多个对象后，只能从这批字段里挑查询字段、聚合目标和条件字段。
     * <p>取交集、类型一致性与数据源关联校验都在执行器里，与保存/执行时的校验同一口径。</p>
     */
    @Override
    public BasicFunctionCommonPropertyVO basicFunctionCommonProperties(BasicFunctionCommonPropertyParam param) {
        PreconditionUtils.checkNotNull(param, "要提供对象id", HttpStatus.BAD_REQUEST);
        return basicFunctionActionExecutor.commonProperties(param.getOntologyIdentifiers(), param.getOntologySpaceId());
    }

    /** 基础函数行为：不绑算子，出参与实例要求都按配置里的对象算 */
    private boolean isBasicFunctionAction(OntologyAction bound) {
        return bound.getActionKind() == ActionKindEnum.BASE
                && bound.getSubjectType() == ActionSubjectTypeEnum.BASIC_FUNCTION;
    }

    /** 行为落库的基础函数配置；坏了或没有就按配置不可用报错，不列半套出参 */
    private ActionBasicFunctionConfig basicFunctionConfigOf(OntologyAction bound) {
        return basicFunctionActionExecutor.parse(bound.getBasicFunctionConfig(), bound.getApi());
    }

    /**
     * 基础函数节点可以按对象选实例：选了就把查询收窄到那条数据，不选按该对象全量查。
     * <p>所以这份列表列出来但不强制（{@code entityPrimaryKeyRequired} 仍为 false），
     * 与属性值/实例关系那两类「不给实例就没法定位数据」的要求不同。</p>
     * <p>配了结果回写时还要把回写目标对象也列进来：回写的行挂在哪个实例上只有执行方知道，
     * 这个对象不在查询对象里时更要在这里给出实例，执行侧拿不到会直接报错。</p>
     */
    private List<TreeInstanceRequirementVO> basicFunctionInstanceRequirements(OntologyAction bound) {
        if (!isBasicFunctionAction(bound)) {
            return List.of();
        }
        var config = basicFunctionConfigOf(bound);
        var objectIdentifiers = new LinkedHashSet<>(basicFunctionActionExecutor.configOntologyIdentifiers(config));
        var writeBackTarget = basicFunctionActionExecutor.writeBackTargetIdentifier(config);
        if (StringUtils.isNotBlank(writeBackTarget)) {
            objectIdentifiers.add(writeBackTarget);
        }
        return objectIdentifiers.stream()
                .map(objectId -> TreeInstanceRequirementVO.builder().objectIdentifier(objectId).build())
                .collect(Collectors.toList());
    }

    /**
     * 按出参绑定行从一次执行的返回体里取值，供回传黑板和被下游节点引用。
     * <p>取值路径与出参写回实例同一条，但这里一份只能是一个值：创建侧校验就按属性类型把它当单值，
     * 通配路径展开出的是一批行，黑板键和下游标量入参装不下整批，取批里有值的第一条。</p>
     */
    private Object readTreeOutputValue(String resultJson, TreeOutputRow row) {
        // 路径由自己生成的出参（基础函数）形状是定的：走放宽兜底会按字段名在别的行里找到一个同名值，宁可不放宽
        if (row.exactPath()) {
            return FunctionOutputValueReader.readExact(resultJson, row.expression());
        }
        if (FunctionOutputValueReader.hasWildcard(row.expression())) {
            var values = FunctionOutputValueReader.readMany(resultJson, row.expression(), row.functionParamName());
            if (CollectionUtils.isEmpty(values)) {
                return null;
            }
            return values.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        return FunctionOutputValueReader.read(resultJson, row.expression(), row.functionParamName(), row.propertyType());
    }

    /** 行为的一条出参绑定行：三种可点名标识、取值路径、类型与输出方式；exactPath 表示这条路径按字面取，不做形状放宽 */
    private record TreeOutputRow(String propertyUniqueIdentifier, String parameterName, String functionParamName,
                                 String expression, FunctionParamTypeEnum type, OntologyDataTypeEnum propertyType,
                                 String displayName, ActionOutputModeEnum outputMode, boolean exactPath) {
    }

    /** 收集树里绑定的行为及对应节点：行为 api -> 绑定它的节点，用来描述入参与实例来源 */
    private Map<String, List<BehaviorTreeNode>> collectTreeActionNodes(List<BehaviorTreeNode> nodes) {
        Map<String, List<BehaviorTreeNode>> actionNodes = new LinkedHashMap<>();
        collectTreeActionNodes(nodes, actionNodes);
        return actionNodes;
    }

    private void collectTreeActionNodes(List<BehaviorTreeNode> nodes, Map<String, List<BehaviorTreeNode>> actionNodes) {
        for (var node : nodes) {
            if (node.getKind() == BehaviorNodeKindEnum.ACTION && node.getConfig() != null) {
                var config = readTreeConfig(node, BehaviorActionConfig.class);
                if (StringUtils.isNotBlank(config.getActionApi())) {
                    actionNodes.computeIfAbsent(config.getActionApi(), api -> new ArrayList<>()).add(node);
                }
            }
            collectTreeActionNodes(node.getChildren(), actionNodes);
        }
    }

    /**
     * 行为的外部输入参数：取值来源是外部输入的入参行。
     * <p>出参行的 sourceType 为空，不能当作外部输入；不绑算子的行为（属性值）参数名与类型取属性。</p>
     */
    private List<TreeExternalParam> treeExternalParamDefs(OntologyAction bound) {
        return treeInputParamDefs(bound).stream()
                .filter(param -> param.sourceType() == ActionParamSourceEnum.EXTERNAL)
                .map(param -> new TreeExternalParam(param.name(),
                        param.type() == null ? FunctionParamTypeEnum.STRING : param.type(), param.description()))
                .collect(Collectors.toList());
    }

    /**
     * 行为声明的算子入参，按绑定行带出取值来源。
     * <p>同一参数有多个子字段绑定时合成一条：只要有一行来源是外部输入，这个参数就要调用方整包传值。
     * 来源缺省和创建校验保持一致：绑了算子的按对象属性，不绑算子的基础行为按外部输入。</p>
     * <p>读实例的字段行在同一叶子名落在多个入参（或同一入参的多条路径）上时，名字写成
     * 「父参数名.字段路径」，和行为测试 inputParams 的写法一致：展示、节点绑定、取值覆盖都靠它区分，
     * 不会再把两条轨迹的 longitude 合成一行。外部输入、固定值仍按叶子名，params 是平铺结构。</p>
     */
    private List<TreeInputParamDef> treeInputParamDefs(OntologyAction bound) {
        var mappings = ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>()
                .eq(OntologyActionMappingIn::getOntologyActionId, bound.getId())
                .orderByAsc(OntologyActionMappingIn::getId));
        if (CollectionUtils.isEmpty(mappings)) {
            return List.of();
        }
        Set<Long> funcParamIds = mappings.stream()
                .map(OntologyActionMappingIn::getFunctionParamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, FunctionParamPO> funcParams = funcParamIds.isEmpty() ? Map.of()
                : functionParamMapper.selectBatchIds(funcParamIds).stream()
                .collect(Collectors.toMap(FunctionParamPO::getId, param -> param, (existing, ignored) -> existing));
        Set<String> propertyIds = mappings.stream()
                .map(OntologyActionMappingIn::getPropertyUniqueIdentifier)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        Map<String, OntologyProperty> properties = propertyIds.isEmpty() ? Map.of()
                : ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                .in(OntologyProperty::getUniqueIdentifier, propertyIds)).stream()
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, property -> property,
                        (existing, ignored) -> existing));

        // 叶子名 -> 该名字出现过的「父参数名.字段路径」：多个才需要带上级区分
        Map<String, Set<String>> ownersByField = new LinkedHashMap<>();
        for (var mapping : mappings) {
            var funcParam = mapping.getFunctionParamId() == null ? null : funcParams.get(mapping.getFunctionParamId());
            if (funcParam == null || funcParam.getCategory() != FunctionParamCategoryEnum.INPUT
                    || StringUtils.isBlank(mapping.getParameterName())
                    || StringUtils.isBlank(mapping.getParamPath())
                    || StringUtils.isBlank(funcParam.getParamName())) {
                continue;
            }
            ownersByField.computeIfAbsent(mapping.getParameterName(), k -> new LinkedHashSet<>())
                    .add(funcParam.getParamName() + "." + mapping.getParamPath());
        }

        Map<String, TreeInputParamDef> params = new LinkedHashMap<>();
        for (var mapping : mappings) {
            var funcParam = mapping.getFunctionParamId() == null ? null : funcParams.get(mapping.getFunctionParamId());
            if (funcParam != null && funcParam.getCategory() != FunctionParamCategoryEnum.INPUT) {
                continue;
            }
            if (StringUtils.isBlank(mapping.getParameterName())) {
                continue;
            }
            var property = StringUtils.isBlank(mapping.getPropertyUniqueIdentifier())
                    ? null : properties.get(mapping.getPropertyUniqueIdentifier());
            FunctionParamTypeEnum type = funcParam != null ? funcParam.getParamType()
                    : (property == null ? null : toBlackboardValueType(property.getPropertyType()));
            String description = funcParam != null ? funcParam.getDescription()
                    : (property == null ? null : property.getDisplayName());
            var sourceType = mapping.getSourceType() == null
                    ? (funcParam == null ? ActionParamSourceEnum.EXTERNAL : ActionParamSourceEnum.PROPERTY)
                    : mapping.getSourceType();
            var ownerName = funcParam != null ? funcParam.getParamName() : mapping.getParameterName();
            var key = treeInputKey(mapping, ownerName, sourceType, ownersByField);
            var existing = params.get(key);
            // 参数级取值只能一种来源：有一行要外部输入，整包就由调用方给
            if (existing != null && (existing.sourceType() == ActionParamSourceEnum.EXTERNAL
                    || sourceType != ActionParamSourceEnum.EXTERNAL)) {
                continue;
            }
            // 外部输入和固定值不从属性取值，历史上残留的属性绑定不往外带
            var bindsProperty = sourceType != ActionParamSourceEnum.EXTERNAL
                    && sourceType != ActionParamSourceEnum.FIXED;
            var objectId = !bindsProperty ? null : StringUtils.defaultIfBlank(mapping.getOntologyUniqueIdentifier(),
                    property == null ? null : property.getOntologyUniqueIdentifier());
            params.put(key, new TreeInputParamDef(key, type,
                    description, sourceType, objectId,
                    bindsProperty ? mapping.getPropertyUniqueIdentifier() : null,
                    bindsProperty && property != null ? property.getDisplayName() : null,
                    ownerName,
                    bindsProperty && property != null ? property.getStorageGroup() : null));
        }
        return new ArrayList<>(params.values());
    }

    /**
     * 入参行的键：整包绑定和外部输入/固定值用叶子名或参数名；读实例的字段行在同一叶子名
     * 出现在多个「父参数名.字段路径」上时带上级，和行为测试 inputParams 的限定写法一致。
     * 只有重名才带前缀，没重名的参数保持原名，已存的节点绑定不用跟着改。
     */
    private static String treeInputKey(OntologyActionMappingIn mapping, String ownerName,
                                       ActionParamSourceEnum sourceType,
                                       Map<String, Set<String>> ownersByField) {
        var parameterName = mapping.getParameterName();
        var path = StringUtils.trimToEmpty(mapping.getParamPath());
        if (StringUtils.isBlank(path) || StringUtils.equals(parameterName, ownerName)
                || StringUtils.isBlank(ownerName)
                || sourceType == ActionParamSourceEnum.EXTERNAL || sourceType == ActionParamSourceEnum.FIXED) {
            return parameterName;
        }
        var owners = ownersByField.get(parameterName);
        return owners != null && owners.size() > 1 ? ownerName + "." + path : parameterName;
    }

    /** 属性类型转黑板值类型；黑板没有对应类型的复杂属性按字符串接收 */
    private FunctionParamTypeEnum toBlackboardValueType(OntologyDataTypeEnum type) {
        if (type == null) {
            return FunctionParamTypeEnum.STRING;
        }
        return switch (type) {
            case Bool -> FunctionParamTypeEnum.BOOL;
            case Int -> FunctionParamTypeEnum.INTEGER;
            case Long -> FunctionParamTypeEnum.LONG;
            case Float -> FunctionParamTypeEnum.FLOAT;
            case Short -> FunctionParamTypeEnum.SHORT;
            case Byte -> FunctionParamTypeEnum.BYTE;
            case Double, Decimal -> FunctionParamTypeEnum.DOUBLE;
            case Date, Timestamp -> FunctionParamTypeEnum.DATE;
            case Array -> FunctionParamTypeEnum.LIST;
            case Map, Ontology -> FunctionParamTypeEnum.OBJECT;
            default -> FunctionParamTypeEnum.STRING;
        };
    }

    private List<TreeActionParamVO> toTreeActionParamVOs(List<TreeExternalParam> params) {
        return params.stream()
                .map(param -> TreeActionParamVO.builder()
                        .name(param.name())
                        .type(param.type())
                        .description(param.description())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 按绑定行为反推执行要求：逐个行为节点列出实例要求、算子入参（带取值来源）和写回位置，调用方按节点在 nodeExecutes 里给数据。
     * <p>节点上配了取值来源（黑板变量/上游行为出参）的入参从这份要求里去掉：值由节点配置供，不再向调用方要。
     * 黑板键的来源是固定值时用黑板项自己的取值，外部输入不传就没有值（条件按空判断、绑到入参时取不到值节点失败），
     * 对象属性按库里的当前值刷新——属性所属的对象在 blackboardInstances 里单独列出，执行前要选好实例；
     * 属性不在主属性分组的，还要再选该分组的一行（blackboardInstances[].storageGroups），按 inputDataPrimaryKeys 传。</p>
     */
    private TreeExecuteInputsVO buildTreeExecuteInputs(BehaviorTreeDefinition tree, Integer spaceId) {
        Map<String, TreeActionNodeTemplate> templates = new HashMap<>();
        var nodeViews = new ArrayList<TreeExecuteNodeVO>();
        for (var node : treeActionNodesInOrder(tree.getRoots())) {
            var actionApi = readTreeConfig(node, BehaviorActionConfig.class).getActionApi();
            var template = templates.computeIfAbsent(actionApi, api -> buildTreeActionNodeTemplate(api, spaceId));
            // 悬空引用由节点执行时上报，这里只描述能用的行为
            if (template == null) {
                continue;
            }
            var boundParams = treeNodeBoundParams(node);
            var inputParams = boundParams.isEmpty() ? template.inputParams() : template.inputParams().stream()
                    .filter(input -> !boundParams.contains(input.getKey()))
                    .collect(Collectors.toList());
            nodeViews.add(TreeExecuteNodeVO.builder()
                    .nodeId(node.getId())
                    .nodeDisplayName(node.getDisplayName())
                    .actionApi(actionApi)
                    .actionDisplayName(template.actionDisplayName())
                    .entityPrimaryKeyRequired(template.entityPrimaryKeyRequired())
                    .instanceRequirements(template.instanceRequirements())
                    .inputParams(inputParams)
                    .outputParams(template.outputParams())
                    .build());
        }
        var blackboardInstances = new ArrayList<TreeBlackboardInstanceVO>();
        var rowGroupsByObject = treeBlackboardPropertyRowGroups(tree);
        treeBlackboardPropertyObjects(tree).forEach((objectId, keys) -> {
            var storageGroups = new ArrayList<TreeBlackboardGroupVO>();
            rowGroupsByObject.getOrDefault(objectId, Map.of()).forEach((group, groupKeys) ->
                    storageGroups.add(TreeBlackboardGroupVO.builder()
                            .storageGroup(group)
                            .blackboardKeys(groupKeys)
                            .build()));
            blackboardInstances.add(TreeBlackboardInstanceVO.builder()
                    .objectIdentifier(objectId)
                    .blackboardKeys(keys)
                    .storageGroups(storageGroups)
                    .build());
        });
        return TreeExecuteInputsVO.builder()
                .entityPrimaryKeyRequired(!blackboardInstances.isEmpty()
                        || nodeViews.stream().anyMatch(TreeExecuteNodeVO::isEntityPrimaryKeyRequired))
                .nodes(nodeViews)
                .blackboardInstances(blackboardInstances)
                .build();
    }

    /** 一个行为在树节点上的执行要求；同一行为被多个节点绑定时共用一份 */
    private record TreeActionNodeTemplate(String actionDisplayName, boolean entityPrimaryKeyRequired,
                                          List<TreeInstanceRequirementVO> instanceRequirements,
                                          List<TreeExecuteInputVO> inputParams,
                                          List<TreeOutputTargetVO> outputParams) {
    }

    /** 按树中顺序收集行为节点：同一行为绑在多个节点上时各节点各一条要求，不按行为合并 */
    private List<BehaviorTreeNode> treeActionNodesInOrder(List<BehaviorTreeNode> nodes) {
        var result = new ArrayList<BehaviorTreeNode>();
        collectTreeActionNodesInOrder(nodes, result);
        return result;
    }

    private void collectTreeActionNodesInOrder(List<BehaviorTreeNode> nodes, List<BehaviorTreeNode> result) {
        for (var node : nodes) {
            if (node.getKind() == BehaviorNodeKindEnum.ACTION && node.getConfig() != null
                    && StringUtils.isNotBlank(readTreeConfig(node, BehaviorActionConfig.class).getActionApi())) {
                result.add(node);
            }
            collectTreeActionNodesInOrder(node.getChildren(), result);
        }
    }

    /** 行为在本空间的执行要求；行为不存在（悬空引用）返回 null */
    private TreeActionNodeTemplate buildTreeActionNodeTemplate(String actionApi, Integer spaceId) {
        var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        if (bound == null) {
            return null;
        }
        List<TreeExecuteInputVO> inputParams = treeInputParamDefs(bound).stream()
                .<TreeExecuteInputVO>map(param -> TreeExecuteInputVO.builder()
                        .key(param.name())
                        .paramName(param.paramName())
                        .valueType(param.type())
                        .required(param.sourceType() == ActionParamSourceEnum.EXTERNAL)
                        .sourceType(param.sourceType())
                        .objectIdentifier(param.objectIdentifier())
                        .propertyUniqueIdentifier(param.propertyUniqueIdentifier())
                        .propertyDisplayName(param.propertyDisplayName())
                        .storageGroup(param.storageGroup())
                        .description(param.description())
                        .build())
                .collect(Collectors.toList());
        var needsInstance = treeActionNeedsEntity(bound);
        return new TreeActionNodeTemplate(bound.getDisplayName(), needsInstance,
                needsInstance ? treeNodeInstanceRequirements(bound) : basicFunctionInstanceRequirements(bound),
                inputParams, treeOutputTargets(bound));
    }

    /** 节点要选实例的对象：按对象逐条列出，关系对端用 from、to 给 */
    private List<TreeInstanceRequirementVO> treeNodeInstanceRequirements(OntologyAction bound) {
        var linkEnds = treeActionLinkEnds(bound);
        var ownObject = ownTreeObject(bound);
        // 关系对端也可以按对象选实例；自关系（对端就是本对象）不能用同一个实例顶替，仍要走 from/to
        var counterpartObject = linkEnds == null ? null : linkEnds.counterpartOf(ownObject);
        if (StringUtils.equals(counterpartObject, ownObject)) {
            counterpartObject = null;
        }
        // 一个行为可能读写多个对象类型：按对象展开，每个对象要哪条实例都由执行请求按对象给
        var objectIds = new ArrayList<>(treeActionKeyRequiredObjects(bound));
        if (objectIds.isEmpty() && StringUtils.isNotBlank(ownObject)) {
            // 存量映射行没写对象标识，只能按行为作用的对象描述；挂了多个对象或认不出时为空，
            // 不往列表里塞 null，否则前端会拿到一条没有对象的实例要求
            objectIds.add(ownObject);
        }
        var requirements = new ArrayList<TreeInstanceRequirementVO>();
        for (var objectId : objectIds) {
            requirements.add(TreeInstanceRequirementVO.builder()
                    .objectIdentifier(objectId)
                    .linkEntityPrimaryKeyRequired(linkEnds != null)
                    .linkFromObjectIdentifier(linkEnds == null ? null : linkEnds.fromObject())
                    .linkToObjectIdentifier(linkEnds == null ? null : linkEnds.toObject())
                    .selfRelation(linkEnds != null && linkEnds.selfRelation())
                    .linkObjectIdentifier(counterpartObject)
                    .build());
        }
        return requirements;
    }

    /**
     * 行为节点的出参：绑算子的行为按出参绑定行逐条列出——写属性的给出数据位置（key 用属性唯一标识，
     * 出参写回侧就是按它点名的），仅返回的只报名（mode=RESULT_ONLY）；属性值-修改/删除这类不绑算子的行为
     * 只写一行，用 dataPrimaryKey 传行主键。
     * <p>主表行就是实例本身，不给行主键；通配出参逐元素新增子表行，也不给行主键。</p>
     */
    private List<TreeOutputTargetVO> treeOutputTargets(OntologyAction bound) {
        var targets = new ArrayList<TreeOutputTargetVO>();
        if (isBasicFunctionAction(bound)) {
            // 基础函数的出参一律是「随结果返回」：配了结果回写也是执行侧直接落库，黑板那一层不回读落库结果，所以不给数据位置
            return basicFunctionOutputRows(basicFunctionConfigOf(bound)).stream()
                    .map(row -> TreeOutputTargetVO.builder()
                            .mode(TreeOutputTargetModeEnum.RESULT_ONLY)
                            .parameterName(row.parameterName())
                            .displayName(row.displayName())
                            .build())
                    .collect(Collectors.toList());
        }
        if (bound.getActionKind() == ActionKindEnum.BASE) {
            var group = bound.getStorageGroup();
            if (bound.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE
                    && bound.getActionOperation() != null
                    && bound.getActionOperation() != ActionOperationEnum.CREATE
                    && StringUtils.isNotBlank(group) && !MAIN_STORAGE_GROUP.equals(group)) {
                targets.add(TreeOutputTargetVO.builder()
                        .mode(TreeOutputTargetModeEnum.DATA_PRIMARY_KEY)
                        .displayName(bound.getDisplayName())
                        .objectIdentifier(ownTreeObject(bound))
                        .storageGroup(group)
                        .rowRequired(true)
                        .build());
            }
            return targets;
        }
        var detail = getActionByApi(bound.getOntologySpaceId(), bound.getApi());
        if (detail == null || CollectionUtils.isEmpty(detail.getMappingIns())) {
            return targets;
        }
        var legacyObjectId = ownTreeObject(bound);
        for (var row : detail.getMappingIns()) {
            if (row.getCategory() != FunctionParamCategoryEnum.OUTPUT) {
                continue;
            }
            // 仅返回的出参没有写回位置，只报名，让调用方知道节点有哪些出参
            if (row.getOutputMode() != ActionOutputModeEnum.WRITE_PROPERTY
                    || StringUtils.isBlank(row.getPropertyUniqueIdentifier())) {
                targets.add(TreeOutputTargetVO.builder()
                        .mode(TreeOutputTargetModeEnum.RESULT_ONLY)
                        .parameterName(row.getParameterName())
                        .displayName(StringUtils.defaultIfBlank(row.getDisplayName(), row.getParameterName()))
                        .build());
                continue;
            }
            var group = row.getStorageGroup();
            var onMainTable = StringUtils.isBlank(group) || MAIN_STORAGE_GROUP.equals(group);
            targets.add(TreeOutputTargetVO.builder()
                    .mode(TreeOutputTargetModeEnum.OUTPUT_PARAM)
                    .key(row.getPropertyUniqueIdentifier())
                    .parameterName(row.getParameterName())
                    .displayName(StringUtils.defaultIfBlank(row.getDisplayName(), row.getParameterName()))
                    .objectIdentifier(StringUtils.defaultIfBlank(row.getOntologyUniqueIdentifier(), legacyObjectId))
                    .storageGroup(group)
                    .rowRequired(!onMainTable && !FunctionOutputValueReader.hasWildcard(row.getFunctionParamExpression()))
                    .build());
        }
        return targets;
    }

    /** 行为绑定算子的参数定义；不绑算子、算子已下架或取不到时返回空，调用方按不拦的口径处理 */
    private List<FunctionParameterVO> treeFunctionParams(OntologyAction bound) {
        if (bound == null || StringUtils.isBlank(bound.getFunctionApi())) {
            return List.of();
        }
        try {
            var detail = functionService.getFunctionDetailByApi(bound.getOntologySpaceId(), bound.getFunctionApi(), null);
            return detail == null || detail.getParams() == null ? List.of() : detail.getParams();
        } catch (Exception e) {
            log.warn("取算子参数定义失败, actionApi={}, functionApi={}", bound.getApi(), bound.getFunctionApi(), e);
            return List.of();
        }
    }

    /**
     * 节点要实例才能执行的行为：实例关系按 from/to 给两端；属性值行为必须定位到一行实例。
     * <p>绑了算子的行为（应用行为、基础算子）不再要求节点级实例：入参按参数用 inputParams 选实例、
     * 写回按 outputParams 按行选实例，与单行为测试一致；没按行给位置时执行侧按空实例处理。</p>
     */
    private boolean treeActionNeedsEntity(OntologyAction bound) {
        return treeActionLinkEnds(bound) != null
                || (bound.getActionKind() == ActionKindEnum.BASE
                && bound.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE);
    }

    /**
     * 执行前复制一份定义并把历史定义缺失的黑板来源补齐，编译缓存里的定义不能改。
     * 行为外部入参不再补成黑板键，只按执行请求 nodeExecutes 给。
     */
    private BehaviorTreeDefinition prepareTreeDefinition(BehaviorTreeDefinition definition) {
        var prepared = readTreeDefinition(writeTreeDefinition(definition));
        normalizeTreeBlackboardSources(prepared);
        return prepared;
    }

    /** 历史定义没有 source：绑了属性的按对象属性，否则按外部输入；只补内存里的副本，不改库 */
    private void normalizeTreeBlackboardSources(BehaviorTreeDefinition tree) {
        tree.getBlackboard().forEach(item -> {
            if (item.getSource() == null) {
                item.setSource(item.resolvedSource());
            }
        });
    }

    /** 节点上配了取值来源的入参名：这些参数由黑板变量或上游出参供值，执行请求不再给 */
    private Set<String> treeNodeBoundParams(BehaviorTreeNode node) {
        if (node.getConfig() == null) {
            return Set.of();
        }
        return MapUtils.emptyIfNull(readTreeConfig(node, BehaviorActionConfig.class).getInputBindings()).keySet()
                .stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 执行前按节点校验行为要的数据是否配齐：行为外部入参只认节点自己的那份，没有全树默认值可回退，
     * 缺什么一次列清楚，避免行为拿着 null 跑出误导结果。
     * <p>节点上配了取值来源的入参不在要求之列——值由黑板或上游出参供，请求再传一份就是两处供值，直接拒绝。</p>
     *
     * @param requireInstances 实例是否必须由调用方给；批量执行的实例由遍历提供时传 false
     */
    private void checkTreeExecuteInputs(BehaviorTreeDefinition tree, Integer spaceId, TreeExecutionRuntime runtime,
                                        boolean requireInstances) {
        var problems = new ArrayList<String>();
        var missingParams = new LinkedHashSet<String>();
        var unknownParams = new LinkedHashSet<String>();
        var boundParamsSupplied = new LinkedHashSet<String>();
        var missingInstances = new LinkedHashSet<String>();
        var missingLinks = new LinkedHashSet<String>();
        for (var entry : collectTreeActionNodes(tree.getRoots()).entrySet()) {
            var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                    .eq(OntologyAction::getApi, entry.getKey())
                    .eq(OntologyAction::getOntologySpaceId, spaceId));
            // 悬空引用由节点执行时上报，这里不抢先中断
            if (bound == null) {
                continue;
            }
            var declaredParams = treeExternalParamDefs(bound).stream()
                    .map(TreeExternalParam::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (var node : entry.getValue()) {
                var nodeId = node.getId();
                var nodeLabel = treeNodeLabel(node);
                var boundParams = treeNodeBoundParams(node);
                for (var paramName : declaredParams) {
                    if (!boundParams.contains(paramName) && runtime.nodeParamValue(nodeId, paramName) == null) {
                        missingParams.add(paramName + "（节点 " + nodeLabel + "）");
                    }
                }
                runtime.nodeParamNames(nodeId).stream()
                        .filter(boundParams::contains)
                        .forEach(paramName -> boundParamsSupplied.add(paramName + "（节点 " + nodeLabel + "）"));
                runtime.nodeParamNames(nodeId).stream()
                        .filter(paramName -> !declaredParams.contains(paramName) && !boundParams.contains(paramName))
                        .forEach(paramName -> unknownParams.add(paramName + "（节点 " + nodeLabel + "）"));
                // 绑算子的行为读写定位由 inputParams、outputParams 按参数/按行给，和单行为测试一样不要求节点实例
                if (!requireInstances || !treeActionNeedsEntity(bound)) {
                    continue;
                }
                // 关系行为按单行为测试的口径传关系两端：nodeExecutes[节点].entityPrimaryKeys 里的 from、to
                var nodeKeys = runtime.nodeEntityPrimaryKeys(nodeId);
                var hasBothLinkEnds = nodeKeys.get(TEST_LINK_FROM_KEY) != null && nodeKeys.get(TEST_LINK_TO_KEY) != null;
                var linkEnds = treeActionLinkEnds(bound);
                for (var objectId : treeActionKeyRequiredObjects(bound)) {
                    if (hasBothLinkEnds && linkEnds != null
                            && (StringUtils.equals(objectId, linkEnds.fromObject())
                            || StringUtils.equals(objectId, linkEnds.toObject()))) {
                        // 关系两端的实例由 from/to 给，不再要求按对象再选一次
                        continue;
                    }
                    if (runtime.nodeEntityPrimaryKey(nodeId, objectId) == null) {
                        missingInstances.add("节点 " + nodeLabel + " 的对象 " + objectId);
                    }
                }
                if (treeInstanceNeedLink(bound) && !hasBothLinkEnds
                        && runtime.nodeLinkEntityPrimaryKey(nodeId) == null
                        && runtime.nodeMappedEntityPrimaryKey(nodeId, treeActionCounterpartObject(bound)) == null) {
                    missingLinks.add("节点 " + nodeLabel);
                }
            }
        }
        if (requireInstances) {
            var missingPropertyInstances = new LinkedHashSet<String>();
            treeBlackboardPropertyObjects(tree).forEach((objectId, keys) -> {
                if (runtime.executionEntityPrimaryKey(objectId) == null) {
                    missingPropertyInstances.add(objectId + "（黑板键 " + String.join("、", keys) + "）");
                }
            });
            if (!missingPropertyInstances.isEmpty()) {
                problems.add("缺少实例：" + String.join("、", missingPropertyInstances)
                        + "；来源是对象属性的黑板键要按对象选实例才能读到值，请用 entityPrimaryKey 或 entityPrimaryKeys 传");
            }
            // 非主分组的属性一行定不到值：实例只定位对象，分组行还要单独选。dataPrimaryKey 是历史口径，仍认
            var missingPropertyRows = new LinkedHashSet<String>();
            if (runtime.executionDataPrimaryKey() == null) {
                treeBlackboardPropertyRowGroups(tree).forEach((objectId, groupsByName) ->
                        groupsByName.forEach((group, keys) -> {
                            var rowKey = runtime.executionInputDataPrimaryKeys()
                                    .getOrDefault(objectId, Map.of()).get(group);
                            if (rowKey == null) {
                                missingPropertyRows.add("对象 " + objectId + " 的分组 " + group
                                        + "（黑板键 " + String.join("、", keys) + "）");
                            }
                        }));
            }
            if (!missingPropertyRows.isEmpty()) {
                problems.add("缺少分组行数据：" + String.join("、", missingPropertyRows)
                        + "；黑板键绑定的属性不在主属性分组时要按行回读，"
                        + "请在 inputDataPrimaryKeys[对象标识][分组] 里传该组行主键");
            }
        }
        if (!missingParams.isEmpty()) {
            problems.add("缺少行为入参：" + String.join("、", missingParams)
                    + "；每个节点只用自己 nodeExecutes[节点标识].params 里的值，请按节点补齐");
        }
        if (!unknownParams.isEmpty()) {
            problems.add("行为没有这些外部入参：" + String.join("、", unknownParams)
                    + "；nodeExecutes 的 params 只能传该行为声明的外部输入参数名");
        }
        if (!boundParamsSupplied.isEmpty()) {
            problems.add("这些入参已在节点上配了取值来源，执行请求不要再传值："
                    + String.join("、", boundParamsSupplied) + "；值由节点配的来源供，请从 nodeExecutes 的 params 里去掉");
        }
        if (!missingInstances.isEmpty()) {
            problems.add("缺少实例：" + String.join("、", missingInstances)
                    + "；请用 entityPrimaryKey、按对象的 entityPrimaryKeys 或 nodeExecutes 传");
        }
        if (!missingLinks.isEmpty()) {
            problems.add("缺少关系两端实例：" + String.join("、", missingLinks)
                    + "；请在 nodeExecutes[节点标识].entityPrimaryKeys 里用 from、to 成对传关系两端的实例主键，"
                    + "或用 linkEntityPrimaryKey 补对端");
        }
        if (!problems.isEmpty()) {
            throw new BusinessException("行为树执行前数据没配齐：" + String.join("；", problems), HttpStatus.BAD_REQUEST);
        }
    }

    /** 该行为是否要关系对端主键；对端是绑定关系时的对端对象，自关系只能显式传 */
    private boolean treeInstanceNeedLink(OntologyAction bound) {
        return treeActionLinkEnds(bound) != null;
    }

    /**
     * 批量执行按一个对象的实例逐条跑，实例主键只对该对象类型有效：
     * 树里若还有别的对象类型要用实例数据，批量请求没有任何地方能给，只能在执行前拦下。
     */
    private void checkTreeBatchInstanceScope(TreeExecuteInputsVO plan, TreeBatchExecuteParam param) {
        var traversalObject = param.getOntologyUniqueIdentifier();
        var mismatched = new ArrayList<String>();
        for (var node : plan.getNodes()) {
            node.getInstanceRequirements().stream()
                    .filter(requirement -> StringUtils.isNotBlank(requirement.getObjectIdentifier()))
                    .filter(requirement -> !StringUtils.equals(requirement.getObjectIdentifier(), traversalObject))
                    .forEach(requirement -> mismatched.add("行为 " + node.getActionApi()
                            + "（对象 " + requirement.getObjectIdentifier() + "，节点 " + node.getNodeId() + "）"));
        }
        plan.getBlackboardInstances().stream()
                .filter(requirement -> StringUtils.isNotBlank(requirement.getObjectIdentifier()))
                .filter(requirement -> !StringUtils.equals(requirement.getObjectIdentifier(), traversalObject))
                .forEach(requirement -> mismatched.add("黑板键 " + String.join("、", requirement.getBlackboardKeys())
                        + "（对象 " + requirement.getObjectIdentifier() + "）"));
        if (mismatched.isEmpty()) {
            return;
        }
        var detail = mismatched.stream().distinct().collect(Collectors.joining("；"));
        throw new BusinessException("批量执行遍历的对象是 " + traversalObject
                + "，每条实例只给这个对象的实例主键，但树里这些行为还要按各自对象取实例：" + detail
                + "；请改用单次执行，在 entityPrimaryKeys 里按对象传实例主键", HttpStatus.BAD_REQUEST);
    }

    /**
     * 批量执行只按对象遍历实例，没有按分组再选行的位置：黑板键绑定的属性在非主属性分组时，
     * 每条实例该读哪一行无从指定，只能靠 dataPrimaryKey 指一行（历史口径），否则执行前拦下。
     */
    private void checkTreeBatchBlackboardRows(BehaviorTreeDefinition tree, TreeBatchExecuteParam param) {
        if (param.getDataPrimaryKey() != null) {
            return;
        }
        var missing = new ArrayList<String>();
        treeBlackboardPropertyRowGroups(tree).forEach((objectId, groupsByName) ->
                groupsByName.forEach((group, keys) -> missing.add("对象 " + objectId + " 的分组 " + group
                        + "（黑板键 " + String.join("、", keys) + "）")));
        if (missing.isEmpty()) {
            return;
        }
        throw new BusinessException("批量执行没有给这些分组选行数据的位置：" + String.join("、", missing)
                + "；黑板键绑定的属性不在主属性分组时要按行回读，请改用单次执行在 inputDataPrimaryKeys[对象标识][分组] 里传行主键，"
                + "或给 dataPrimaryKey 指定一行", HttpStatus.BAD_REQUEST);
    }

    /**
     * 批量执行的实例由遍历逐条给出，按节点传的数据里只有外部入参对所有实例通用：
     * 定位实例的字段在这里没有来源，静默忽略等于让调用方以为已经传进去了。
     */
    private void checkTreeBatchNodeExecutes(TreeBatchExecuteParam param) {
        if (MapUtils.isEmpty(param.getNodeExecutes())) {
            return;
        }
        var invalid = param.getNodeExecutes().entrySet().stream()
                .filter(entry -> hasTreeNodeInstanceData(entry.getValue()))
                .map(entry -> "节点 " + entry.getKey())
                .collect(Collectors.joining("、"));
        PreconditionUtils.checkArgument(StringUtils.isBlank(invalid),
                "批量执行按节点传的执行数据里带了定位实例的字段：" + invalid
                        + "；批量执行的实例由遍历逐条给出，nodeExecutes 只能传 params（行为的外部入参），"
                        + "按实例传数据请改用单次执行", HttpStatus.BAD_REQUEST);
    }

    private static boolean hasTreeNodeInstanceData(TreeNodeExecuteParam data) {
        return data != null && (data.getEntityPrimaryKey() != null
                || MapUtils.isNotEmpty(data.getEntityPrimaryKeys())
                || data.getDataPrimaryKey() != null
                || MapUtils.isNotEmpty(data.getInputDataPrimaryKeys())
                || MapUtils.isNotEmpty(data.getOutputDataPrimaryKeys())
                || MapUtils.isNotEmpty(data.getInputParams())
                || MapUtils.isNotEmpty(data.getOutputParams())
                || data.getLinkEntityPrimaryKey() != null);
    }

    /** 行为的一个外部输入参数：名字是 nodeExecutes.params 的键，类型和说明用于执行要求 */
    private record TreeExternalParam(String name, FunctionParamTypeEnum type, String description) {
    }

    /**
     * 行为声明的一个算子入参：名字、类型、取值来源，属性来源时带绑定属性和它所在的存储分组，用于执行要求逐条列出。
     * <p>paramName 是所属顶层算子入参名：LIST 的元素字段行按字段名逐条列出，数据位置按参数名或该字段名给。
     * 叶子名在多个入参上重复时 name 带上级（父参数名.字段路径），paramName 仍是顶层参数名。</p>
     */
    private record TreeInputParamDef(String name, FunctionParamTypeEnum type, String description,
                                     ActionParamSourceEnum sourceType, String objectIdentifier,
                                     String propertyUniqueIdentifier, String propertyDisplayName,
                                     String paramName, String storageGroup) {
    }

    /**
     * 比较方式与黑板键类型必须配得上，否则运行时是拿字符串比大小这类静默错。
     */
    private void checkTreeCompareOpType(String nodeLabel, BehaviorCompareOpEnum op, FunctionParamTypeEnum declared,
                                        String leftLabel) {
        if (op == BehaviorCompareOpEnum.GT || op == BehaviorCompareOpEnum.GE
                || op == BehaviorCompareOpEnum.LT || op == BehaviorCompareOpEnum.LE) {
            PreconditionUtils.checkArgument(TREE_NUMBER_TYPES.contains(declared),
                    "节点 " + nodeLabel + " 的" + op.getName() + "只支持数值类型，" + leftLabel + "是 " + declared.name(),
                    HttpStatus.BAD_REQUEST);
        }
        if (op == BehaviorCompareOpEnum.CONTAINS || op == BehaviorCompareOpEnum.NOT_CONTAINS) {
            PreconditionUtils.checkArgument(declared == FunctionParamTypeEnum.STRING || declared == FunctionParamTypeEnum.LIST,
                    "节点 " + nodeLabel + " 的" + op.getName() + "只支持字符串和列表，" + leftLabel + "是 " + declared.name(),
                    HttpStatus.BAD_REQUEST);
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
        var nodeLabel = treeNodeLabel(node);
        PreconditionUtils.checkNotNull(node.getConfig(), "节点 " + nodeLabel + " 缺少" + kindName + "配置", HttpStatus.BAD_REQUEST);
        try {
            return jsonMapper.convertValue(node.getConfig(), configType);
        } catch (IllegalArgumentException e) {
            // convertValue 把 Jackson 的异常包在 IllegalArgumentException 里，取原始消息才看得出没对上的是哪个字段
            var cause = e.getCause();
            var detail = cause instanceof JsonMappingException mapping ? mapping.getOriginalMessage() : e.getMessage();
            throw new BusinessException("节点 " + nodeLabel + " 的" + kindName + "配置不对：" + detail, HttpStatus.BAD_REQUEST);
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
        // 行为树没有调度入口，对外执行都是手动触发：一次请求记一条手动执行日志
        return manualExecuteLogService.runAndLog(param.getOntologySpaceId(), param.getTreeApi(), param,
                () -> runTreeExecution(param), this::treeResultMessage);
    }

    private TreeExecuteResultVO runTreeExecution(TreeExecuteParam param) {
        var compiled = btTreeManager.getTree(param.getOntologySpaceId(), param.getTreeApi());
        var definition = prepareTreeDefinition(compiled.definition());
        var runtime = buildTreeRuntime(compiled, definition, param);
        checkTreeExecuteInputs(definition, param.getOntologySpaceId(), runtime, true);
        return runTreeInstance(compiled, runtime);
    }

    /**
     * 行为树测试：草稿树也能试跑，用于发布前验证编排；真实落库，与正式执行共用同一套运行时。
     * <p>草稿可能存着早于当前校验规则的配置，试跑前按保存期同一套规则再校验一遍，把悬空引用之类的问题先拦下。</p>
     */
    @Override
    public TreeExecuteResultVO testTree(TreeExecuteParam param) {
        return manualExecuteLogService.runAndLog(param.getOntologySpaceId(), param.getTreeApi(), param,
                () -> runTreeTest(param), this::treeResultMessage);
    }

    private TreeExecuteResultVO runTreeTest(TreeExecuteParam param) {
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
        // 校验会把编译用的定义补成完整契约，试跑同样要求调用方补齐外部入参与实例
        var definition = prepareTreeDefinition(compiled.definition());
        var runtime = buildTreeRuntime(compiled, definition, param);
        checkTreeExecuteInputs(definition, param.getOntologySpaceId(), runtime, true);
        return runTreeInstance(compiled, runtime);
    }

    /**
     * 批量执行行为树：按对象类型遍历实例数据，每条实例独立初始化黑板后跑同一棵树，实例之间互不影响。
     * <p>分片规则与行为批量执行一致（下标取模），供调度侧横向拆分；明细默认只返回失败实例，
     * 避免万级实例时响应体过大。实例主键按主键升序分页拉取、逐页执行，不会一次把整表主键读进内存。</p>
     */
    @Override
    public TreeBatchExecuteResultVO executeTreeBatch(TreeBatchExecuteParam param) {
        // 一次批量请求记一条手动执行日志，实例明细只在那一条的结果里
        return manualExecuteLogService.runAndLog(param.getOntologySpaceId(), param.getTreeApi(), param,
                () -> runTreeBatchExecution(param), this::treeBatchResultMessage);
    }

    private TreeBatchExecuteResultVO runTreeBatchExecution(TreeBatchExecuteParam param) {
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
        // 批量执行每条实例各带一个主键，这里只校验输入参数，实例由遍历结果提供
        var definition = prepareTreeDefinition(compiled.definition());
        checkTreeBatchNodeExecutes(param);
        var plan = buildTreeExecuteInputs(definition, param.getOntologySpaceId());
        checkTreeExecuteInputs(definition, param.getOntologySpaceId(),
                buildTreeRuntime(compiled, definition, null, param), false);
        checkTreeBatchInstanceScope(plan, param);
        checkTreeBatchBlackboardRows(definition, param);
        var instanceSource = treeBatchInstanceSource(param.getOntologySpaceId(), param.getOntologyUniqueIdentifier());
        var includeSuccess = Boolean.TRUE.equals(param.getIncludeSuccess());
        var results = new ArrayList<TreeBatchInstanceResultVO>();
        var total = 0;
        var successCount = 0;
        // 分片下标按跨页累计的实例序号取模，翻页不改变分片归属
        var index = 0;
        Object lastKey = null;
        while (true) {
            var page = pageTreeInstancePrimaryKeys(instanceSource, lastKey, TREE_BATCH_PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            for (var entityPrimaryKey : page) {
                if (shardTotal != null && index++ % shardTotal != shardIndex) {
                    continue;
                }
                total++;
                TreeBatchInstanceResultVO instanceResult;
                try {
                    var executed = runTreeInstance(compiled, buildTreeRuntime(compiled, definition, entityPrimaryKey, param));
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
            if (page.size() < TREE_BATCH_PAGE_SIZE) {
                break;
            }
            lastKey = page.get(page.size() - 1);
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
        // 目录只下发容器和装饰器：行为节点从 availableActions 生成，黑板条件和表达式条件不再对外提供新建；
        // 三种叶子 kind 的保存校验与执行照旧，存量树不受影响
        var nodes = Arrays.stream(BehaviorNodeKindEnum.values())
                .filter(kind -> kind.getCategory() != BehaviorNodeCategoryEnum.LEAF)
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

    /** 可绑定行为的参数按算子定义给出：外部输入参数由执行请求按参数名给，前端据此生成执行表单 */
    private TreeAvailableActionVO toTreeAvailableAction(OntologyAction action) {
        var builder = TreeAvailableActionVO.builder()
                .actionApi(action.getApi())
                .displayName(action.getDisplayName())
                .actionKind(action.getActionKind())
                .subjectType(action.getSubjectType())
                .functionApi(action.getFunctionApi())
                // 执行时全树共用一个实例主键，目录里先标明哪些行为离不开实例
                .requiresInstance(treeActionNeedsEntity(action))
                .objectIdentifier(ownTreeObject(action))
                .instanceObjects(new ArrayList<>(treeActionKeyRequiredObjects(action)))
                // 外部输入参数才是执行时要调用方补的，属性/固定值来源不需要，目录里单独列出来
                .externalParams(toTreeActionParamVOs(treeExternalParamDefs(action)))
                // 节点上能改取值来源的入参：与执行要求、保存校验用的是同一套参数名
                .bindableInputs(treeInputParamDefs(action).stream()
                        .map(param -> TreeActionParamVO.builder()
                                .name(param.name())
                                .paramName(param.paramName())
                                .type(param.type())
                                .description(param.description())
                                .sourceType(param.sourceType())
                                .build())
                        .collect(Collectors.toList()))
                .bindableOutputs(treeBindableOutputs(action));
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

    /**
     * 可被下游引用或回传黑板的出参。
     * <p>属性值、实例关系这两类基础行为没有返回体，出参既取不到也就无从引用或回传；
     * 基础函数同样不绑算子，但执行会返回查询结果，出参与其它行为一样可以选。</p>
     */
    private List<TreeActionParamVO> treeBindableOutputs(OntologyAction action) {
        if (action.getActionKind() == ActionKindEnum.BASE
                && action.getSubjectType() != ActionSubjectTypeEnum.BASIC_FUNCTION) {
            return new ArrayList<>();
        }
        try {
            return treeOutputRows(action).stream()
                    .map(row -> TreeActionParamVO.builder()
                            .name(treeOutputKey(row))
                            .fieldName(StringUtils.defaultIfBlank(row.parameterName(), row.functionParamName()))
                            .type(row.type())
                            .description(row.displayName())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // 单条行为定义坏了（如基础函数配置无法解析）不能让整个目录不可用，行为照常出现，只是暂时没有出参可选
            log.warn("行为树节点目录取行为出参失败, actionApi={}", action.getApi(), e);
            return new ArrayList<>();
        }
    }

    private List<TreeActionParamVO> toTreeActionParams(List<FunctionParameterVO> params) {
        if (CollectionUtils.isEmpty(params)) {
            return new ArrayList<>();
        }
        return params.stream()
                .map(param -> TreeActionParamVO.builder()
                        .name(param.getParamName())
                        .type(param.paramTypeEnum())
                        .description(param.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /** 单次执行/试跑的运行时：执行请求里的全套数据原样带进来，执行前的校验和真正执行共用这一份 */
    private TreeExecutionRuntime buildTreeRuntime(CompiledBehaviorTree compiled, BehaviorTreeDefinition definition,
                                                  TreeExecuteParam param) {
        return buildTreeRuntime(compiled, definition, param.getEntityPrimaryKey(),
                param.getEntityPrimaryKeys(), param.getInputs(), param.getDataPrimaryKey(),
                param.getInputDataPrimaryKeys(), param.getOutputDataPrimaryKeys(),
                // 树级默认值一次执行每个入参只给一个数据位置（LIST 按元素多位置只在节点级给），校验后原样交给运行时
                treeInputTargets(param.getInputParams()), param.getOutputParams(),
                param.getLinkEntityPrimaryKey(), param.getNodeExecutes());
    }

    /**
     * 批量执行的运行时：实例主键来自遍历，其余数据全实例共用；按节点传的数据只有外部入参可用，
     * 定位实例的字段已在执行前拒绝。
     */
    private TreeExecutionRuntime buildTreeRuntime(CompiledBehaviorTree compiled, BehaviorTreeDefinition definition,
                                                  Object entityPrimaryKey, TreeBatchExecuteParam param) {
        return buildTreeRuntime(compiled, definition, entityPrimaryKey, null, param.getInputs(),
                param.getDataPrimaryKey(), null, null, null, null, param.getLinkEntityPrimaryKey(),
                param.getNodeExecutes());
    }

    private TreeExecutionRuntime buildTreeRuntime(CompiledBehaviorTree compiled, BehaviorTreeDefinition definition,
                                                  Object entityPrimaryKey, Map<String, Object> entityPrimaryKeys,
                                                  Map<String, Object> inputs, Object dataPrimaryKey,
                                                  Map<String, Map<String, Object>> inputDataPrimaryKeys,
                                                  Map<String, Map<String, Object>> outputDataPrimaryKeys,
                                                  Map<String, List<ActionInstanceTargetParam>> inputParams,
                                                  Map<String, ActionInstanceTargetParam> outputParams,
                                                  Object linkEntityPrimaryKey,
                                                  Map<String, TreeNodeExecuteParam> nodeExecutes) {
        return new TreeExecutionRuntime(definition, compiled.api(), entityPrimaryKey, entityPrimaryKeys, inputs,
                dataPrimaryKey, inputDataPrimaryKeys, outputDataPrimaryKeys, inputParams, outputParams,
                linkEntityPrimaryKey, nodeExecutes, compiled.spaceId(),
                this::tryRunTreeCooldown, this::refundTreeCooldown, this::runTreeBranches, this::invokeTreeNodeAction,
                this::refreshTreeBlackboardProperties);
    }

    /** 跑一轮树并还原结果树。每条实例一个运行时与黑板，编译结果可复用。 */
    private TreeExecuteResultVO runTreeInstance(CompiledBehaviorTree compiled, TreeExecutionRuntime runtime) {
        compiled.tree().tick(runtime, runtime.getBehaviorBlackboard());
        return buildTreeResult(compiled, runtime);
    }

    /**
     * 并行节点的分支统一走专用线程池，同时真正执行的分支数不超过配置值。
     * <p>只把 maxConcurrency 个分支同时提交进池，完成一个再提交下一个：池里的任务始终是正在跑的分支，
     * 不会出现线程拿到任务后阻塞等许可、白占线程，配置并发数小于分支数时也不会退回调用线程串行执行。
     * 外层限时还在时，等待不超过截止时间；到点取消没跑完的分支并按失败返回，避免请求无限挂住。</p>
     */
    private TreeBranchRunner.BranchRun runTreeBranches(List<Supplier<BehaviorStateEnum>> branches, int maxConcurrency,
                                                       long deadlineMillis) throws InterruptedException {
        if (branches.isEmpty()) {
            // 调用方已拦空分支，这里兜住接口本身：并发数至少为 1 会越界取第 0 个分支
            return new TreeBranchRunner.BranchRun(new ArrayList<>(), false);
        }
        var states = new ArrayList<BehaviorStateEnum>(Collections.nCopies(branches.size(), BehaviorStateEnum.FAILURE));
        var concurrency = Math.max(1, Math.min(maxConcurrency, branches.size()));
        var running = new ArrayDeque<BranchTask>();
        var next = 0;
        try {
            while (next < concurrency) {
                running.add(submitBranch(branches, next++));
            }
            while (!running.isEmpty()) {
                var task = running.poll();
                BehaviorStateEnum state;
                try {
                    state = deadlineMillis <= 0 ? task.future().get()
                            : task.future().get(Math.max(0L, deadlineMillis - System.currentTimeMillis()),
                            TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    log.warn("行为树并行分支等待超过限时，未完成的分支按失败计, branchCount={}", branches.size());
                    task.future().cancel(true);
                    running.forEach(pending -> pending.future().cancel(true));
                    // 结果里标出超时：未完成的分支写操作可能还在后台执行，不能按普通失败理解
                    return new TreeBranchRunner.BranchRun(states, true);
                } catch (ExecutionException e) {
                    // 分支自己兜异常，这里只可能是线程被中断等极端情况
                    log.warn("行为树并行分支执行异常", e.getCause());
                    state = BehaviorStateEnum.FAILURE;
                }
                states.set(task.index(), state);
                if (next < branches.size()) {
                    running.add(submitBranch(branches, next++));
                }
            }
            return new TreeBranchRunner.BranchRun(states, false);
        } catch (InterruptedException e) {
            running.forEach(pending -> pending.future().cancel(true));
            throw e;
        }
    }

    private BranchTask submitBranch(List<Supplier<BehaviorStateEnum>> branches, int index) {
        return new BranchTask(index, behaviorTreeBranchExecutor.submit(() -> branches.get(index).get()));
    }

    /** 并行分支下标与句柄：结果要按下标回填，等待完成后再提交下一个分支 */
    private record BranchTask(int index, Future<BehaviorStateEnum> future) {
    }

    /**
     * 冷却记录定位用的实例主键：为空落空串（整棵树共享），超长时不能裸截断——
     * 两个共享前 255 字符的长主键会撞成同一条记录互相顶掉冷却，改成「前缀 + 全值哈希」保证不同值不同键。
     * <p>占位与退还必须走本方法，算出完全相同的 scopeKey 才能删到同一条。</p>
     */
    private String treeCooldownScopeKey(Object entityPrimaryKey) {
        if (entityPrimaryKey == null) {
            return "";
        }
        var value = String.valueOf(entityPrimaryKey);
        if (value.length() <= TREE_COOLDOWN_SCOPE_MAX_LENGTH) {
            return value;
        }
        // 预留 17 字符给 "#"+16位哈希，前缀取剩余长度，总长正好 255 不溢出列宽
        var hash = sha256Hex(value).substring(0, 16);
        return value.substring(0, TREE_COOLDOWN_SCOPE_MAX_LENGTH - hash.length() - 1) + "#" + hash;
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 是 JDK 必备算法，取不到属于环境异常；退化为原值哈希兜底仍保证不同值大概率不同键
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * 冷却占位：同一棵树、同一节点、同一执行实例只保留最近一次执行时间。
     * upsert 自带间隔判断，并发执行时不会出现两次都读到旧时间后同时放行。
     * <p>冷却状态表缺失时按「不可用」返回，由冷却节点放行并在结果里提示；
     * 不把辅助表的缺失升级成整棵树执行失败。</p>
     */
    private TreeCooldownTracker.CooldownDecision tryRunTreeCooldown(Integer spaceId, String treeApi, String nodeId,
                                                                    Object entityPrimaryKey, int intervalSeconds) {
        var scopeKey = treeCooldownScopeKey(entityPrimaryKey);
        try {
            return behaviorTreeCooldownMapper.tryAcquire(spaceId, treeApi, nodeId, scopeKey, intervalSeconds) > 0
                    ? TreeCooldownTracker.CooldownDecision.acquire()
                    : TreeCooldownTracker.CooldownDecision.inCooldown();
        } catch (DataAccessException e) {
            warnCooldownTableUnavailable(e);
            return TreeCooldownTracker.CooldownDecision.storeUnavailable();
        }
    }

    /**
     * 退还冷却占位：子节点本轮被跳过时删掉刚占的记录，行为没真实执行不该占用冷却窗口。
     * <p>scopeKey 用与 {@link #tryRunTreeCooldown} 完全相同的规则算出，删的是同一条。
     * 冷却表不可用时跳过删除（同占位：不让辅助表把主流程事务打断）——占位那次本就没写成功。</p>
     */
    private void refundTreeCooldown(Integer spaceId, String treeApi, String nodeId, Object entityPrimaryKey) {
        if (!cooldownTableReady()) {
            return;
        }
        var scopeKey = treeCooldownScopeKey(entityPrimaryKey);
        try {
            behaviorTreeCooldownMapper.refund(spaceId, treeApi, nodeId, scopeKey);
        } catch (DataAccessException e) {
            warnCooldownTableUnavailable(e);
        }
    }

    /**
     * 冷却表是否存在：存在才做清理。直接在事务里执行一条会失败的 DELETE 会让 PostgreSQL 事务进入中止状态，
     * 后面的提交会连带回滚，所以要先探测再用。
     */
    private boolean cooldownTableReady() {
        try {
            return Boolean.TRUE.equals(behaviorTreeCooldownMapper.existsTable());
        } catch (DataAccessException e) {
            warnCooldownTableUnavailable(e);
            return false;
        }
    }

    private void warnCooldownTableUnavailable(Exception e) {
        if (behaviorTreeCooldownWarned.compareAndSet(false, true)) {
            log.warn("行为树冷却状态表不可用，冷却节点将跳过冷却检查；请在目标库执行 "
                    + "ddl/alter_table_20261106_behavior_tree_cooldown.sql", e);
        }
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
                // trace 存在但没落终态：节点进来过却没跑完（并行超时后仍在后台的孤儿分支被并发读到），
                // 正常路径每个节点都会写完 SUCCESS/FAILURE/跳过，留空的按未完成失败处理，不能替它谎报成功；
                // 单轮执行也不会停在 RUNNING，所以兜底取失败而非 RUNNING
                .state(trace.getState() == null ? BehaviorStateEnum.FAILURE : trace.getState())
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

    /** 行为树单次执行写进执行日志的说明：成功记一句话，失败取第一个有原因的失败节点 */
    private String treeResultMessage(TreeExecuteResultVO result) {
        if (result.getState() == BehaviorStateEnum.SUCCESS) {
            return "行为树执行成功：" + result.getTreeApi();
        }
        var failureMessage = firstFailureMessage(result.getNodes());
        return StringUtils.isBlank(failureMessage) ? "行为树执行失败：" + result.getTreeApi() : failureMessage;
    }

    /** 行为树批量执行写进执行日志的说明：一次请求只留实例数与成功失败计数 */
    private String treeBatchResultMessage(TreeBatchExecuteResultVO result) {
        return "共 " + result.getTotal() + " 条实例，成功 " + result.getSuccessCount()
                + " / 失败 " + result.getFailCount();
    }

    /**
     * 行为节点：节点决定绑哪个行为、重试次数，以及每个入参从哪取值、出参要不要回传黑板。
     * <p>没配取值来源的入参沿用执行请求按节点给的值；配了却取不到值时节点直接失败，不让行为拿残缺的参数执行。
     * 执行成功后把返回体留在本次运行时里，供下游节点按「节点 + 出参」引用；节点失败或没有返回体时清掉，
     * 循环里下游拿到的就是最后一轮的值。</p>
     * <p>节点配了执行成功条件时，行为调用没报错还要条件成立才算成功；不成立按失败处理，
     * 这次的出参既不回传黑板也不留给下游引用。</p>
     */
    private TreeActionInvoker.TreeActionOutcome invokeTreeNodeAction(String nodeId, Integer spaceId,
                                                                     BehaviorActionConfig config,
                                                                     TreeExecutionRuntime runtime) {
        if (config == null || StringUtils.isBlank(config.getActionApi())) {
            runtime.clearNodeOutput(nodeId);
            return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.FAILURE,
                    "行为节点缺少行为配置，没有可执行的行为");
        }
        var boundApi = config.getActionApi();
        var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, boundApi)
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        if (bound == null || bound.getActionKind() == ActionKindEnum.TREE
                || !Objects.equals(bound.getStatus(), ActionStatusEnum.PUBLISHED.getValue())) {
            runtime.clearNodeOutput(nodeId);
            return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.FAILURE,
                    "绑定的行为不可用（不存在、未发布或是树）：" + boundApi);
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        var problem = resolveTreeNodeInputs(nodeId, spaceId, config, runtime, overrides);
        if (problem != null) {
            runtime.clearNodeOutput(nodeId);
            return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.FAILURE, problem);
        }
        var entityPrimaryKeys = runtime.nodeEntityPrimaryKeys(nodeId);
        // 成功条件表达式要读本次真正传给算子的入参（#inputs）：执行侧把解析后的入参回填到这个容器里
        var resolvedInputs = new LinkedHashMap<String, Object>();
        var testParam = ActionInvokeParam.builder()
                .ontologySpaceId(spaceId)
                .actionApi(boundApi)
                // 应用行为没有所属对象，单靠 entityPrimaryKey 定位不到实例，按对象把主键传下去
                .entityPrimaryKey(runtime.nodeEntityPrimaryKey(nodeId, ownTreeObject(bound)))
                .entityPrimaryKeys(entityPrimaryKeys.isEmpty() ? null : entityPrimaryKeys)
                .dataPrimaryKey(runtime.nodeDataPrimaryKey(nodeId))
                .inputEntities(toInstanceTargets(runtime.nodeInputDataPrimaryKeys(nodeId)))
                .outputEntities(toInstanceTargets(runtime.nodeOutputDataPrimaryKeys(nodeId)))
                .inputParams(toTestInputPositions(runtime.nodeInputParams(nodeId)))
                .outputParams(runtime.nodeOutputParams(nodeId))
                .linkEntityPrimaryKey(treeLinkEntityPrimaryKey(nodeId, bound, runtime))
                .params(treeExternalParams(nodeId, bound, runtime))
                .paramValueOverrides(overrides.isEmpty() ? null : overrides)
                .resolvedInputs(resolvedInputs)
                .build();
        // 节点面板按绑定行/元素字段名给的数据位置归到顶层入参名，LIST 的元素字段位置按数组下标拼成同一元素；
        // 按参数名给的写法不受影响
        normalizeInputParamKeys(testParam);
        // 树节点的数据位置都来自执行请求，走内部入口，别绕回对外请求再剥掉一次
        var testResult = runTestAction(testParam);
        // 有关系的执行按对端逐条 try/catch，异常只回一个空串；节点不能把这种结果当成功
        var counterparts = countCounterpartOutcome(resultText(testResult.getResult()));
        if (counterparts != null && counterparts.failed > 0) {
            runtime.clearNodeOutput(nodeId);
            return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.FAILURE,
                    "对端执行失败：" + counterparts.failed + "/" + counterparts.total + " 条结果为空");
        }
        // 出参回传与下游引用按算子返回体形状取值，先把执行结果那层字符串数组外壳剥掉
        var resultJson = unwrapActionResultForRead(resultText(testResult.getResult()));
        // 先判成功条件：不成立这次结果不可信，出参不回传黑板、节点产出也不留给下游
        var conditionProblem = checkNodeSuccessCondition(nodeId, config, resultJson, resolvedInputs, runtime);
        if (conditionProblem != null) {
            runtime.clearNodeOutput(nodeId);
            return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.FAILURE, conditionProblem);
        }
        var writeBackProblem = writeTreeOutputsToBlackboard(nodeId, bound, config, resultJson, runtime);
        if (writeBackProblem != null) {
            runtime.clearNodeOutput(nodeId);
            return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.FAILURE, writeBackProblem);
        }
        if (StringUtils.isNotBlank(resultJson)) {
            runtime.setNodeOutput(nodeId, boundApi, resultJson);
        } else {
            runtime.clearNodeOutput(nodeId);
        }
        // 失败重试由行为节点自己控制，这里只回单次执行结果
        return new TreeActionInvoker.TreeActionOutcome(BehaviorStateEnum.SUCCESS, testResult.getMessage());
    }

    /**
     * 解析节点上配置的入参取值来源，取到的值写进 {@code overrides}。
     * <p>一个入参一条：黑板变量按当前值取（有键的来源是对象属性时先整体回读一次库里的值），
     * 上游出参从那次执行的返回体按出参绑定行的取值路径取，不回库——出参可能本来就不写实例，
     * 也可能写的是另一个对象的实例。任何一条取不到就整节点不执行，避免行为拿着半套参数跑出误导结果。</p>
     * <p>LIST 入参取上游出参什么形状都收，见 {@link #resolveActionOutputInput}。</p>
     *
     * @return 取不到值的原因，全部取到时返回 null
     */
    private String resolveTreeNodeInputs(String nodeId, Integer spaceId, BehaviorActionConfig config,
                                         TreeExecutionRuntime runtime, Map<String, Object> overrides) {
        var bindings = MapUtils.emptyIfNull(config.getInputBindings());
        if (bindings.isEmpty()) {
            return null;
        }
        if (hasTreeBlackboardPropertyInput(bindings, runtime)) {
            runtime.refreshBlackboard(nodeId);
        }
        var rowsByActionApi = new HashMap<String, List<TreeOutputRow>>();
        for (var entry : bindings.entrySet()) {
            var paramName = entry.getKey();
            var binding = entry.getValue();
            if (binding == null) {
                continue;
            }
            var problem = StringUtils.isNotBlank(binding.getBlackboardKey())
                    ? resolveBlackboardInput(nodeId, paramName, binding.getBlackboardKey().trim(), runtime, overrides)
                    : resolveActionOutputInput(nodeId, spaceId, paramName, binding, runtime, overrides, rowsByActionApi);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    /** 这份绑定里有没有要取对象属性来源黑板键的：有就得先按实例把库里的当前值刷进来 */
    private boolean hasTreeBlackboardPropertyInput(Map<String, BehaviorInputBinding> bindings,
                                                   TreeExecutionRuntime runtime) {
        var items = runtime.getBlackboardItems();
        return bindings.values().stream()
                .filter(binding -> binding != null && StringUtils.isNotBlank(binding.getBlackboardKey()))
                .map(binding -> items.get(binding.getBlackboardKey().trim()))
                .anyMatch(item -> item != null && item.resolvedSource() == BehaviorBlackboardSourceEnum.PROPERTY);
    }

    /** 取黑板键的当前值：键不在本树声明里、或还没有值，都返回原因 */
    private String resolveBlackboardInput(String nodeId, String paramName, String key,
                                          TreeExecutionRuntime runtime, Map<String, Object> overrides) {
        var label = "行为节点「" + runtime.nodeLabel(nodeId) + "」的入参 " + paramName + " ";
        if (runtime.getBlackboardItems().get(key) == null) {
            return label + "要取的黑板键 " + key + " 不在本树声明里，行为不执行";
        }
        var value = runtime.blackboardValue(key);
        if (value == null) {
            return label + "要取的黑板键 " + key + " 还没有值（外部输入没传、上游也没回传），行为不执行";
        }
        overrides.put(paramName, value);
        return null;
    }

    /**
     * 取上游行为节点的出参：按「节点 + 出参」从那轮返回体取值，取不到就返回原因。
     * <p>通配出参行展开的是一批值，整批放进覆盖值；路径指向数组的行整包给；单值行给标量。
     * 覆盖值按入参声明类型收口在执行侧完成：LIST 入参把单值当只有一个元素的集合、整批原样收下，
     * 单值入参展沿用取批里第一条非空。</p>
     */
    private String resolveActionOutputInput(String nodeId, Integer spaceId, String paramName,
                                            BehaviorInputBinding binding, TreeExecutionRuntime runtime,
                                            Map<String, Object> overrides,
                                            Map<String, List<TreeOutputRow>> rowsByActionApi) {
        var fromNodeId = binding.getNodeId();
        var upstream = runtime.nodeOutput(fromNodeId);
        var label = "行为节点「" + runtime.nodeLabel(nodeId) + "」的入参 " + paramName + " ";
        var fromLabel = "行为节点「" + runtime.nodeLabel(fromNodeId) + "」";
        if (upstream == null || StringUtils.isBlank(upstream.resultJson())) {
            return label + "要引用" + fromLabel + "的出参，但它这轮没有可用产出（没执行、执行失败或没有返回体），行为不执行";
        }
        var rows = rowsByActionApi.computeIfAbsent(upstream.actionApi(),
                api -> treeOutputRows(requireTreeActionByApi(api, spaceId)));
        var matched = matchTreeOutputRows(rows, StringUtils.trimToEmpty(binding.getOutput()));
        if (matched.size() != 1) {
            return label + "引用的出参 " + StringUtils.defaultIfBlank(binding.getOutput(), "（未选择）")
                    + " 在" + fromLabel + "的行为上认不出"
                    + "（要命中唯一一条，可用：属性唯一标识 > 出参字段名 > 顶层算子出参名），行为不执行";
        }
        var row = matched.get(0);
        // 通配出参行按 [*] 展开成整批列：整批交给执行侧，不能在这里退化成批里第一条
        if (!row.exactPath() && FunctionOutputValueReader.hasWildcard(row.expression())) {
            var values = FunctionOutputValueReader.readMany(upstream.resultJson(), row.expression(),
                    row.functionParamName());
            if (CollectionUtils.isEmpty(values) || values.stream().allMatch(Objects::isNull)) {
                return label + "从" + fromLabel + "的出参「" + row.displayName() + "」取不到值，行为不执行";
            }
            overrides.put(paramName, values);
            return null;
        }
        var value = readTreeOutputValue(upstream.resultJson(), row);
        if (value == null) {
            return label + "从" + fromLabel + "的出参「" + row.displayName() + "」取不到值，行为不执行";
        }
        overrides.put(paramName, value);
        return null;
    }

    /**
     * 把节点配置的出参回传写进黑板。
     * <p>取值口径与出参写回实例同一条路径；取不到值时节点失败，不留一个空值让后面的条件误判。</p>
     *
     * @return 回传失败的原因，全部写进去时返回 null
     */
    private String writeTreeOutputsToBlackboard(String nodeId, OntologyAction bound, BehaviorActionConfig config,
                                                String resultJson, TreeExecutionRuntime runtime) {
        var returns = MapUtils.emptyIfNull(config.getOutputBindings());
        if (returns.isEmpty()) {
            return null;
        }
        var label = "行为节点「" + runtime.nodeLabel(nodeId) + "」";
        if (StringUtils.isBlank(resultJson)) {
            return label + "的行为没有返回体，出参无法回传黑板，请去掉这些出参的回传配置";
        }
        var rows = treeOutputRows(bound);
        // 先把每条出参的取值全部算完并校验，暂存到 pending；任一条认不出或取不到就直接返回失败，一条都不写黑板。
        // 逐条写会出现「前几条已落黑板、后几条失败」的半套回传，下游条件会拿这份残缺值误判成执行
        Map<String, Object> pending = new LinkedHashMap<>();
        for (var entry : returns.entrySet()) {
            var outputKey = StringUtils.trimToEmpty(entry.getKey());
            var blackboardKey = entry.getValue();
            var matched = matchTreeOutputRows(rows, outputKey);
            if (matched.size() != 1) {
                return label + "要回传的出参 " + StringUtils.defaultIfBlank(outputKey, "（未填写）")
                        + " 在该行为上认不出（要命中唯一一条，可用：属性唯一标识 > 出参字段名 > 顶层算子出参名）";
            }
            var row = matched.get(0);
            // LIST 黑板键：通配行按 [*] 展开成整批列回传，单值行按只有一个元素的集合回传
            var targetItem = StringUtils.isBlank(blackboardKey) ? null : runtime.getBlackboardItems().get(blackboardKey);
            var listTarget = targetItem != null && targetItem.getValueType() == FunctionParamTypeEnum.LIST;
            if (listTarget && !row.exactPath() && FunctionOutputValueReader.hasWildcard(row.expression())) {
                var values = FunctionOutputValueReader.readMany(resultJson, row.expression(), row.functionParamName());
                if (CollectionUtils.isEmpty(values) || values.stream().allMatch(Objects::isNull)) {
                    return label + "的出参「" + row.displayName() + "」整批取不到值，"
                            + "不回传黑板键 " + blackboardKey;
                }
                pending.put(blackboardKey, values);
                continue;
            }
            var value = readTreeOutputValue(resultJson, row);
            if (value == null) {
                return label + "的出参「" + row.displayName() + "」在本次返回里取不到值，"
                        + "不回传黑板键 " + blackboardKey;
            }
            pending.put(blackboardKey, listTarget && !(value instanceof List)
                    ? Lists.newArrayList(value) : value);
        }
        // 全部就绪才一次性提交，保证回传要么整套生效、要么一套都不写
        pending.forEach(runtime::setBlackboardValue);
        return null;
    }

    /**
     * 行为节点的成功条件判定：没配就算过（行为调用没报错即成功）；配了就在只读上下文里对本次执行求值，
     * 必须返回 true/false。作用域里的值全部来自本次执行，不掺黑板也不回库：
     * <ul>
     *   <li>{@code #result}：本次返回体（完整外壳，解析成 Map/List；不是 JSON 时是原文）；</li>
     *   <li>{@code #data}：返回体里的业务数据层（有 data 字段时取它，否则同 #result）；</li>
     *   <li>{@code #inputs}：本次真正传给算子的入参，顶层入参名 -> 值，LIST 入参可按下标取某一项；</li>
     * </ul>
     * false 算条件不成立，求值异常、取不到字段、结果不是布尔都算判不了，同样按失败处理——
     * 不能因为判不了就默默放过，用户配条件就是为了拦住这一轮结果不可信的情况。
     *
     * @param resolvedInputs 本次真正传给算子的入参，按 #inputs 读取；不收集时为 null
     * @return 不成立或判不了时的说明；成立返回 null
     */
    private String checkNodeSuccessCondition(String nodeId, BehaviorActionConfig config, String resultJson,
                                             Map<String, Object> resolvedInputs, TreeExecutionRuntime runtime) {
        var condition = config.getSuccessCondition();
        var expression = condition == null ? null : StringUtils.trimToNull(condition.getExpression());
        if (expression == null) {
            return null;
        }
        var label = "行为节点「" + runtime.nodeLabel(nodeId) + "」";
        Object parsed = null;
        if (StringUtils.isNotBlank(resultJson)) {
            try {
                parsed = jsonMapper.readValue(resultJson, Object.class);
            } catch (Exception e) {
                // 属性值/实例关系这类行为只回一句提示文本，表达式按原文还能判空或判前缀
                parsed = resultJson;
            }
        }
        var variables = new LinkedHashMap<String, Object>();
        // 空值不放变量：SpEL 把未定义的变量和空值都当取不到，表达式会按「判不了」失败，语义一致
        if (parsed != null) {
            variables.put("result", parsed);
            var data = parsed instanceof Map<?, ?> map && map.containsKey("data") ? map.get("data") : parsed;
            if (data != null) {
                variables.put("data", data);
            }
        }
        variables.put("inputs", MapUtils.emptyIfNull(resolvedInputs));
        var hit = runtime.evaluateSuccessExpression(nodeId, expression, variables);
        if (hit == null) {
            return label + "的执行成功条件判不了：" + expression
                    + "（可用变量 #result 本次返回体、#data 业务数据、#inputs 本次入参）";
        }
        return hit ? null : label + "的执行成功条件不成立：" + expression;
    }

    /** 按 api 取本空间的行为：执行期引用上游产出时用它找声明的出参行，取不到就是定义变了 */
    private OntologyAction requireTreeActionByApi(String actionApi, Integer spaceId) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        PreconditionUtils.checkNotNull(action, "行为不存在：" + actionApi, HttpStatus.BAD_REQUEST);
        return action;
    }

    /**
     * 关系对端主键：执行请求给了就用它，否则按对端对象选的实例。
     * <p>自关系的对端就是本对象，按对象取的实例会被当成两端同一条，不能用来推导。</p>
     */
    private Object treeLinkEntityPrimaryKey(String nodeId, OntologyAction bound, TreeExecutionRuntime runtime) {
        var linkEntityPrimaryKey = runtime.nodeLinkEntityPrimaryKey(nodeId);
        if (linkEntityPrimaryKey != null) {
            return linkEntityPrimaryKey;
        }
        var counterpart = treeActionCounterpartObject(bound);
        if (StringUtils.equals(counterpart, ownTreeObject(bound))) {
            return null;
        }
        return runtime.nodeMappedEntityPrimaryKey(nodeId, counterpart);
    }

    /**
     * 行为树只配到「分组行」一级，实例仍由共用的 entityPrimaryKeys 定位；
     * 单个行为/执行的入参出参数据位置是一个对象一项，这里把行主键包进去。
     */
    private Map<String, ActionInstanceTargetParam> toInstanceTargets(Map<String, Map<String, Object>> rowKeysByObject) {
        if (MapUtils.isEmpty(rowKeysByObject)) {
            return null;
        }
        Map<String, ActionInstanceTargetParam> targets = new LinkedHashMap<>();
        rowKeysByObject.forEach((objectId, byGroup) -> targets.put(objectId,
                ActionInstanceTargetParam.builder().dataPrimaryKeys(byGroup).build()));
        return targets.isEmpty() ? null : targets;
    }

    /** 复制一份可写的目标：执行请求里的默认值可能被多个节点共用，不能就地改 */
    private ActionInstanceTargetParam copyParamTarget(ActionInstanceTargetParam source) {
        return ActionInstanceTargetParam.builder()
                .ontologyIdentifier(source.getOntologyIdentifier())
                .entityPrimaryKey(source.getEntityPrimaryKey())
                .dataPrimaryKeys(source.getDataPrimaryKeys() == null ? null
                        : new LinkedHashMap<>(source.getDataPrimaryKeys()))
                .build();
    }

    /** 运行时按入参存的数据位置都是一维写法：LIST 是这次列表的元素、非 LIST 是唯一一个位置，包成测试请求的形状 */
    private static Map<String, ActionInputPositionsParam> toTestInputPositions(
            Map<String, List<ActionInstanceTargetParam>> targets) {
        if (MapUtils.isEmpty(targets)) {
            return null;
        }
        var positions = new LinkedHashMap<String, ActionInputPositionsParam>();
        targets.forEach((paramName, list) -> positions.put(paramName, list == null ? null
                : ActionInputPositionsParam.ofPositions(new ArrayList<>(list))));
        return positions;
    }

    /** 树级默认数据位置：一次执行每个入参只用数组里的唯一元素，多元素是按节点给的位置 */
    private Map<String, List<ActionInstanceTargetParam>> treeInputTargets(
            Map<String, List<ActionInstanceTargetParam>> targets) {
        if (MapUtils.isEmpty(targets)) {
            return null;
        }
        Map<String, List<ActionInstanceTargetParam>> checked = new LinkedHashMap<>();
        targets.forEach((paramName, positions) -> {
            if (positions == null) {
                return;
            }
            var label = "inputParams[" + paramName + "]";
            PreconditionUtils.checkArgument(positions.size() == 1,
                    label + " 是树级默认值，一次执行只给一个数据位置；LIST 入参要按多个元素取值请改在 nodeExecutes[节点].inputParams 里给",
                    HttpStatus.BAD_REQUEST);
            checkInputTargetElement(label, positions.get(0));
            checked.put(paramName, new ArrayList<>(List.of(copyParamTarget(positions.get(0)))));
        });
        return checked.isEmpty() ? null : checked;
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
     * 把行为执行结果剥成出参取值能认的返回体：执行链路把每次算子调用返回体拼成 JSON 字符串数组
     * （对端逐条一条一个，失败的是空串），而出参绑定行的取值路径按算子返回体形状书写，隔着这层数组外壳永远取不到。
     * <p>去掉空条目后只剩一条时直接返回那条返回体原文；多条时逐条解析后拼成对象数组，取值器认得进数组下钻。
     * 不是数组或解析不动就原样返回（如基础行为直接返回提示文本）。</p>
     */
    private String unwrapActionResultForRead(String result) {
        if (StringUtils.isBlank(result)) {
            return result;
        }
        try {
            var root = jsonMapper.readTree(result);
            if (root == null || !root.isArray() || root.isEmpty()) {
                return result;
            }
            var payloads = new ArrayList<String>();
            for (var item : root) {
                if (item.isNull() || (item.isTextual() && StringUtils.isBlank(item.asText()))) {
                    continue;
                }
                payloads.add(item.isTextual() ? item.asText() : jsonMapper.writeValueAsString(item));
            }
            if (payloads.isEmpty()) {
                return result;
            }
            if (payloads.size() == 1) {
                return payloads.get(0);
            }
            var array = jsonMapper.createArrayNode();
            for (var payload : payloads) {
                var parsed = jsonMapper.readTree(payload);
                if (parsed.isArray()) {
                    for (var element : parsed) {
                        array.add(element);
                    }
                } else {
                    array.add(parsed);
                }
            }
            return jsonMapper.writeValueAsString(array);
        } catch (Exception e) {
            return result;
        }
    }

    /**
     * 行为声明的外部输入参数按节点取值：每个节点只用自己 nodeExecutes[节点].params 里给的值，
     * 和详情接口列出的必填入参同一套定义。
     * <p>黑板不参与行为执行：它是条件用的全局变量，和执行请求里的行为入参是两套东西。</p>
     */
    private Map<String, Object> treeExternalParams(String nodeId, OntologyAction bound, TreeExecutionRuntime runtime) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (var param : treeExternalParamDefs(bound)) {
            var value = runtime.nodeParamValue(nodeId, param.name());
            if (value != null) {
                params.put(param.name(), value);
            }
        }
        return params;
    }

    /**
     * 条件求值前把来源是对象属性的黑板键按库里的当前值重读一遍。
     * <p>行为不写黑板，属性值只能通过这条路进黑板：绑同一个对象的键合并成一次回读，
     * 定位不到实例或行里没有这个属性时不动黑板值，把原因写进节点说明，
     * 否则条件会拿着上一次求值留下的值判成执行。实例与分组行取执行请求里全树共用的那份，
     * 节点级覆盖不影响黑板——黑板是树级变量，不同节点不能读到不同的行。</p>
     */
    private void refreshTreeBlackboardProperties(String nodeId, TreeExecutionRuntime runtime) {
        var boundItems = runtime.getBlackboardItems().values().stream()
                .filter(item -> item.resolvedSource() == BehaviorBlackboardSourceEnum.PROPERTY
                        && StringUtils.isNotBlank(item.getBindPropertyUniqueIdentifier()))
                .collect(Collectors.toList());
        if (boundItems.isEmpty()) {
            return;
        }
        var propertyIds = boundItems.stream()
                .map(BehaviorBlackboardItem::getBindPropertyUniqueIdentifier)
                .distinct()
                .collect(Collectors.toList());
        var objectByProperty = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .in(OntologyProperty::getUniqueIdentifier, propertyIds)).stream()
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier,
                        OntologyProperty::getOntologyUniqueIdentifier, (left, right) -> left));
        Map<String, Set<String>> propsByObject = new LinkedHashMap<>();
        List<String> unknownProps = new ArrayList<>();
        for (var item : boundItems) {
            var bind = item.getBindPropertyUniqueIdentifier();
            var objectId = objectByProperty.get(bind);
            if (StringUtils.isBlank(objectId)) {
                unknownProps.add(item.getKey());
                continue;
            }
            propsByObject.computeIfAbsent(objectId, ignored -> new LinkedHashSet<>()).add(bind);
        }
        // 属性已从对象上删掉的键不会再有值，报一次比让条件用默认值判成执行诚实
        if (!unknownProps.isEmpty()) {
            runtime.setNodeMessage(nodeId, "黑板键 " + String.join("、", unknownProps) + " 绑定的属性在本空间不存在");
        }
        Map<String, Object> latestValues = new LinkedHashMap<>();
        List<String> noInstanceObjects = new ArrayList<>();
        propsByObject.forEach((objectId, props) -> {
            // 黑板回读走树级口径（与分组行键 executionInputDataPrimaryKeys 同源）：节点级实例覆盖只影响行为执行，不让不同节点读到不同的行
            var entityPrimaryKey = runtime.executionEntityPrimaryKey(objectId);
            if (entityPrimaryKey == null) {
                noInstanceObjects.add(objectId);
                return;
            }
            latestValues.putAll(readTreeRowPropertyValues(objectId, entityPrimaryKey,
                    runtime.executionInputDataPrimaryKeys().get(objectId), runtime.executionDataPrimaryKey(), props));
        });
        if (!noInstanceObjects.isEmpty()) {
            runtime.appendNodeMessage(nodeId, "对象 " + String.join("、", noInstanceObjects)
                    + " 本次执行没给实例，绑定其属性的黑板键没有刷新");
        }
        runtime.getBlackboardItems().forEach((key, item) -> {
            var bind = item.getBindPropertyUniqueIdentifier();
            if (item.resolvedSource() == BehaviorBlackboardSourceEnum.PROPERTY
                    && StringUtils.isNotBlank(bind) && latestValues.containsKey(bind)) {
                runtime.setBlackboardValue(key, latestValues.get(bind));
            }
        });
    }

    /**
     * 回读一个对象某个实例行的属性值。
     * <p>扩展表一个实例下有多行，逐行覆盖会读到无关行的值：按分组给了行主键就只认那一行，
     * 没给就只认主表行（回读接口把主表分组放在第一位），扩展表宁可不读也不读错值。</p>
     */
    private Map<String, Object> readTreeRowPropertyValues(String objectId, Object entityPrimaryKey,
                                                         Map<String, Object> rowKeysByGroup, Object fallbackRowKey,
                                                         Set<String> boundProps) {
        Map<String, Object> latestValues = new LinkedHashMap<>();
        var detail = entityService.getEntityPropertyRowDetail(EntityPropertyRowQueryParam.builder()
                .ontologyUniqueIdentifier(objectId)
                .entityPrimaryKey(entityPrimaryKey)
                .sort(QuerySortEnum.ASC)
                .build());
        if (detail == null || CollectionUtils.isEmpty(detail.getPropertyGroups())) {
            return latestValues;
        }
        var groups = detail.getPropertyGroups();
        for (var i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            if (group.getGroupDataList() == null) {
                continue;
            }
            Object rowKey;
            if (i == 0) {
                rowKey = entityPrimaryKey;
            } else if (rowKeysByGroup != null) {
                rowKey = rowKeysByGroup.get(group.getStorageGroup());
            } else {
                rowKey = fallbackRowKey;
            }
            if (rowKey == null) {
                continue;
            }
            for (var row : group.getGroupDataList()) {
                if (row.getProps() == null || !sameTreeRowKey(row.getDataPrimaryKey(), rowKey)) {
                    continue;
                }
                row.getProps().stream()
                        .filter(prop -> boundProps.contains(prop.getPropertyUniqIdentifier()))
                        .forEach(prop -> latestValues.put(prop.getPropertyUniqIdentifier(), prop.getPropertyValue()));
            }
        }
        return latestValues;
    }

    /** 行主键可能一边是数字一边是字符串，按文本比；任一为空都不算匹配 */
    private boolean sameTreeRowKey(Object left, Object right) {
        return left != null && right != null && StringUtils.equals(String.valueOf(left), String.valueOf(right));
    }

    /**
     * 按对象类型取全部实例主键。行为树没有对象绑定，不能走 initActionContextInfoDTO（它会拒绝树），
     * 直接从对象主键属性定位实体主表。
     */
    /**
     * 批量执行的数据源信息：空间归属与主键属性只查一次，分页时不再重复校验。
     * <p>行为树只按 api 定位，对象标识来自请求体：先确认这个对象属于本次请求的空间，
     * 否则传一个别的空间的对象标识就能读到那个空间的数据。</p>
     */
    private TreeBatchInstanceSource treeBatchInstanceSource(Integer ontologySpaceId, String ontologyUniqueIdentifier) {
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier));
        PreconditionUtils.checkNotNull(meta, "无效的对象：" + ontologyUniqueIdentifier, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(meta.getOntologySpaceId(), ontologySpaceId),
                "对象 " + ontologyUniqueIdentifier + " 不属于当前对象空间，不能用于批量执行", HttpStatus.BAD_REQUEST);
        var primaryProperty = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                        .eq(OntologyProperty::getIsPrimaryKey, 1))
                .stream().findFirst().orElse(null);
        PreconditionUtils.checkNotNull(primaryProperty, "对象没有主键属性：" + ontologyUniqueIdentifier, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(primaryProperty.getDatasourceColumnName()),
                "对象主键未绑定数据源列：" + ontologyUniqueIdentifier, HttpStatus.BAD_REQUEST);
        var hasDeletedField = tableMetadataMapper.isColumnExist(primaryProperty.getDatasourceSchema(),
                primaryProperty.getDatasourceId(), "is_deleted");
        return new TreeBatchInstanceSource(primaryProperty.getDatasourceSchema(), primaryProperty.getDatasourceId(),
                primaryProperty.getDatasourceColumnName(), hasDeletedField);
    }

    /** 按主键升序取一页实例主键；lastKey 为上一页最后一个主键，空表示第一页 */
    private List<Object> pageTreeInstancePrimaryKeys(TreeBatchInstanceSource source, Object lastKey, int pageSize) {
        var records = entityMapper.queryPrimaryKeyValuePage(source.schemaName(), source.tableName(),
                source.primaryKeyColumnName(), lastKey, pageSize, source.hasDeletedField());
        if (records == null) {
            return List.of();
        }
        return records.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    /** 批量执行的数据源定位：副本表、主键列、是否存在逻辑删除列 */
    private record TreeBatchInstanceSource(String schemaName, String tableName, String primaryKeyColumnName,
                                           boolean hasDeletedField) {
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
     * <p>关联关系可选：应用行为与基础算子行为都不强制绑定，绑了才在测试/执行时做关系操作。</p>
     *
     * @param allowedTypes  该类别允许的算子类型
     * @param spaceId       行为所属空间，算子 api 仅在该空间内唯一
     * @param linkRequired  本次是否必须绑定一条对象关系
     */
    private void checkFunctionAction(ActionCreateOrUpdateParam param, ActionDefinition definition,
                                     List<FunctionTypeEnum> allowedTypes, Integer spaceId, boolean linkRequired) {
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
        var mappingIns = mergeWholeExternalBindings(param.getMappingIns(), definition.funcParams);
        if (CollectionUtils.isEmpty(mappingIns)) {
            return;
        }
        // List 入参按子表多行取值：元素字段必须同对象同分组，各绑定行的行数必须一致
        Map<Long, String> listElementGroups = new HashMap<>();
        Map<Long, String> listElementGroupLabels = new HashMap<>();
        Map<Long, Integer> listRowLimits = new HashMap<>();
        for (var mapping : mappingIns) {
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
            // 固定值和外部输入的取值不来自属性，这两类即使带着历史残留的属性标识也不参与类型比对
            boolean valueFromProperty = sourceType != ActionParamSourceEnum.FIXED
                    && sourceType != ActionParamSourceEnum.EXTERNAL;
            if (property != null && isInput && valueFromProperty) {
                var paramType = resolveInputParamType(funcParam, paramPath);
                var dataType = OntologyDataTypeEnum.valueOfDataType(paramType);
                // 对象/自定义类参数没有对应的属性数据类型，整包绑定时不做标量比对
                var compatible = dataType == null || dataType.equals(OntologyDataTypeEnum.Array)
                        || property.getPropertyType().equals(dataType)
                        || isInputTypeCompatible(paramType, property.getPropertyType());
                // 形参类型可以整层为 null（子字段没声明类型），放行时也要能走过去，所以报错文案只在真要报时才拼
                if (!compatible) {
                    throw new BusinessException("行为入参类型不兼容：算子入参[" + funcParam.getParamName() + "]是 "
                                    + paramType.getName() + "，属性[" + property.getDisplayName() + "]是 "
                                    + property.getPropertyType().getValue() + "，请改绑类型兼容的属性或调整算子形参类型",
                            HttpStatus.BAD_REQUEST);
                }
            }
            if (isInput) {
                checkListRowBinding(funcParam, mapping, property, paramPath, valueFromProperty,
                        listElementGroups, listElementGroupLabels, listRowLimits);
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
            // 挂载对象汇总：入参按来源属性所属对象，出参只认写回属性那些行；
            // 固定值/外部输入没有来源属性、仅返回的出参不落库，都不算动了某个对象
            var touchesProperty = property != null && (isInput ? valueFromProperty
                    : mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY);
            if (touchesProperty) {
                definition.objectIdentifiers.add(property.getOntologyUniqueIdentifier());
                definition.propertyIdentifiers.add(property.getUniqueIdentifier());
            }
            // List 来源不落到 property_unique_identifier 列，属性 id 都在 source_config 的数组里
            if (isInput && sourceType == ActionParamSourceEnum.LIST) {
                definition.propertyIdentifiers.addAll(listSourcePropertyIdentifiers(mapping.getSourceConfig()));
            }
            definition.mappings.add(OntologyActionMappingIn.builder()
                    .functionParamId(funcParam.getId())
                    .functionParamExpression(mapping.getFunctionParamExpression())
                    .parameterName(StringUtils.isEmpty(mapping.getParameterName()) ? funcParam.getParamName() : mapping.getParameterName())
                    .propertyUniqueIdentifier(mapping.getPropertyUniqueIdentifier())
                    .sourceType(sourceType)
                    .sourceConfig(mapping.getSourceConfig())
                    .paramPath(paramPath)
                    .rowLimit(mapping.getRowLimit())
                    .outputMode(funcParam.getCategory().equals(FunctionParamCategoryEnum.OUTPUT)
                            ? (mapping.getOutputMode() != null ? mapping.getOutputMode() : ActionOutputModeEnum.RESULT_ONLY)
                            : null)
                    .ontologyUniqueIdentifier(mapping.getOntologyUniqueIdentifier())
                    .build());
        }
    }

    /**
     * 对象/列表入参的来源全部是外部输入、又按子字段逐行绑定时，合并成一条整包绑定。
     * <p>行为测试的外部输入是「参数名 -&gt; 值」的平铺结构，逐字段绑定拿字段名当键：
     * 列表元素里的 latitude、longitude 各占一行，重名的字段（两条轨迹各有一列 latitude）
     * 会取到同一个值而串值。整包绑定后按算子入参名给一个值：对象给 JSON 对象、列表给数组。</p>
     * <p>只有「全部外部输入」才合并，部分字段来自属性、固定值等其他来源时保持原绑定形状不动。</p>
     */
    private List<ActionParamMappingCreateParam> mergeWholeExternalBindings(List<ActionParamMappingCreateParam> rows,
                                                                           List<FunctionParamPO> funcParams) {
        if (CollectionUtils.isEmpty(rows)) {
            return rows;
        }
        var funcParamById = funcParams.stream()
                .collect(Collectors.toMap(FunctionParamPO::getId, v -> v, (existingValue, newValue) -> existingValue));
        var rowsByParam = new LinkedHashMap<Long, List<ActionParamMappingCreateParam>>();
        for (var row : rows) {
            rowsByParam.computeIfAbsent(row.getFunctionParamId(), k -> new ArrayList<>()).add(row);
        }
        var merged = new ArrayList<ActionParamMappingCreateParam>(rows.size());
        for (var entry : rowsByParam.entrySet()) {
            var paramRows = entry.getValue();
            var funcParam = funcParamById.get(entry.getKey());
            var wholeExternal = paramRows.stream()
                    .allMatch(row -> row.getSourceType() == ActionParamSourceEnum.EXTERNAL);
            var boundByField = paramRows.stream().anyMatch(row -> StringUtils.isNotEmpty(row.getParamPath()));
            // 标量参数没有子字段，逐字段绑定本身就不成立，不在这里处理
            var objectLike = funcParam != null && funcParam.getCategory() == FunctionParamCategoryEnum.INPUT
                    && (funcParam.getParamType() == FunctionParamTypeEnum.OBJECT
                    || funcParam.getParamType() == FunctionParamTypeEnum.LIST);
            if (!(wholeExternal && boundByField && objectLike)) {
                merged.addAll(paramRows);
                continue;
            }
            var first = paramRows.get(0);
            merged.add(ActionParamMappingCreateParam.builder()
                    .functionParamId(first.getFunctionParamId())
                    .functionParamExpression(first.getFunctionParamExpression())
                    .parameterName(funcParam.getParamName())
                    .sourceType(ActionParamSourceEnum.EXTERNAL)
                    .build());
        }
        return merged;
    }

    /**
     * List 入参按子表多行取值的两条约束：
     * <ul>
     *   <li>元素字段（带 param_path 的行）必须同属一个对象、一个存储分组：不同表没有共同行序，
     *       按序号拼起来会把不同行的数据拼进同一个元素；</li>
     *   <li>行数只对 List 入参的对象属性绑定生效，同一个 List 入参的各行必须一致，
     *       否则一列读 10 行、另一列读 100 行只能拼出错位数据。</li>
     * </ul>
     */
    private void checkListRowBinding(FunctionParamPO funcParam, ActionParamMappingCreateParam mapping,
                                     OntologyProperty property, String paramPath, boolean valueFromProperty,
                                     Map<Long, String> listElementGroups, Map<Long, String> listElementGroupLabels,
                                     Map<Long, Integer> listRowLimits) {
        var paramName = funcParam.getParamName();
        var paramTypeName = funcParam.getParamType() == null ? "OBJECT" : funcParam.getParamType().getName();
        boolean isList = funcParam.getParamType() == FunctionParamTypeEnum.LIST;
        if (mapping.getRowLimit() != null) {
            PreconditionUtils.checkArgument(mapping.getRowLimit() > 0,
                    "行数必须大于 0：算子入参[" + paramName + "]填的是 " + mapping.getRowLimit(), HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(isList && valueFromProperty,
                    "行数只对 List 入参的对象属性绑定生效：算子入参[" + paramName + "]是 " + paramTypeName
                            + "，来源是 " + (valueFromProperty ? "非对象属性" : "固定值/外部输入"),
                    HttpStatus.BAD_REQUEST);
            var previous = listRowLimits.putIfAbsent(funcParam.getId(), mapping.getRowLimit());
            PreconditionUtils.checkArgument(previous == null || previous.equals(mapping.getRowLimit()),
                    "同一个 List 入参的绑定行行数必须一致：算子入参[" + paramName + "]已有行数是 "
                            + previous + "，当前行是 " + mapping.getRowLimit(), HttpStatus.BAD_REQUEST);
        }
        // 整包绑定（没有子字段路径）不参与分组比对：它取的是整列值，不存在按行对齐的问题
        if (!isList || StringUtils.isEmpty(paramPath) || property == null || !valueFromProperty) {
            return;
        }
        var objectId = StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())
                ? mapping.getOntologyUniqueIdentifier() : property.getOntologyUniqueIdentifier();
        var group = StringUtils.defaultIfBlank(property.getStorageGroup(), MAIN_STORAGE_GROUP);
        var currentKey = objectId + "|" + group;
        var currentLabel = "属性[" + property.getDisplayName() + "]（分组 " + group + "）";
        var previousKey = listElementGroups.putIfAbsent(funcParam.getId(), currentKey);
        if (previousKey == null) {
            listElementGroupLabels.put(funcParam.getId(), currentLabel);
            return;
        }
        PreconditionUtils.checkArgument(previousKey.equals(currentKey),
                "同一个 List 入参的元素字段必须绑定同一对象同一存储分组的属性：算子入参[" + paramName + "]已有 "
                        + listElementGroupLabels.get(funcParam.getId()) + "，当前行是 " + currentLabel
                        + "。不同对象或不同存储分组没有共同行序，按行拼对象会串行，请改绑同一分组的属性",
                HttpStatus.BAD_REQUEST);
    }

    /** 子字段路径归一：去掉前端可能带上的参数名前缀，空白按「未绑定子字段」处理 */
    private String normalizeParamPath(FunctionParamPO funcParam, String paramPath) {
        if (StringUtils.isBlank(paramPath)) {
            return null;
        }
        return StringUtils.removeStart(paramPath.trim(), funcParam.getParamName() + ".");
    }

    /**
     * 出参是数组（LIST）且写回目标是子表时，给取值路径补上 {@code [*]}。
     * <p>数组有两种来源：顶层出参本身声明成 LIST（参数树点出来的 {@code $.result.longitude}，{@code result}
     * 下面是元素字段），以及顶层出参是对象、路径上某个嵌套字段声明成 LIST（脚本把集合包在返回对象里的
     * {@code $.data.tracks.longitude}）。两种都按单值取只会拿到第一个元素，返回 12 条就静默只落 1 行，
     * 从落库数据上看不出是被截断还是本来只有 1 条。这里按参数声明的形状补成 {@code [*]} 的写法，
     * 执行侧才走逐元素插行。</p>
     * <p>不动的几种：主表只有实例那一行，取第一个元素就是它的值，补了反而会被「通配只能写子表」拒掉；
     * 已经写了 {@code [*]} 或写了 {@code [0]} 这类下标的按用户原意保留；路径停在数组层本身
     * （{@code $.data.tracks} 整包给 Array 属性）没有逐元素可言；数组后面接的不是该数组声明的元素字段时
     * 认不出数组层，宁可不补也不猜路径。</p>
     */
    private String wildcardListExpression(String expression, FunctionParamPO funcParam, OntologyProperty property) {
        if (StringUtils.isBlank(expression) || FunctionOutputValueReader.hasWildcard(expression)
                || !writesToSubtable(property)) {
            return expression;
        }
        var fields = parseParamFields(funcParam);
        if (funcParam.getParamType() == FunctionParamTypeEnum.LIST) {
            return wildcardAfterParamName(expression, funcParam, fields);
        }
        var token = listFieldToken(expression, fields);
        return token == null ? expression
                : expression.substring(0, token.end()) + "[*]" + expression.substring(token.end());
    }

    /** 顶层出参就是 LIST：元素字段直接挂在出参名下，把 {@code [*]} 补在出参名那一层之后 */
    private String wildcardAfterParamName(String expression, FunctionParamPO funcParam,
                                          List<FunctionParameterVO> fields) {
        var elementFieldNames = fields.stream()
                .map(FunctionParameterVO::getParamName)
                .collect(Collectors.toSet());
        var paramName = funcParam.getParamName();
        int nameIndex = StringUtils.indexOf(expression, paramName + ".");
        // 写了出参名就在那一层之后补；没写（$.longitude）就认根节点是数组层，插在 $ 之后
        int insertAt = nameIndex >= 0 ? nameIndex + paramName.length()
                : (expression.startsWith("$") ? 1 : -1);
        if (insertAt < 0 || insertAt >= expression.length()) {
            return expression;
        }
        var fieldName = segmentName(expression, insertAt);
        if (fieldName == null || !elementFieldNames.contains(fieldName)) {
            return expression;
        }
        return expression.substring(0, insertAt) + "[*]" + expression.substring(insertAt);
    }

    /**
     * 顶层出参是对象时，按声明的字段树找路径里第一个 LIST 字段，在它后面补 {@code [*]}。
     * <p>{@code data} 外壳、前端拼的出参名这类不在字段树里的段按未命中跳过；数组后面必须是它声明的
     * 元素字段，路径停在数组层、或不认识下一段时原样返回。</p>
     */
    private PathToken listFieldToken(String expression, List<FunctionParameterVO> fields) {
        var tokens = pathTokens(expression);
        if (tokens == null) {
            return null;
        }
        var current = fields;
        for (int i = 0; i < tokens.size() - 1; i++) {
            var field = findField(current, tokens.get(i).name());
            if (field == null) {
                continue;
            }
            var childFields = field.getFields() == null ? List.<FunctionParameterVO>of() : field.getFields();
            if (field.paramTypeEnum() != FunctionParamTypeEnum.LIST) {
                current = childFields;
                continue;
            }
            if (findField(childFields, tokens.get(i + 1).name()) != null) {
                return tokens.get(i);
            }
        }
        return null;
    }

    /** 写回目标是否在子表（非 main 分组）；main 只有实例那一行，不做逐元素展开 */
    private static boolean writesToSubtable(OntologyProperty property) {
        var storageGroup = property == null ? null : property.getStorageGroup();
        return StringUtils.isNotBlank(storageGroup) && !MAIN_STORAGE_GROUP.equals(storageGroup);
    }

    /** 字段树里按名字找一层字段 */
    private static FunctionParameterVO findField(List<FunctionParameterVO> fields, String name) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        return fields.stream().filter(field -> name.equals(field.getParamName())).findFirst().orElse(null);
    }

    /**
     * 取值路径切成字段名段，并记下每段在原文里的结束位置，供插入 {@code [*]}。
     * <p>带 {@code [0]} 这类显式下标、或写法不是简单的 {@code $.a.b} 时返回 null，按用户原意保留。</p>
     */
    private static List<PathToken> pathTokens(String expression) {
        var tokens = new ArrayList<PathToken>();
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (c == '$' || c == '.' || Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '[') {
                return null;
            }
            int start = i;
            while (i < expression.length() && expression.charAt(i) != '.' && expression.charAt(i) != '[') {
                i++;
            }
            int end = i;
            while (end > start && Character.isWhitespace(expression.charAt(end - 1))) {
                end--;
            }
            if (end > start) {
                tokens.add(new PathToken(expression.substring(start, end), end));
            }
            if (i < expression.length() && expression.charAt(i) == '[') {
                return null;
            }
        }
        return tokens.isEmpty() ? null : tokens;
    }

    /** 取值路径里的一段字段名及它在原文中的结束位置（插入 {@code [*]} 用） */
    private record PathToken(String name, int end) {
    }

    /** 取值路径从 from 起的字段名段（遇 {@code .} 或 {@code [} 截止）；下标写法或已到结尾返回 null */
    private static String segmentName(String expression, int from) {
        var start = expression.charAt(from) == '.' ? from + 1 : from;
        if (start >= expression.length() || expression.charAt(start) == '[') {
            return null;
        }
        int end = start;
        while (end < expression.length() && expression.charAt(end) != '.' && expression.charAt(end) != '[') {
            end++;
        }
        return expression.substring(start, end);
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
            currentType = field.paramTypeEnum();
            fields = field.getFields() == null ? Lists.newArrayList() : field.getFields();
        }
        return currentType;
    }

    /** 数值属性类型：执行侧按算子形参声明类型对 Number 做窄化/宽化，互绑不改变含义 */
    private static final Set<OntologyDataTypeEnum> NUMERIC_DATA_TYPES = Set.of(
            OntologyDataTypeEnum.Int, OntologyDataTypeEnum.Short, OntologyDataTypeEnum.Byte,
            OntologyDataTypeEnum.Long, OntologyDataTypeEnum.Float, OntologyDataTypeEnum.Double,
            OntologyDataTypeEnum.Decimal);

    /** 结构类属性：整列存的是 JSON 结构，转成标量形参只会得到一串文本，仍要求类型对得上 */
    private static final Set<OntologyDataTypeEnum> STRUCTURED_DATA_TYPES = Set.of(
            OntologyDataTypeEnum.Array, OntologyDataTypeEnum.Map, OntologyDataTypeEnum.Vector,
            OntologyDataTypeEnum.TimeSeries, OntologyDataTypeEnum.Attachment,
            OntologyDataTypeEnum.MediaReference, OntologyDataTypeEnum.Geohash,
            OntologyDataTypeEnum.Geoshape, OntologyDataTypeEnum.Ontology);

    /**
     * 入参绑定的属性类型与算子形参类型是否兼容；完全相等的组合已在调用处放行，这里只放宽确实能转的：
     * <ul>
     *   <li>数值形参（整型/长整型/单浮点/双浮点）↔ 任何数值属性：主键是 Int 而形参写 Long、成本是 Decimal 而形参写 Double 都能取值；</li>
     *   <li>字符串形参 ← 除结构类以外的属性：算子没有「时间」这种形参类型，时间戳/日期属性只能按文本传给脚本；</li>
     *   <li>其余组合不放行，例如文本列喂给整型形参（列里能存下任何文本）、结构类属性喂给标量形参。</li>
     * </ul>
     */
    private boolean isInputTypeCompatible(FunctionParamTypeEnum paramType, OntologyDataTypeEnum propertyType) {
        if (paramType == null || propertyType == null) {
            return true;
        }
        return switch (paramType) {
            case INTEGER, LONG, FLOAT, DOUBLE -> NUMERIC_DATA_TYPES.contains(propertyType);
            case STRING -> !STRUCTURED_DATA_TYPES.contains(propertyType);
            default -> false;
        };
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

    /**
     * 出参行没写回属性时的类型：按取值路径在算子出参的字段树里找子字段自己的类型。
     * <p>算子出参常是一个 OBJECT（或 List<对象>），整包类型对单条子字段没有意义：拿 OBJECT 去校验
     * 成功条件和上游入参，会把数值、字符串字段一起拦住。路径认不出（函数没字段树、路径停在整包）时
     * 退回整包参数类型，保持原口径。</p>
     */
    private FunctionParamTypeEnum resolveOutputRowType(FunctionParamPO funcParam, String parameterName, String expression) {
        if (funcParam == null) {
            return FunctionParamTypeEnum.STRING;
        }
        var fields = parseParamFields(funcParam);
        if (fields.isEmpty()) {
            return funcParam.getParamType();
        }
        var fromExpression = leafFieldType(fields, expression);
        if (fromExpression != null) {
            return fromExpression;
        }
        // 出参行一般只存参数名不存路径：子字段可能嵌在 LIST/OBJECT 里，按名字在整棵字段树里找
        var fromName = fieldTypeByName(fields, parameterName);
        return fromName != null ? fromName : funcParam.getParamType();
    }

    /**
     * 只有字段名、没有取值路径时在整棵字段树里按名字找类型。
     * <p>层序查找，同名优先最外层的字段，避免被更深层的同名字段覆盖。</p>
     */
    private FunctionParamTypeEnum fieldTypeByName(List<FunctionParameterVO> fields, String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        var queue = new ArrayDeque<>(fields);
        while (!queue.isEmpty()) {
            var field = queue.poll();
            if (name.equals(field.getParamName())) {
                return field.paramTypeEnum();
            }
            if (CollectionUtils.isNotEmpty(field.getFields())) {
                queue.addAll(field.getFields());
            }
        }
        return null;
    }

    /**
     * 沿 JsonPath 在字段树里逐段下行，返回最后一段命中的字段类型。
     * <p>路径里带数组下标、或前面是出参名这类字段树里没有的包装段时跳过继续往后找；
     * 最后一段没命中就不认，避免把中间某个对象字段的类型当成叶子类型。</p>
     */
    private FunctionParamTypeEnum leafFieldType(List<FunctionParameterVO> fields, String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        var current = fields;
        FunctionParamTypeEnum leafType = null;
        boolean lastMatched = false;
        for (var segmentText : path.trim().replaceFirst("^\\$\\.?", "").split("\\.")) {
            var segment = segmentText.replaceAll("\\[[^\\]]*]", "").trim();
            if (segment.isEmpty()) {
                // [*] / [0] 这类数组下标不代表字段层级
                continue;
            }
            var field = current.stream()
                    .filter(item -> segment.equals(item.getParamName()))
                    .findFirst().orElse(null);
            lastMatched = field != null;
            if (field == null) {
                continue;
            }
            leafType = field.paramTypeEnum();
            current = field.getFields() == null ? Lists.newArrayList() : field.getFields();
        }
        return lastMatched ? leafType : null;
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
     * 出参写入子表时必须把该分组下的属性选全：子表写回是新增一行，漏一个属性就写出结构不完整的行。
     * <p>main 分组改的是实例已有的那一行，漏绑只保留原值，不要求全选；分组表自己的主键由序列生成、
     * 对象主键不在分组表里，都不参与绑定。按「对象 + 分组」分桶，同名分组在不同对象下是两张表。</p>
     */
    private void checkSubtableFullWrite(ActionDefinition definition) {
        var writeRows = definition.mappings.stream()
                .filter(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY)
                .filter(m -> StringUtils.isNotEmpty(m.getPropertyUniqueIdentifier()))
                .collect(Collectors.toList());
        if (writeRows.isEmpty()) {
            return;
        }
        var writeProps = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .in(OntologyProperty::getUniqueIdentifier, writeRows.stream()
                                .map(OntologyActionMappingIn::getPropertyUniqueIdentifier)
                                .distinct()
                                .collect(Collectors.toList())))
                .stream()
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, v -> v, (existingValue, newValue) -> existingValue));
        // 按「对象 + 分组」分桶：同一分组名在不同对象下是两张表
        Map<GroupKey, Set<String>> boundByGroup = new LinkedHashMap<>();
        for (var row : writeRows) {
            var prop = writeProps.get(row.getPropertyUniqueIdentifier());
            if (prop == null) {
                continue;
            }
            var storageGroup = prop.getStorageGroup();
            if (StringUtils.isBlank(storageGroup) || MAIN_STORAGE_GROUP.equals(storageGroup)) {
                continue;
            }
            var objectId = StringUtils.isNotEmpty(row.getOntologyUniqueIdentifier())
                    ? row.getOntologyUniqueIdentifier() : prop.getOntologyUniqueIdentifier();
            boundByGroup.computeIfAbsent(new GroupKey(objectId, storageGroup), ignored -> new LinkedHashSet<>())
                    .add(prop.getUniqueIdentifier());
        }
        boundByGroup.forEach((group, boundIds) -> {
            var missing = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                            .eq(OntologyProperty::getOntologyUniqueIdentifier, group.objectId())
                            .eq(OntologyProperty::getStorageGroup, group.storageGroup()))
                    .stream()
                    .filter(prop -> !Integer.valueOf(1).equals(prop.getIsPrimaryKey()))
                    .filter(prop -> !boundIds.contains(prop.getUniqueIdentifier()))
                    .map(OntologyProperty::getDisplayName)
                    .collect(Collectors.joining("、"));
            PreconditionUtils.checkArgument(StringUtils.isEmpty(missing),
                    "出参写入子表[" + group.storageGroup() + "]必须绑定该分组下的全部属性，缺少：" + missing,
                    HttpStatus.BAD_REQUEST);
        });
    }

    /** 出参写回的子表归属：对象 + 存储分组 */
    private record GroupKey(String objectId, String storageGroup) {
    }

    /**
     * 数组出参写子表的取值路径批量补 {@code [*]}：按「对象 + 分组」成批判定，整组的列都来自数组出参才补。
     * <p>逐元素插行时同一行的每一列都取自同一个数组，组里只要有一列来自非数组出参，补完就成了
     * 「通配列与普通列混用」；那种写法原本保存得下来，不能因为补路径把它变成报错，所以这种组整组保持原样。
     * 主表分组同样跳过——主表只有实例那一行，取第一个元素就是它的值。</p>
     */
    private void fillListWildcardExpressions(ActionDefinition definition) {
        var writeRows = definition.mappings.stream()
                .filter(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY)
                .filter(m -> StringUtils.isNotEmpty(m.getFunctionParamExpression()))
                .filter(m -> StringUtils.isNotEmpty(m.getPropertyUniqueIdentifier()))
                .collect(Collectors.toList());
        if (writeRows.isEmpty()) {
            return;
        }
        var props = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .in(OntologyProperty::getUniqueIdentifier, writeRows.stream()
                                .map(OntologyActionMappingIn::getPropertyUniqueIdentifier)
                                .distinct()
                                .collect(Collectors.toList())))
                .stream()
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, v -> v, (existingValue, newValue) -> existingValue));
        var paramMap = definition.funcParams.stream()
                .collect(Collectors.toMap(FunctionParamPO::getId, v -> v, (existingValue, newValue) -> existingValue));
        var rowsByGroup = new LinkedHashMap<GroupKey, List<OntologyActionMappingIn>>();
        var propByRow = new IdentityHashMap<OntologyActionMappingIn, OntologyProperty>();
        var paramByRow = new IdentityHashMap<OntologyActionMappingIn, FunctionParamPO>();
        for (var row : writeRows) {
            var prop = props.get(row.getPropertyUniqueIdentifier());
            var funcParam = paramMap.get(row.getFunctionParamId());
            if (prop == null || funcParam == null) {
                continue;
            }
            var storageGroup = prop.getStorageGroup();
            if (StringUtils.isBlank(storageGroup) || MAIN_STORAGE_GROUP.equals(storageGroup)) {
                continue;
            }
            var objectId = StringUtils.isNotEmpty(row.getOntologyUniqueIdentifier())
                    ? row.getOntologyUniqueIdentifier() : prop.getOntologyUniqueIdentifier();
            rowsByGroup.computeIfAbsent(new GroupKey(objectId, storageGroup), ignored -> new ArrayList<>()).add(row);
            propByRow.put(row, prop);
            paramByRow.put(row, funcParam);
        }
        rowsByGroup.values().stream()
                .filter(rows -> rows.stream().allMatch(row ->
                        isListOutputRow(row.getFunctionParamExpression(), paramByRow.get(row))))
                .forEach(rows -> rows.forEach(row -> row.setFunctionParamExpression(
                        wildcardListExpression(row.getFunctionParamExpression(), paramByRow.get(row), propByRow.get(row)))));
    }

    /**
     * 这一列是否来自数组出参：顶层出参声明成 LIST，或取值路径上有声明成 LIST 的嵌套字段。
     * <p>整组都是数组列才补 {@code [*]}：组里混一列普通出参，补完就成了「通配列与普通列混用」，
     * 原来保存得下来的绑定不能因为补路径变成报错。</p>
     */
    private boolean isListOutputRow(String expression, FunctionParamPO funcParam) {
        if (funcParam.getParamType() == FunctionParamTypeEnum.LIST) {
            return true;
        }
        if (StringUtils.isBlank(expression)) {
            return false;
        }
        if (FunctionOutputValueReader.hasWildcard(expression)) {
            return true;
        }
        return listFieldToken(expression, parseParamFields(funcParam)) != null;
    }

    /**
     * 通配出参（取值路径含 {@code [*]}）的创建侧校验：一列取一批值，逐元素往子表追加插行。
     * <p>main 分组只有实例那一行，逐元素插行没有落点；同一「对象 + 分组」的各列要按元素成行，
     * 指向不同数组或混用普通路径会写出每行列来自不同元素的错位行。这些都放在创建时报错，
     * 不留到执行链路中途才炸。没填取值路径的绑定行执行时本来就跳过（该列补空），不参与混用判定。</p>
     */
    private void checkWildcardOutputGroups(ActionDefinition definition) {
        var writeRows = definition.mappings.stream()
                .filter(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY)
                .filter(m -> StringUtils.isNotEmpty(m.getPropertyUniqueIdentifier()))
                .filter(m -> StringUtils.isNotEmpty(m.getFunctionParamExpression()))
                .collect(Collectors.toList());
        if (writeRows.stream().noneMatch(m -> FunctionOutputValueReader.hasWildcard(m.getFunctionParamExpression()))) {
            return;
        }
        var props = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .in(OntologyProperty::getUniqueIdentifier, writeRows.stream()
                                .map(OntologyActionMappingIn::getPropertyUniqueIdentifier)
                                .distinct()
                                .collect(Collectors.toList())))
                .stream()
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, v -> v, (existingValue, newValue) -> existingValue));
        Map<GroupKey, Set<String>> prefixesByGroup = new LinkedHashMap<>();
        Map<GroupKey, List<String>> plainPropsByGroup = new LinkedHashMap<>();
        for (var row : writeRows) {
            var prop = props.get(row.getPropertyUniqueIdentifier());
            if (prop == null) {
                continue;
            }
            var wildcard = FunctionOutputValueReader.hasWildcard(row.getFunctionParamExpression());
            var storageGroup = prop.getStorageGroup();
            var onMain = StringUtils.isBlank(storageGroup) || MAIN_STORAGE_GROUP.equals(storageGroup);
            PreconditionUtils.checkArgument(!wildcard || !onMain,
                    "取值路径带 [*] 的出参只能写子表，属性[" + prop.getDisplayName() + "]在 main 分组上",
                    HttpStatus.BAD_REQUEST);
            if (onMain) {
                continue;
            }
            var objectId = StringUtils.isNotEmpty(row.getOntologyUniqueIdentifier())
                    ? row.getOntologyUniqueIdentifier() : prop.getOntologyUniqueIdentifier();
            var group = new GroupKey(objectId, storageGroup);
            if (wildcard) {
                var pathProblem = FunctionOutputValueReader.wildcardPathProblem(row.getFunctionParamExpression());
                PreconditionUtils.checkArgument(pathProblem == null,
                        "属性[" + prop.getDisplayName() + "]的取值路径格式不对：" + pathProblem, HttpStatus.BAD_REQUEST);
                prefixesByGroup.computeIfAbsent(group, ignored -> new LinkedHashSet<>())
                        .add(FunctionOutputValueReader.loopPrefix(row.getFunctionParamExpression()));
            } else {
                plainPropsByGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(prop.getDisplayName());
            }
        }
        prefixesByGroup.forEach((group, prefixes) -> {
            PreconditionUtils.checkArgument(prefixes.size() == 1,
                    "子表[" + group.storageGroup() + "]的通配出参指向了不同的数组：" + String.join("、", prefixes)
                            + "，同一张子表的各列要逐元素成行，取值路径的数组层必须一致", HttpStatus.BAD_REQUEST);
            var plain = plainPropsByGroup.get(group);
            // 整组都是通配列（补完 [*] 后的正常写法）时这里根本没有普通列，map 里就是 null；
            // 报错文案要在真要报时才拼，否则校验通过也会被 String.join 的 null 参数炸成 500
            if (CollectionUtils.isNotEmpty(plain)) {
                throw new BusinessException("子表[" + group.storageGroup() + "]的通配出参列与普通出参列混用：" + String.join("、", plain)
                        + "，逐元素插行时该分组每一列都要写成 " + prefixes.iterator().next()
                        + "[*].字段名 的形式", HttpStatus.BAD_REQUEST);
            }
        });
    }

    /**
     * 基础函数行为：按选中的对象做聚合或查询，不绑算子；配了结果回写才落库，因此没有关系映射和参数绑定。
     * <p>对象可以选多个（必须挂在同一对象分类下），此时只能做聚合，且各对象命中的行合成一批只算一个统一聚合值；
     * 聚合目标与条件字段存的是属性 apiName，单对象从属性里选、多对象手输，
     * 由执行器逐个对象确认这些字段都存在且关联了数据源。</p>
     */
    private void checkBasicFunctionAction(ActionCreateOrUpdateParam param, ActionDefinition definition, Integer spaceId) {
        PreconditionUtils.checkArgument(StringUtils.isEmpty(param.getFunctionApi()), "基础函数行为不需要绑定算子", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getOperation() == null && StringUtils.isEmpty(param.getStorageGroup()),
                "基础函数行为不需要基础操作和存储分组", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getLinkMapping() == null && CollectionUtils.isEmpty(param.getMappingIns()),
                "基础函数行为不绑算子也不写关系，不需要传行为参数列表和关系映射", HttpStatus.BAD_REQUEST);
        // 输出方式由配置自己说话：配了结果回写才算写入对象属性，调用方单传一个 WRITE_PROPERTY 依然拒掉
        PreconditionUtils.checkArgument(param.getOutputMode() != ActionOutputModeEnum.WRITE_PROPERTY
                        || basicFunctionActionExecutor.hasWriteBack(param.getBasicFunction()),
                "基础函数行为只有配了结果回写才落库，没配回写时输出方式只能是仅返回结果", HttpStatus.BAD_REQUEST);
        var config = param.getBasicFunction();
        basicFunctionActionExecutor.checkConfig(config, spaceId);
        // 顶层对象id 对基础函数没有独立作用（对象都在配置里），传了就必须是配置里的某一个，否则挂载对象会多出一个查不到的对象
        PreconditionUtils.checkArgument(StringUtils.isBlank(param.getOntologyIdentifier())
                        || basicFunctionActionExecutor.configOntologyIdentifiers(config).contains(param.getOntologyIdentifier()),
                "基础函数行为作用的对象写在 basicFunction 配置里，顶层对象id 要么不传，要么传配置里选中的某个对象",
                HttpStatus.BAD_REQUEST);
        definition.basicFunctionConfig = basicFunctionActionExecutor.write(config);
        definition.objectIdentifiers.addAll(basicFunctionActionExecutor.configOntologyIdentifiers(config));
        collectBasicFunctionPropertyIdentifiers(config, definition);
    }

    /**
     * 基础函数配置里的字段按属性 apiName 引用，保存时按选中对象解析成属性唯一标识记到行为上，
     * 删除属性时才能拦住；多对象共用同名字段时各对象的属性都算被引用。
     */
    private void collectBasicFunctionPropertyIdentifiers(ActionBasicFunctionConfig config, ActionDefinition definition) {
        var objectIds = basicFunctionActionExecutor.configOntologyIdentifiers(config);
        var apiNames = basicFunctionActionExecutor.referencedPropertyApiNames(config);
        if (!objectIds.isEmpty() && !apiNames.isEmpty()) {
            ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                            .in(OntologyProperty::getOntologyUniqueIdentifier, objectIds)
                            .in(OntologyProperty::getApiName, apiNames))
                    .forEach(property -> definition.propertyIdentifiers.add(property.getUniqueIdentifier()));
        }
        var writeBack = config.getWriteBack();
        if (writeBack == null || CollectionUtils.isEmpty(writeBack.getBindings())) {
            return;
        }
        writeBack.getBindings().stream()
                .map(ActionBasicFunctionConfig.FieldBinding::getTargetPropertyIdentifier)
                .filter(StringUtils::isNotBlank)
                .forEach(definition.propertyIdentifiers::add);
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
            definition.propertyIdentifiers.add(propertyId);
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
        // 关系行为执行时动的是两端实例（建边/改边/删边），两端对象都是这个行为的挂载对象
        definition.objectIdentifiers.add(link.getOntologyUniqueIdentifierFrom());
        definition.objectIdentifiers.add(link.getOntologyUniqueIdentifierTo());
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

    /**
     * 行为挂载的那一个对象：挂载对象可以有多个，列表里恰好一个时才有唯一答案。
     * <p>调度、属性值这类只在一个对象上执行的链路靠它；挂载了多个对象时返回 null，
     * 由调用方按绑定行或执行请求另行定位，不能随便取列表里的一个。</p>
     */
    private String singleActionObject(OntologyAction action) {
        var identifiers = action.getOntologyUniqueIdentifier();
        return identifiers != null && identifiers.size() == 1 ? identifiers.get(0) : null;
    }

    /**
     * 单次测试请求作用的对象：请求只给了其中一个挂载对象的实例时就是它。
     * <p>行为可以挂载在多个对象上，只传一个对象实例的旧请求靠它把实例落到对应对象；
     * 按多个对象都传了实例时返回 null，由绑定行逐条定位。</p>
     */
    private String requestActionObject(OntologyAction action, ActionInvokeParam param) {
        var mounted = action.getOntologyUniqueIdentifier();
        if (CollectionUtils.isNotEmpty(mounted) && param.getEntityPrimaryKeys() != null) {
            var hits = param.getEntityPrimaryKeys().keySet().stream()
                    .filter(mounted::contains)
                    .distinct()
                    .collect(Collectors.toList());
            if (hits.size() == 1) {
                return hits.get(0);
            }
        }
        return singleActionObject(action);
    }

    /** 调度按 actionApi 定位行为，对象 id 仅作展示：调用方指定优先，没指定时挂载单个对象才推得出，推不出存空 */
    private String scheduleObject(OntologyAction action, String requested) {
        if (StringUtils.isNotBlank(requested)) {
            return requested;
        }
        return singleActionObject(action);
    }

    /**
     * 行为在树里作用的本端对象：属性值行为取挂载的唯一对象，挂载多个对象时按绑定行认；
     * 关系行为取关系的 From 端。
     * <p>合并后不再单独保存关系行为挂在哪一端，落边时两端实例由执行请求成对给出，方向不影响结果，
     * 这里统一把 From 端当本端，另一端作为对端要求调用方补实例。</p>
     */
    private String ownTreeObject(OntologyAction bound) {
        if (bound.getActionKind() == ActionKindEnum.BASE
                && bound.getSubjectType() == ActionSubjectTypeEnum.LINK) {
            var link = actionLinkMapper.selectOne(new LambdaQueryWrapper<OntologyActionLink>()
                    .eq(OntologyActionLink::getOntologyActionId, bound.getId()));
            var linkGroup = link == null || StringUtils.isBlank(link.getOntologyLinkUniqueIdentifier()) ? null
                    : linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getUniqueIdentifier, link.getOntologyLinkUniqueIdentifier()));
            if (linkGroup != null) {
                return linkGroup.getOntologyUniqueIdentifierFrom();
            }
        }
        var single = singleActionObject(bound);
        if (single != null) {
            return single;
        }
        // 挂了多个对象：树节点一次只作用一个对象，按绑定行认（属性/实例属于哪个对象）
        var mapped = new LinkedHashSet<String>();
        ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>()
                        .eq(OntologyActionMappingIn::getOntologyActionId, bound.getId()))
                .forEach(mapping -> {
                    if (StringUtils.isNotBlank(mapping.getOntologyUniqueIdentifier())) {
                        mapped.add(mapping.getOntologyUniqueIdentifier());
                    }
                });
        if (mapped.isEmpty()) {
            // 绑定行也没写对象id（存量数据）：退回创建时选的对象，与合并前的单值口径一致
            return bound.primaryOntologyIdentifier();
        }
        return mapped.size() == 1 ? mapped.iterator().next() : null;
    }

    /**
     * 树挂载的对象：把各节点绑定的行为那列并进来。
     * <p>行为那列是保存时算的，改造前建的行为没有值，这类节点在这棵树上就少算一个对象，重新保存一次行为才补齐。</p>
     */
    private Set<String> treeObjectIdentifiers(Set<String> boundApis, Integer spaceId) {
        var identifiers = new LinkedHashSet<String>();
        if (boundApis.isEmpty()) {
            return identifiers;
        }
        list(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getOntologySpaceId, spaceId)
                .in(OntologyAction::getApi, boundApis))
                .forEach(bound -> {
                    if (CollectionUtils.isNotEmpty(bound.getOntologyUniqueIdentifier())) {
                        identifiers.addAll(bound.getOntologyUniqueIdentifier());
                    }
                });
        return identifiers;
    }

    /** 行为树黑板里按对象属性取值的键，属性 id 记到树上：删属性时要看是否被树引用 */
    private Set<String> treePropertyIdentifiers(BehaviorTreeDefinition tree) {
        var identifiers = new LinkedHashSet<String>();
        if (tree == null || CollectionUtils.isEmpty(tree.getBlackboard())) {
            return identifiers;
        }
        tree.getBlackboard().stream()
                .filter(item -> item.resolvedSource() == BehaviorBlackboardSourceEnum.PROPERTY)
                .map(BehaviorBlackboardItem::getBindPropertyUniqueIdentifier)
                .filter(StringUtils::isNotBlank)
                .forEach(identifiers::add);
        return identifiers;
    }

    /**
     * List 入参的来源属性 id 存在 source_config 的 JSON 数组里，解析出来记到行为上。
     * 形状认不出来时按空处理：存量数据可能来自手工改库，保存本身不因记录失败而拒绝。
     */
    private Set<String> listSourcePropertyIdentifiers(String sourceConfig) {
        var identifiers = new LinkedHashSet<String>();
        if (StringUtils.isBlank(sourceConfig)) {
            return identifiers;
        }
        try {
            List<String> propertyIds = jsonMapper.readValue(sourceConfig, new TypeReference<List<String>>() {
            });
            propertyIds.stream()
                    .filter(StringUtils::isNotBlank)
                    .forEach(identifiers::add);
        } catch (Exception e) {
            log.warn("行为 List 入参来源配置无法解析，引用的属性不记入行为：{}", sourceConfig);
        }
        return identifiers;
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
        /** 基础函数行为的查询配置 JSON，只有 subjectType=BASIC_FUNCTION 那一支会填 */
        private String basicFunctionConfig;
        private final List<OntologyActionMappingIn> mappings = new ArrayList<>();
        /** 本次绑定里能认出的挂载对象id，校验顺路收集，落库到行为的挂载对象列 */
        private final Set<String> objectIdentifiers = new LinkedHashSet<>();
        /** 本次定义引用到的属性id，校验顺路收集，落库到行为的属性引用列 */
        private final Set<String> propertyIdentifiers = new LinkedHashSet<>();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteActionByApi(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(!Objects.equals(action.getStatus(), ActionStatusEnum.PUBLISHED.getValue()),
                "该行为已发布，不能直接删除，请先撤回发布（行为树请先下线）", HttpStatus.BAD_REQUEST);
        // 调度归属行为按 actionApi + 空间反查定时/规则两张调度表：action_id 列已删，但这两张表一直存有这两列，校验可实现
        checkNoSchedulingAtAll(action.getOntologySpaceId(), action.getApi());
        var referencingTrees = findTreesReferencingAction(action.getOntologySpaceId(), actionApi);
        PreconditionUtils.checkArgument(referencingTrees.isEmpty(),
                "该行为被行为树引用，不能直接删除：" + String.join(", ", referencingTrees), HttpStatus.BAD_REQUEST);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, action.getId()));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, action.getId()));
        if (action.getActionKind() == ActionKindEnum.TREE && cooldownTableReady()) {
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
            wrapper.apply(OBJECT_IDENTIFIER_CONTAINS, ontologyId);
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
                .forEach((api, desc) -> functionDescMap.put(functionRefKey(spaceId, api), desc)));
        var functionNameMap = new HashMap<String, String>();
        apisBySpace.forEach((spaceId, apis) -> functionService.mapDisplayNameByApi(spaceId, apis)
                .forEach((api, name) -> functionNameMap.put(functionRefKey(spaceId, api), name)));
        List<OntologyActionInfoVO> records = pageResult.getRecords().stream()
                .map(v -> OntologyActionInfoVO.builder()
                        .id(v.getId())
                        .actionApi(v.getApi())
                        .description(v.getDescription())
                        .ontologyUniqIdentifier(v.primaryOntologyIdentifierText())
                        .displayName(v.getDisplayName())
                        .icon(v.getIcon())
                        .functionApi(v.getFunctionApi())
                        .functionName(functionNameMap.get(functionRefKey(v.getOntologySpaceId(), v.getFunctionApi())))
                        .functionDescription(functionDescMap.get(functionRefKey(v.getOntologySpaceId(), v.getFunctionApi())))
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

    /** 算子名称/描述按「空间 + api」定位，跨空间的同名 api 各是各的算子 */
    private String functionRefKey(Integer spaceId, String functionApi) {
        return spaceId + ":" + functionApi;
    }

    @Override
    public List<OntologyActionInfoVO> listSelectableActions(Integer ontologySpaceId, List<String> ontologyIdentifiers,
                                                            List<String> functionApis) {
        PreconditionUtils.checkNotNull(ontologySpaceId, "必须传对象空间id", HttpStatus.BAD_REQUEST);
        var objectIds = splitSelectionIds(ontologyIdentifiers);
        var apis = splitSelectionIds(functionApis);
        // 对象编号是收窄条件：没选出对象时两类行为都没有命中的挂载对象
        // 函数编号只收窄应用行为，基础行为本就不绑算子，不传函数编号照样可选
        if (objectIds.isEmpty()) {
            return List.of();
        }
        var wrapper = new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId)
                .eq(OntologyAction::getStatus, ActionStatusEnum.PUBLISHED.getValue())
                .and(kindOrFunction -> {
                    kindOrFunction.eq(OntologyAction::getActionKind, ActionKindEnum.BASE);
                    if (!apis.isEmpty()) {
                        kindOrFunction.or().in(OntologyAction::getFunctionApi, apis);
                    }
                })
                .and(bound -> {
                    for (int i = 0; i < objectIds.size(); i++) {
                        if (i == 0) {
                            bound.apply(OBJECT_IDENTIFIER_CONTAINS, objectIds.get(i));
                        } else {
                            bound.or().apply(OBJECT_IDENTIFIER_CONTAINS, objectIds.get(i));
                        }
                    }
                })
                .orderByDesc(OntologyAction::getId);
        // 一个行为挂多个已选对象时条件会多次命中，按行为 id 只留一条
        Map<Long, OntologyAction> unique = new LinkedHashMap<>();
        for (var action : list(wrapper)) {
            unique.putIfAbsent(action.getId(), action);
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        var functionDescMap = functionService.mapDescriptionByApi(ontologySpaceId, new ArrayList<>(apis));
        return unique.values().stream()
                .map(v -> OntologyActionInfoVO.builder()
                        .id(v.getId())
                        .actionApi(v.getApi())
                        .description(v.getDescription())
                        .ontologyUniqIdentifier(v.primaryOntologyIdentifierText())
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
    }

    /** 选择项支持重复传参，也支持逗号拼在同一个参数里；空白和重复编号丢掉 */
    private List<String> splitSelectionIds(List<String> raw) {
        if (CollectionUtils.isEmpty(raw)) {
            return List.of();
        }
        var ids = new LinkedHashSet<String>();
        for (var item : raw) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            for (var part : item.split(",")) {
                if (StringUtils.isNotBlank(part)) {
                    ids.add(part.trim());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    @Override
    public OntologyActionDetailVO getActionByApi(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        // 挂载列表里还有绑定读写的对象和关系对端，详情沿用改造前的单值口径只回显创建时选的那一端（列表首位）；
        // 其余对象仍保留在库里，执行和按对象查询照常用完整列表
        var mountedObjectText = action.primaryOntologyIdentifierText();
        var detailVO = OntologyActionDetailVO
                .builder()
                .id(action.getId())
                .ontologySpaceId(action.getOntologySpaceId())
                .functionApi(action.getFunctionApi())
                .functionName(functionService
                        .mapDisplayNameByApi(action.getOntologySpaceId(), Collections.singletonList(action.getFunctionApi()))
                        .get(action.getFunctionApi()))
                .functionDescription(functionService
                        .mapDescriptionByApi(action.getOntologySpaceId(), Collections.singletonList(action.getFunctionApi()))
                        .get(action.getFunctionApi()))
                .actionApi(action.getApi())
                .description(action.getDescription())
                .displayName(action.getDisplayName())
                .ontologyUniqIdentifier(mountedObjectText)
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
        // 基础函数行为的查询配置原样回显，前端据此重画对象、字段和条件；名称紧贴各自的 id 一并返回，供直接展示
        if (action.getSubjectType() == ActionSubjectTypeEnum.BASIC_FUNCTION) {
            var basicFunction = basicFunctionActionExecutor.parse(action.getBasicFunctionConfig(), action.getApi());
            basicFunction.setOntologyNames(basicFunctionActionExecutor.queryOntologyDisplayNames(basicFunction));
            if (basicFunctionActionExecutor.hasWriteBack(basicFunction)) {
                basicFunction.getWriteBack().setTargetOntologyName(
                        basicFunctionActionExecutor.writeBackOntologyDisplayName(basicFunction));
            }
            detailVO.setBasicFunction(basicFunction);
        }
        // 行为树没有算子、关系和参数绑定，读回定义即可
        if (action.getActionKind() == ActionKindEnum.TREE) {
            var tree = readTreeDefinition(action.getDefinition());
            // 早于来源字段的草稿按原字段推导一次，让详情和执行期看到同一份来源
            normalizeTreeBlackboardSources(tree);
            detailVO.setTreeDefinition(tree);
            detailVO.setExecuteInputs(buildTreeExecuteInputs(tree, action.getOntologySpaceId()));
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

        // 函数参数定义（入参 + 出参），先取到以便给 mappingIns 每行填 category 和参数类型
        Map<Long, FunctionParamCategoryEnum> funcParamCategoryMap = new HashMap<>();
        Map<Long, String> funcParamTypeMap = new HashMap<>();
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
                funcParams.forEach(p -> {
                    funcParamCategoryMap.put(p.getId(), p.getCategory());
                    funcParamTypeMap.put(p.getId(),
                            ScriptParamSchemaUtil.displayParamType(p.getParamType(), p.getTypeReferenceName(), p.getTypeText()));
                });
                detailVO.setFunctionParams(funcParams.stream().map(p -> FunctionParamVO.builder()
                        .paramId(p.getId())
                        .paramName(p.getParamName())
                        .paramType(funcParamTypeMap.get(p.getId()))
                        .category(p.getCategory())
                        .paramOrder(p.getParamOrder())
                        .description(p.getDescription())
                        .referenceType(p.getTypeReferenceName())
                        .fields(parseFunctionParamFields(p))
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
                                .paramType(funcParamTypeMap.get(v.getFunctionParamId()))
                                .parameterName(v.getParameterName())
                                .sourceType(v.getSourceType())
                                .sourceConfig(v.getSourceConfig())
                                .paramPath(v.getParamPath())
                                .rowLimit(v.getRowLimit())
                                .propertyUniqueIdentifier(v.getPropertyUniqueIdentifier())
                                .propertyType(prop == null ? null : prop.getPropertyType())
                                .displayName(prop == null ? null : prop.getDisplayName())
                                .propertyDisplayName(prop == null ? null : prop.getDisplayName())
                                // 分组名决定按行取参/写回时 dataPrimaryKeys 的键；与执行侧同源实时取，不存快照
                                .storageGroup(prop == null ? null : prop.getStorageGroup())
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

    /** fields JSON 按 FunctionParamDTO 结构存储，回显给绑定 UI 的定义树（各层含 description）；解析失败不拖垮详情 */
    private List<FunctionParamVO> parseFunctionParamFields(FunctionParamPO p) {
        if (StringUtils.isEmpty(p.getFields())) {
            return null;
        }
        try {
            return jsonMapper.readValue(p.getFields(), new TypeReference<List<FunctionParamVO>>() {});
        } catch (JsonProcessingException e) {
            log.warn("反序列化参数 fields 失败：paramName={}, cause={}", p.getParamName(), e.getMessage());
            return null;
        }
    }

    @SneakyThrows
    @Override
    public String executeAction(OntologyActionExecuteParam param) {
        // 对外的按对象执行属于手动触发，整次执行记一条手动执行日志；调度走 executeActionForScheduling，日志由调度侧记
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .select(OntologyMeta::getOntologySpaceId)
                .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyUniqueIdentifier()));
        return manualExecuteLogService.runAndLog(meta == null ? null : meta.getOntologySpaceId(),
                param.getActionApi(), param, () -> executeActionForScheduling(param), msg -> msg);
    }

    @SneakyThrows
    @Override
    public String executeActionForScheduling(OntologyActionExecuteParam param) {
        // 定时调度存了整段实体执行参数（entity_json）：解析成 EntityActionExecuteParam，
        // 按它指定的实例直接执行，不再按对象下全部实例遍历。缺省（没存 JSON）仍走下面的全量逻辑。
        if (param instanceof XxlJobActionExecuteParam xxlParam && xxlParam.getScheduleId() != null) {
            var scheduledTask = actionHandleTaskService.getById(xxlParam.getScheduleId());
            if (scheduledTask != null && StringUtils.isNotBlank(scheduledTask.getEntityJson())) {
                var executeParam = jsonMapper.readValue(scheduledTask.getEntityJson(), EntityActionExecuteParam.class);
                var scheduledContextInfoDTO = entityService.initActionContextInfoDTO(executeParam);
                var executeResult = entityService.executeEntityAction(executeParam, scheduledContextInfoDTO,
                        entityService.getLinkedEntities(scheduledContextInfoDTO));
                // 把本次执行结果回填到调度日志表（XxlJobHandler 已按 scheduleId 插入日志行，取最新一条）
                var handleLog = actionHandleLogMapper.selectOne(new LambdaQueryWrapper<ActionHandleLog>()
                        .eq(ActionHandleLog::getActionHandleId, xxlParam.getScheduleId())
                        .eq(ActionHandleLog::getActionHandleType, ActionSchedulingTypeEnum.TASK)
                        .orderByDesc(ActionHandleLog::getId), false);
                if (handleLog != null) {
                    actionHandleLogMapper.updateById(handleLog.setExecuteResult(executeResult));
                }
                var scheduledMsg = "执行行为" + executeParam.getActionApi() + "成功, OntologyUniqueIdentifier:"
                        + executeParam.getOntologyUniqueIdentifier() + ", entityPrimaryKey:" + executeParam.getEntityPrimaryKey();
                log.info(scheduledMsg);
                XxlJobHelper.log(scheduledMsg);
                return jsonMapper.writeValueAsString(Lists.newArrayList(scheduledMsg));
            }
        }

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
        // 实例主键只对基础函数有意义（把查询收窄到那一条数据）；别的类别按下面遍历全部实例执行，传了也不会生效
        PreconditionUtils.checkArgument(param.getEntityPrimaryKey() == null
                        || contextInfoDTO.getActionDetailVO().getSubjectType() == ActionSubjectTypeEnum.BASIC_FUNCTION,
                "只有基础函数行为支持用 entityPrimaryKey 指定实例：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        // 基础函数按配置一次把所有对象查一遍（配了回写也是这一次执行落库），按对象下实例逐个循环只会把同一个聚合重复算出来
        if (contextInfoDTO.getActionDetailVO().getSubjectType() == ActionSubjectTypeEnum.BASIC_FUNCTION) {
            return runBasicFunctionAction(contextInfoDTO.getActionDetailVO(), param.getOntologyUniqueIdentifier(),
                    param.getEntityPrimaryKey(), null, null, null);
        }
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

    @Override
    public ActionTestResultVO testAction(ActionTestParam param) {
        // 对外的行为测试都是手动触发，一次请求（含批量展开的各组）记一条手动执行日志
        return manualExecuteLogService.runAndLog(param.getOntologySpaceId(), param.getActionApi(), param,
                () -> testActionRequest(param), ActionTestResultVO::getMessage);
    }

    /**
     * 对外测试入口：多组取值必须由调用方指定展开方式（inputMode），这里给明确提示。
     * <p>inputMode 只属于本次请求，不落库；行为和绑定的形状与它无关。</p>
     */
    private ActionTestResultVO testActionRequest(ActionTestParam param) {
        var invokeParam = toInvokeParam(param);
        if (param.getInputMode() == ActionTestInputModeEnum.OBJECT) {
            return testByObjectInstances(invokeParam);
        }
        normalizeInputParamKeys(invokeParam);
        // 按实例测试同样可以按子级配子表读取规则：没配规则的子表仍按行主键倒序取默认行数
        resolveListReadRulesForInstanceTest(invokeParam);
        var listParams = listInputParamNames(invokeParam);
        // 外部输入参数必须给值：单值直跑与批量展开同样会拿 null 静默继续，统一在分支前校验
        var testActionPo = findTestAction(param.getOntologySpaceId(), param.getActionApi());
        checkExternalParamsProvided(testActionPo, param.getParams());
        // 入参方向把某个字段写成数组就是批量测试：按 inputMode 展开成组合后逐组跑一遍
        var testAxes = collectTestAxes(invokeParam, listParams);
        if (testAxes.isEmpty()) {
            return runTestAction(invokeParam, listParams);
        }
        PreconditionUtils.checkArgument(param.getInputMode() != null,
                "这次测试要按多个候选值展开，请在 inputMode 里指定展开方式：" + describeInputModes(),
                HttpStatus.BAD_REQUEST);
        return testActionCombos(invokeParam, testAxes, expandByMode(param.getInputMode(), testAxes, true),
                listParams, null);
    }

    @Override
    public ActionTestResultVO testActionForScheduling(ActionTestParam param) {
        var invokeParam = toInvokeParam(param);
        if (param.getInputMode() == ActionTestInputModeEnum.OBJECT) {
            return testByObjectInstances(invokeParam);
        }
        // 前端按绑定行选数据位置时键是字段名（longitude、position.longitude），执行侧只认顶层入参名，
        // 这里先归到所属入参；顶层入参名的写法不动
        normalizeInputParamKeys(invokeParam);
        resolveListReadRulesForInstanceTest(invokeParam);
        var listParams = listInputParamNames(invokeParam);
        // 调度/行为树内部调用不会传候选值数组；真出现数组又没指定方式时保持改造前的自动判断
        var testAxes = collectTestAxes(invokeParam, listParams);
        if (testAxes.isEmpty()) {
            return runTestAction(invokeParam, listParams);
        }
        var combos = param.getInputMode() == null ? expandTestCombos(invokeParam, testAxes, listParams)
                : expandByMode(param.getInputMode(), testAxes, true);
        return testActionCombos(invokeParam, testAxes, combos, listParams, null);
    }

    private OntologyAction findTestAction(Integer ontologySpaceId, String actionApi) {
        return getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
    }

    private String describeInputModes() {
        return Arrays.stream(ActionTestInputModeEnum.values())
                .map(mode -> mode.name() + "（" + mode.getName() + "）")
                .collect(Collectors.joining("、"));
    }

    private List<List<Object>> expandByMode(ActionTestInputModeEnum mode, List<TestAxis> axes, boolean capByMaxCombos) {
        return mode == ActionTestInputModeEnum.GROUP ? expandTestGroups(axes)
                : expandTestProduct(axes, capByMaxCombos);
    }

    /**
     * 对象模式：不选实例和行，遍历行为涉及对象下的全部实例逐组执行；涉及多个对象时按笛卡尔积组合。
     * <p>只支持绑算子的行为；子表按 listReadRules 读取，不配规则按行主键倒序取最新 10 行；
     * 外部输入照旧在 params 里给，多组共用同一个值。</p>
     */
    private ActionTestResultVO testByObjectInstances(ActionInvokeParam param) {
        var action = findTestAction(param.getOntologySpaceId(), param.getActionApi());
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.TREE,
                "行为树不支持按单个行为测试", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.BASE,
                "对象模式只支持绑算子的行为，属性值/实例关系/基础函数行为请选择具体实例：" + action.getApi(),
                HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getEntityPrimaryKey() == null
                        && MapUtils.isEmpty(param.getEntityPrimaryKeys())
                        && MapUtils.isEmpty(param.getInputParams()) && MapUtils.isEmpty(param.getOutputParams())
                        && param.getDataPrimaryKey() == null && param.getLinkEntityPrimaryKey() == null
                        && param.getWriteBackEntityPrimaryKey() == null,
                "对象模式按实例和规则自动定位，不用再传 entityPrimaryKey、entityPrimaryKeys、inputParams、outputParams、"
                        + "dataPrimaryKey、linkEntityPrimaryKey、writeBackEntityPrimaryKey：" + action.getApi(),
                HttpStatus.BAD_REQUEST);
        checkExternalParamsProvided(action, param.getParams());
        var detail = getActionByApi(param.getOntologySpaceId(), param.getActionApi());
        var objects = objectModeObjects(action, detail);
        // 读取规则按绑定行归位：子级字段的键指到哪个对象、哪张子表就只管那张表，逐组共用同一份规则
        param.setResolvedReadRules(normalizeListReadRules(param,
                objects.stream().map(ObjectAxis::objectId).collect(Collectors.toCollection(LinkedHashSet::new)),
                objects.isEmpty() ? null : objects.get(0).objectId()));
        // 逐组把遍历到的实例写进 entityPrimaryKeys：请求里这张表是空的（对象模式不选实例）
        param.setEntityPrimaryKeys(new LinkedHashMap<>());
        var axes = new ArrayList<TestAxis>(objects.size());
        objects.forEach(object -> axes.add(instanceAxis(object.key(), object.objectId())));
        // 对象模式不设组合上限：用户明确要求把实例全部跑一遍
        return testActionCombos(param, axes, expandTestProduct(axes, false),
                listInputParamNames(action), "对象模式");
    }

    /** 对象模式遍历的取值轴：关系行为两端各一轴（自关系也两轴，覆盖实例两两组合），其余按涉及对象逐个 */
    private List<ObjectAxis> objectModeObjects(OntologyAction action, OntologyActionDetailVO detail) {
        var axes = new ArrayList<ObjectAxis>();
        var linkMapping = detail.getLinkMapping();
        if (linkMapping != null) {
            var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getUniqueIdentifier, linkMapping.getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(linkGroup != null,
                    "无效的对象关系：" + linkMapping.getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
            var fromObject = linkGroup.getOntologyUniqueIdentifierFrom();
            var toObject = linkGroup.getOntologyUniqueIdentifierTo();
            axes.add(new ObjectAxis(TEST_LINK_FROM_KEY, fromObject));
            axes.add(new ObjectAxis(TEST_LINK_TO_KEY, toObject));
            // 关系两端之外还读了别的对象（第三个对象上的属性入参/写回）时也一并遍历
            var otherObjects = new LinkedHashSet<String>();
            CollectionUtils.emptyIfNull(action.getOntologyUniqueIdentifier()).stream()
                    .filter(StringUtils::isNotBlank).forEach(otherObjects::add);
            CollectionUtils.emptyIfNull(detail.getMappingIns()).forEach(mapping -> {
                if (readNeedsInstance(mapping) || writesProperty(mapping)) {
                    otherObjects.add(mapping.getOntologyUniqueIdentifier());
                }
            });
            otherObjects.remove(fromObject);
            otherObjects.remove(toObject);
            otherObjects.remove("");
            otherObjects.stream().filter(StringUtils::isNotBlank)
                    .forEach(objectId -> axes.add(new ObjectAxis(objectId, objectId)));
            return axes;
        }
        var objects = new LinkedHashSet<String>();
        CollectionUtils.emptyIfNull(action.getOntologyUniqueIdentifier()).stream()
                .filter(StringUtils::isNotBlank).forEach(objects::add);
        if (objects.isEmpty()) {
            // 存量行为的挂载对象列可能为空：按绑定行认对象
            CollectionUtils.emptyIfNull(detail.getMappingIns()).forEach(mapping -> {
                var needsObject = readNeedsInstance(mapping) || writesProperty(mapping);
                if (needsObject && StringUtils.isNotBlank(mapping.getOntologyUniqueIdentifier())) {
                    objects.add(mapping.getOntologyUniqueIdentifier());
                }
            });
        }
        PreconditionUtils.checkArgument(!objects.isEmpty(),
                "行为没有可用于遍历实例的挂载对象：" + action.getApi(), HttpStatus.BAD_REQUEST);
        var primary = action.primaryOntologyIdentifier();
        if (StringUtils.isNotBlank(primary)) {
            objects.remove(primary);
            axes.add(new ObjectAxis(primary, primary));
        }
        objects.forEach(objectId -> axes.add(new ObjectAxis(objectId, objectId)));
        return axes;
    }

    private static boolean writesProperty(ActionParamMappingVO mapping) {
        return mapping.getCategory() == FunctionParamCategoryEnum.OUTPUT
                && mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY;
    }

    /** 对象模式的一个取值轴：axisKey 是写进 entityPrimaryKeys 的键（对象id 或关系两端 from/to） */
    private record ObjectAxis(String key, String objectId) {
    }

    /** 对象下的全部实例主键作为取值轴；没有实例直接报错，别让「一组都没跑」看起来像成功 */
    private TestAxis instanceAxis(String axisKey, String objectId) {
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(objectId),
                "对象模式遍历实例时对象id为空，无法定位实例", HttpStatus.BAD_REQUEST);
        var primaryKeys = allInstancePrimaryKeys(objectId);
        PreconditionUtils.checkArgument(!primaryKeys.isEmpty(),
                "对象[" + objectDisplayName(objectId) + "]下没有实例数据，无法用对象模式执行", HttpStatus.BAD_REQUEST);
        return new TestAxis(TEST_AXIS_FIELD_ENTITY_PRIMARY_KEYS, axisKey, null, primaryKeys);
    }

    /** 分页拉取对象下全部实例主键（只取主存储分组的主键列） */
    private List<Object> allInstancePrimaryKeys(String objectId) {
        var primaryKeys = new ArrayList<Object>();
        for (int pageNum = 1; ; pageNum++) {
            var page = entityService.getEntities(objectId, "", "", pageNum, TREE_BATCH_PAGE_SIZE, false);
            var records = page.getRecords();
            if (CollectionUtils.isEmpty(records)) {
                break;
            }
            records.stream().map(EntityInfoVO::getPrimaryKey).filter(Objects::nonNull).forEach(primaryKeys::add);
            if (records.size() < TREE_BATCH_PAGE_SIZE) {
                break;
            }
        }
        return primaryKeys;
    }

    private String objectDisplayName(String objectId) {
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, objectId));
        return meta == null || StringUtils.isEmpty(meta.getDisplayName()) ? objectId : meta.getDisplayName();
    }

    /**
     * 子表读取规则按子级字段定位：键写成 {@code 父参数名.字段路径}（字段名只属于一个入参时也可裸写），
     * 一条键对应的那些绑定行自己带着对象与存储分组，所以同一入参下来自不同对象的子表能各配各的行数与排序。
     * <p>键不再认顶层入参名：一个入参的子级可能落在多张子表上，而一张子表一次查询只能按一个列取一段行。</p>
     * <p>落到同一张物理子表的多条规则必须完全一致，否则同一张表会出现两种取法，直接报错。</p>
     */
    /**
     * 按实例测试的子表读取规则：键与对象模式同一套写法（父参数名.字段路径），按绑定行归到对象与分组。
     * <p>规则只改"这个子级读几行、按哪列排"，没配规则的子表仍按行主键倒序取默认行数；
     * 子级位置自己点了行主键时以行主键为准，规则不再参与那次读取。</p>
     */
    private void resolveListReadRulesForInstanceTest(ActionInvokeParam param) {
        if (MapUtils.isEmpty(param.getListReadRules())) {
            return;
        }
        var index = inputParamKeyIndex(param.getOntologySpaceId(), param.getActionApi());
        PreconditionUtils.checkNotNull(index,
                "子表读取规则只支持绑算子的应用行为：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        var action = findTestAction(param.getOntologySpaceId(), param.getActionApi());
        var legacyObjectId = action == null ? null : action.primaryOntologyIdentifier();
        Set<String> boundObjects = index.instanceRows().stream()
                .map(mapping -> StringUtils.defaultIfEmpty(mapping.getOntologyUniqueIdentifier(), legacyObjectId))
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (StringUtils.isNotEmpty(legacyObjectId)) {
            boundObjects.add(legacyObjectId);
        }
        if (action != null) {
            CollectionUtils.emptyIfNull(action.getOntologyUniqueIdentifier()).stream()
                    .filter(StringUtils::isNotBlank)
                    .forEach(boundObjects::add);
        }
        var resolved = normalizeListReadRules(param, boundObjects, legacyObjectId);
        checkRulesNotOnElementParams(param, resolved);
        param.setResolvedReadRules(resolved);
    }

    /**
     * 逐元素给数据位置的入参按自己的行读取，不经过入参级视图，规则对它不会生效；
     * 两条路径同时用就直接报出来，不静默忽略规则。
     */
    private void checkRulesNotOnElementParams(ActionInvokeParam param,
                                              Map<String, Map<String, Map<String, ActionTestListReadRuleParam>>> resolved) {
        if (MapUtils.isEmpty(resolved) || MapUtils.isEmpty(param.getInputParams())) {
            return;
        }
        resolved.forEach((owner, byObject) -> {
            var shape = param.getInputParams().get(owner);
            if (shape == null || shape.isEmpty()) {
                return;
            }
            var positions = shape.getPositions();
            var elementStyle = shape.isCandidates()
                    || (positions != null && (positions.size() > 1
                    || (positions.size() == 1 && MapUtils.isNotEmpty(positions.get(0).getDataPrimaryKeys()))));
            PreconditionUtils.checkArgument(!elementStyle,
                    "入参[" + owner + "]按元素逐条给了数据位置，子表读取规则不会生效：规则只作用于按实例或按对象读取的入参，"
                            + "请去掉该入参的逐元素位置，或去掉它的读取规则", HttpStatus.BAD_REQUEST);
        });
    }

    private Map<String, Map<String, Map<String, ActionTestListReadRuleParam>>> normalizeListReadRules(
            ActionInvokeParam param, Set<String> traversedObjects, String legacyObjectId) {
        var raw = param.getListReadRules();
        if (MapUtils.isEmpty(raw)) {
            return null;
        }
        var index = inputParamKeyIndex(param.getOntologySpaceId(), param.getActionApi());
        PreconditionUtils.checkNotNull(index,
                "子表读取规则只支持绑算子的应用行为：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        var rowsByFieldKey = listReadRuleRows(index);
        Map<String, Map<String, Map<String, ActionTestListReadRuleParam>>> rulesByParam = new LinkedHashMap<>();
        Map<String, RuleOwnerKey> appliedByTable = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            var key = StringUtils.trimToEmpty(entry.getKey());
            var rule = entry.getValue();
            PreconditionUtils.checkArgument(StringUtils.isNotEmpty(key),
                    "子表读取规则里有一条空键，请写成 父参数名.字段路径：" + param.getActionApi(),
                    HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkNotNull(rule,
                    "子表读取规则里[" + key + "]的规则是空的，不需要规则就别写这一条", HttpStatus.BAD_REQUEST);
            var rows = resolveRuleRows(index, rowsByFieldKey, key);
            var owner = rows.get(0).owner();
            checkRuleAppliesToInstanceParam(owner, key, rule, index);
            var propertyIds = rows.stream().map(row -> row.mapping().getPropertyUniqueIdentifier())
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            PreconditionUtils.checkArgument(!propertyIds.isEmpty(),
                    "子表读取规则的键[" + key + "]对应的绑定行没有绑定对象属性，没有可作用的子表分组",
                    HttpStatus.BAD_REQUEST);
            var properties = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                    .in(OntologyProperty::getUniqueIdentifier, propertyIds));
            var subGroups = new LinkedHashSet<String>();
            for (var row : rows) {
                var group = groupOfProperty(properties, row.mapping().getPropertyUniqueIdentifier());
                if (MAIN_STORAGE_GROUP.equals(group)) {
                    continue;
                }
                var objectId = rowObjectId(row, legacyObjectId);
                checkRuleTableReadable(owner, key, rule, traversedObjects, properties, group, objectId);
                subGroups.add(group);
                var table = objectId + "." + group;
                var applied = appliedByTable.putIfAbsent(table, new RuleOwnerKey(owner, key, rule));
                if (applied != null && !applied.rule().equals(rule)) {
                    throw new BusinessException("子表[" + objectDisplayName(objectId) + "." + group
                            + "]被规则键[" + applied.key() + "]和[" + key + "]重复指定，同一张子表只能按一种顺序取行"
                            + "（前者是 " + describeListReadRule(applied.rule()) + "，后者是 "
                            + describeListReadRule(rule) + "），请把两条写成一样的规则", HttpStatus.BAD_REQUEST);
                }
                rulesByParam.computeIfAbsent(owner, k -> new LinkedHashMap<>())
                        .computeIfAbsent(objectId, k -> new LinkedHashMap<>())
                        .put(group, rule);
            }
            PreconditionUtils.checkArgument(!subGroups.isEmpty(),
                    "子表读取规则的键[" + key + "]绑定的属性都在主表，主表只有实例那一行，规则不会生效",
                    HttpStatus.BAD_REQUEST);
        }
        return rulesByParam;
    }

    /** 读取规则命中的一条绑定行：归到的顶层入参名 + 绑定行本身（行上的属性决定对象与子表分组） */
    private record RuleRow(String owner, ActionParamMappingVO mapping) {
    }

    /** 一张物理子表上先落地的规则，用于报出重复指定同一张表的两条键 */
    private record RuleOwnerKey(String owner, String key, ActionTestListReadRuleParam rule) {
    }

    /**
     * 规则键认的绑定行：字段路径（{@code tracks.time}）、裸叶子名（{@code time}）、绑定行自己填的字段名；
     * 直接绑属性、没有字段路径的行按属性的 apiName 认。
     * <p>这里单独建索引：混进 inputParams 用的 ownersByField 会改变数据位置键的同名判定。</p>
     */
    private Map<String, List<RuleRow>> listReadRuleRows(InputParamKeyIndex index) {
        var apiNameByProperty = propertyApiNames(index);
        Map<String, List<RuleRow>> rowsByKey = new LinkedHashMap<>();
        for (var mapping : CollectionUtils.emptyIfNull(index.instanceRows())) {
            var owner = paramApiName(index, mapping.getFunctionParamId());
            if (StringUtils.isEmpty(owner)) {
                continue;
            }
            var paramPath = mapping.getParamPath();
            if (StringUtils.isNotEmpty(paramPath)) {
                addRuleRow(rowsByKey, paramPath, owner, mapping);
                addRuleRow(rowsByKey, owner + "." + paramPath, owner, mapping);
                var leaf = StringUtils.substringAfterLast(paramPath, ".");
                addRuleRow(rowsByKey, leaf, owner, mapping);
                addRuleRow(rowsByKey, owner + "." + leaf, owner, mapping);
                var field = mapping.getParameterName();
                addRuleRow(rowsByKey, field, owner, mapping);
                addRuleRow(rowsByKey, owner + "." + field, owner, mapping);
                continue;
            }
            // 直接绑属性、没有字段路径的行只能按属性的 apiName 认
            var apiName = apiNameByProperty.get(mapping.getPropertyUniqueIdentifier());
            addRuleRow(rowsByKey, apiName, owner, mapping);
            addRuleRow(rowsByKey, owner + "." + apiName, owner, mapping);
        }
        return rowsByKey;
    }

    /** 没有字段路径的绑定行按属性 apiName 认键：按本次涉及的属性 id 一次查出 apiName */
    private Map<String, String> propertyApiNames(InputParamKeyIndex index) {
        var propertyIds = index.instanceRows().stream()
                .filter(mapping -> StringUtils.isEmpty(mapping.getParamPath()))
                .map(ActionParamMappingVO::getPropertyUniqueIdentifier)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        return ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .in(OntologyProperty::getUniqueIdentifier, propertyIds)).stream()
                .filter(p -> StringUtils.isNotBlank(p.getApiName()))
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, OntologyProperty::getApiName,
                        (one, two) -> one));
    }

    private static void addRuleRow(Map<String, List<RuleRow>> rowsByKey, String key, String owner,
                                   ActionParamMappingVO mapping) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        rowsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(new RuleRow(owner, mapping));
    }

    private static String paramApiName(InputParamKeyIndex index, Long paramId) {
        var def = paramId == null ? null : index.defById().get(paramId);
        return def == null ? null : def.getParamName();
    }

    /** 规则键归到绑定行：裸字段名落在多个入参上、或键其实是顶层入参名时报错，不当入参级规则用 */
    private List<RuleRow> resolveRuleRows(InputParamKeyIndex index, Map<String, List<RuleRow>> rowsByFieldKey,
                                          String key) {
        var rows = rowsByFieldKey.get(key);
        var isParamName = index.defByName().containsKey(key);
        PreconditionUtils.checkArgument(rows != null || isParamName,
                "子表读取规则的键[" + key + "]不是任何绑定行的字段路径，请写成 父参数名.字段路径"
                        + "（如 入参名.属性名）", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(rows != null,
                "子表读取规则按子级字段配置：键[" + key + "]只是入参名，而它的子级可能落在不同对象的不同子表上，"
                        + "请写成 父参数名.字段路径", HttpStatus.BAD_REQUEST);
        var owners = rows.stream().map(RuleRow::owner).filter(StringUtils::isNotBlank)
                .distinct().collect(Collectors.toList());
        PreconditionUtils.checkArgument(owners.size() == 1,
                "子表读取规则里的字段[" + key + "]同时属于入参 " + String.join("、", owners)
                        + "，无法判断规则给哪个入参，请改写成 父参数名.字段路径（如 "
                        + owners.get(0) + "." + key + "）", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(!isParamName,
                "子表读取规则的键[" + key + "]既是入参名又是绑定行的字段名，请写成 父参数名.字段路径"
                        + "（如 " + owners.get(0) + "." + key + "）明确指到子级", HttpStatus.BAD_REQUEST);
        return rows;
    }

    /** 规则要作用在真会读实例的入参上：外部输入、固定值来源的入参没有子表可读 */
    private void checkRuleAppliesToInstanceParam(String owner, String key, ActionTestListReadRuleParam rule,
                                                 InputParamKeyIndex index) {
        var def = index.defByName().get(owner);
        PreconditionUtils.checkArgument(def != null && def.getParamId() != null
                        && index.instanceParamIds().contains(def.getParamId()),
                "入参[" + owner + "]的取值不读实例数据（外部输入或固定值），子表读取规则不会生效，请去掉这条规则：" + key,
                HttpStatus.BAD_REQUEST);
        if (rule.getRowCount() == null) {
            return;
        }
        PreconditionUtils.checkArgument(rule.getRowCount() > 0,
                "子表读取规则的行数必须大于 0：键[" + key + "]填的是 " + rule.getRowCount(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(def.paramTypeEnum() == FunctionParamTypeEnum.LIST,
                "入参[" + owner + "]不是列表入参，一次只用到一行，rowCount 不会生效：要取最新或最早的那一行"
                        + "请用 orderByProperty 加 sort，把 rowCount 去掉：" + key, HttpStatus.BAD_REQUEST);
    }

    /** 绑定行没存分组时的兜底：按行上的属性查存储分组，主表属性返回 main */
    private String groupOfProperty(List<OntologyProperty> properties, String propertyUniqueIdentifier) {
        var property = StringUtils.isEmpty(propertyUniqueIdentifier) ? null : properties.stream()
                .filter(p -> propertyUniqueIdentifier.equals(p.getUniqueIdentifier()))
                .findFirst().orElse(null);
        return property == null ? MAIN_STORAGE_GROUP
                : StringUtils.defaultIfBlank(property.getStorageGroup(), MAIN_STORAGE_GROUP);
    }

    /** 绑定行归属的对象：行上没存对象时沿用老口径，按本次遍历的主对象认 */
    private String rowObjectId(RuleRow row, String legacyObjectId) {
        var objectId = row.mapping().getOntologyUniqueIdentifier();
        if (StringUtils.isNotEmpty(objectId)) {
            return objectId;
        }
        return legacyObjectId;
    }

    /** 这张子表要真能按规则取行：对象在本次遍历集合里、分组里有绑定行的数据源列、排序列就在这一张表上 */
    private void checkRuleTableReadable(String owner, String key, ActionTestListReadRuleParam rule,
                                        Set<String> traversedObjects, List<OntologyProperty> properties,
                                        String group, String objectId) {
        PreconditionUtils.checkArgument(objectId != null && traversedObjects.contains(objectId),
                "子表读取规则的键[" + key + "]读的对象不在本次遍历的对象集合里，规则不会生效："
                        + (objectId == null ? "对象未确定" : objectDisplayName(objectId)), HttpStatus.BAD_REQUEST);
        var readable = properties.stream()
                .filter(p -> group.equals(StringUtils.defaultIfBlank(p.getStorageGroup(), MAIN_STORAGE_GROUP)))
                .anyMatch(p -> StringUtils.isNotEmpty(p.getDatasourceColumnName()));
        PreconditionUtils.checkArgument(readable,
                "子表读取规则的键[" + key + "]在对象[" + objectDisplayName(objectId) + "]的子表[" + group
                        + "]里没有绑定带数据源列的属性，规则不会生效", HttpStatus.BAD_REQUEST);
        if (StringUtils.isEmpty(rule.getOrderByProperty())) {
            return;
        }
        var canOrderBy = properties.stream()
                .filter(p -> rule.getOrderByProperty().equals(p.getApiName()))
                .filter(p -> StringUtils.isNotEmpty(p.getDatasourceColumnName()))
                .anyMatch(p -> group.equals(StringUtils.defaultIfBlank(p.getStorageGroup(), MAIN_STORAGE_GROUP)));
        PreconditionUtils.checkArgument(canOrderBy,
                "子表读取规则的键[" + key + "]按属性[" + rule.getOrderByProperty() + "]排序，但对象["
                        + objectDisplayName(objectId) + "]的子表[" + group + "]的绑定行里没有这个属性，"
                        + "请改成这张子表上绑过的属性，或去掉排序按行主键取", HttpStatus.BAD_REQUEST);
    }

    /** 规则内容的简述，只用于「同一张子表被重复指定」的报错文案 */
    private String describeListReadRule(ActionTestListReadRuleParam rule) {
        var parts = new ArrayList<String>();
        parts.add(rule.getRowCount() == null ? "默认行数" : "行数 " + rule.getRowCount());
        parts.add(StringUtils.isEmpty(rule.getOrderByProperty()) ? "按行主键排序"
                : "按 " + rule.getOrderByProperty() + " " + (rule.getSort() == null ? "DESC" : rule.getSort().name()));
        return String.join("、", parts);
    }

    /**
     * 来源为外部输入的参数必须在 params 里给值：多组测试共用同一个值，缺了只能拿到 null，
     * 看不出是没给还是算子算出来空。键存在即算给了值（显式 null 也算）。
     * <p>给的键和取值侧同一套：绑定行的参数名、字段路径、父参数名.字段路径全名、整包参数名都算给了值。</p>
     */
    private void checkExternalParamsProvided(OntologyAction action, Map<String, Object> params) {
        if (action == null || StringUtils.isEmpty(action.getFunctionApi())) {
            return;
        }
        var detail = getActionByApi(action.getOntologySpaceId(), action.getApi());
        if (detail == null || CollectionUtils.isEmpty(detail.getMappingIns())) {
            return;
        }
        var namesById = new HashMap<Long, String>();
        CollectionUtils.emptyIfNull(detail.getFunctionParams()).forEach(p -> {
            if (p.getParamId() != null && StringUtils.isNotBlank(p.getParamName())) {
                namesById.putIfAbsent(p.getParamId(), p.getParamName());
            }
        });
        var missing = new LinkedHashSet<String>();
        Set<String> duplicateLeafNames = new LinkedHashSet<>();
        Map<String, Set<String>> ownersByLeaf = new LinkedHashMap<>();
        for (var mapping : detail.getMappingIns()) {
            if (mapping.getCategory() != FunctionParamCategoryEnum.INPUT
                    || mapping.getSourceType() != ActionParamSourceEnum.EXTERNAL) {
                continue;
            }
            var owner = namesById.get(mapping.getFunctionParamId());
            var name = StringUtils.isNotBlank(mapping.getParameterName())
                    ? mapping.getParameterName() : owner;
            if (StringUtils.isBlank(name)) {
                continue;
            }
            if (StringUtils.isNotEmpty(mapping.getParamPath()) && StringUtils.isNotBlank(owner)
                    && !StringUtils.equals(owner, name)) {
                ownersByLeaf.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(owner);
            }
            if (externalParamProvided(params, name, mapping.getParamPath(), owner)) {
                continue;
            }
            missing.add(name);
        }
        ownersByLeaf.forEach((name, owners) -> {
            if (owners.size() > 1) {
                duplicateLeafNames.add(name);
            }
        });
        if (missing.isEmpty()) {
            return;
        }
        var message = new StringBuilder("来源为外部输入的参数必须在 params 里给值：")
                .append(String.join("、", missing));
        var duplicatedMissing = missing.stream().filter(duplicateLeafNames::contains).collect(Collectors.toList());
        if (duplicatedMissing.isEmpty()) {
            message.append("；多组测试共用同一个值，旧行为按字段逐个绑定外部输入的重新保存后会合并成整包绑定");
        } else {
            var firstDuplicated = duplicatedMissing.get(0);
            var exampleOwner = CollectionUtils.emptyIfNull(ownersByLeaf.get(firstDuplicated)).stream()
                    .filter(StringUtils::isNotBlank).findFirst().orElse(firstDuplicated);
            message.append("；其中 ").append(String.join("、", duplicatedMissing))
                    .append(" 是挂在多条入参上的同名字段，同一个键只会喂给全部入参，")
                    .append("请按 父参数名.字段路径（如 ").append(exampleOwner).append(".").append(firstDuplicated)
                    .append("）给值");
        }
        throw new BusinessException(message.toString(), HttpStatus.BAD_REQUEST);
    }

    /** 某条外部输入绑定行在 params 里是否有对应的键：参数名 / 字段路径 / 父参数名.字段路径 / 整包参数名 */
    private static boolean externalParamProvided(Map<String, Object> params, String name, String paramPath,
                                                 String owner) {
        if (params == null) {
            return false;
        }
        if (params.containsKey(name) || (StringUtils.isNotEmpty(paramPath) && params.containsKey(paramPath))) {
            return true;
        }
        if (StringUtils.isBlank(owner)) {
            return false;
        }
        return params.containsKey(owner)
                || (StringUtils.isNotEmpty(paramPath) && params.containsKey(owner + "." + paramPath));
    }

    /**
     * 对外请求转内部调用参数。
     * <p>按对象方向指定实例/行只有行为树在用（树级默认行主键），不放进对外的测试请求，
     * 所以这里必然留空。</p>
     */
    private ActionInvokeParam toInvokeParam(ActionTestParam param) {
        return ActionInvokeParam.builder()
                .ontologySpaceId(param.getOntologySpaceId())
                .actionApi(param.getActionApi())
                .entityPrimaryKey(param.getEntityPrimaryKey())
                .entityPrimaryKeys(param.getEntityPrimaryKeys())
                .writeBackEntityPrimaryKey(param.getWriteBackEntityPrimaryKey())
                .inputParams(param.getInputParams())
                .outputParams(param.getOutputParams())
                .dataPrimaryKey(param.getDataPrimaryKey())
                .linkEntityPrimaryKey(param.getLinkEntityPrimaryKey())
                .params(param.getParams())
                .inputMode(param.getInputMode())
                .listReadRules(param.getListReadRules())
                .build();
    }

    /**
     * 行为测试：用一条真实数据把行为定义跑一遍并落库。
     * <p>
     * 不加事务：属性值行为写数据湖、实例关系行为写图库、算子行为跨两者，
     * 且发现必填属性缺失时的失效标记必须独立提交，不能被随后的异常回滚掉。
     * <p>
     * 批量展开在外层做（对外测试的 {@link #testActionRequest(ActionTestParam)} 与行为树/调度走的
     * {@link #testActionForScheduling(ActionTestParam)}）：行为树节点走后者执行单条行为，
     * 黑板里的值偶尔本来就是集合，不能在那条链路上悄悄炸成多组。手动执行日志记在对外的
     * {@link #testAction(ActionTestParam)}，这一层一次调用就是一次执行，不重复记。
     */
    private ActionTestResultVO runTestAction(ActionInvokeParam param) {
        return runTestAction(param, null);
    }

    /** listParams 为 null 时按行为绑定的算子定义现算，树节点执行走这一支 */
    private ActionTestResultVO runTestAction(ActionInvokeParam param, Set<String> listParams) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, param.getOntologySpaceId()));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        // 草稿可以试跑（发布前正是要拿它验证定义）
        // 行为树编排的是已有行为，没有单条行为的入参绑定，走行为测试只会得到"算子不存在"这类误导提示
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.TREE,
                "行为树不支持按单个行为测试", HttpStatus.BAD_REQUEST);
        // 回写实例只有基础函数用得上，别的类别传了不会生效，直接拒掉
        PreconditionUtils.checkArgument(param.getWriteBackEntityPrimaryKey() == null
                        || (action.getActionKind() == ActionKindEnum.BASE
                        && action.getSubjectType() == ActionSubjectTypeEnum.BASIC_FUNCTION),
                "只有基础函数行为支持用 writeBackEntityPrimaryKey 指定回写实例：" + param.getActionApi(),
                HttpStatus.BAD_REQUEST);
        // 属性值行为按行为所属对象定位实例；关系行为的本端/对端实例在测试体里按关系定义校验，两端都必须给
        if (action.getActionKind() == ActionKindEnum.BASE
                && action.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE) {
            PreconditionUtils.checkArgument(param.getEntityPrimaryKey() != null, "行为测试必须提供实体主键", HttpStatus.BAD_REQUEST);
        }

        var detail = getActionByApi(param.getOntologySpaceId(), param.getActionApi());
        // from/to 是关系测试的保留键，非关系行为带上它说明请求选错了，别让它混进对象实例 map
        if (detail.getLinkMapping() == null && param.getEntityPrimaryKeys() != null
                && (param.getEntityPrimaryKeys().containsKey(TEST_LINK_FROM_KEY)
                || param.getEntityPrimaryKeys().containsKey(TEST_LINK_TO_KEY))) {
            throw new BusinessException("行为没有绑定关系，不需要传 from/to 实例主键", HttpStatus.BAD_REQUEST);
        }
        if (action.getActionKind() == ActionKindEnum.BASE) {
            // 不绑算子的基础行为没有算子入参可指向
            if (MapUtils.isNotEmpty(param.getInputParams()) || MapUtils.isNotEmpty(param.getOutputParams())) {
                throw new BusinessException("属性值/实例关系/基础函数行为不绑算子，不支持用 inputParams、outputParams 按参数指定数据位置",
                        HttpStatus.BAD_REQUEST);
            }
            if (action.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE) {
                return testPropertyValueAction(action, detail, param);
            }
            if (action.getSubjectType() == ActionSubjectTypeEnum.LINK) {
                return testLinkAction(action, detail, param);
            }
            if (action.getSubjectType() == ActionSubjectTypeEnum.BASIC_FUNCTION) {
                return testBasicFunctionAction(action, detail, param);
            }
        }
        return testFunctionAction(action, detail, param,
                listParams == null ? listInputParamNames(action) : listParams);
    }

    /** 基础函数行为测试：按配置真实查一遍，选了实例的对象只查该实例；外部输入条件取 params 里的值 */
    private ActionTestResultVO testBasicFunctionAction(OntologyAction action, OntologyActionDetailVO detail,
                                                       ActionInvokeParam param) {
        // 请求按对象传了实例就能认出是哪个挂载对象；只给对象级主键时沿用合并前的单值口径
        var result = runBasicFunctionAction(detail, requestActionObject(action, param),
                param.getEntityPrimaryKey(), param.getEntityPrimaryKeys(), param.getWriteBackEntityPrimaryKey(),
                param.getParams());
        return testResult(action, "行为执行成功").setResult(jsonResult(result));
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
        if (subjectType == ActionSubjectTypeEnum.BASIC_FUNCTION) {
            return runBasicFunctionAction(detail, param.getOntologyUniqueIdentifier(),
                    param.getEntityPrimaryKey(), param.getEntityPrimaryKeys(), null, externalParams);
        }
        throw new BusinessException("该行为需要绑定算子后才能执行：" + action.getApi(), HttpStatus.BAD_REQUEST);
    }

    /**
     * 执行基础函数行为：按配置里的对象逐个查询，选了实例的对象只查该实例，其余按全量查。
     * 行为配了结果回写时，本次结果按回写目标对象的实例主键落库：优先用 writeBackEntityPrimaryKey（只定落点、不收窄查询），
     * 没给再沿用按对象传的那份里目标对象的键。
     *
     * @param requestObject             请求指定的对象id，用于把单个实例主键归到正确的对象上；多对象查询且没给对象级主键时要按对象传
     * @param entityPrimaryKey          对象级的实例主键，归属对象就是 requestObject（只有一个查询对象时不传对象id 也算给它）
     * @param primaryKeyByObject        按对象给的实例主键，优先于 entityPrimaryKey；回写目标对象不是查询对象时靠它给实例
     * @param writeBackEntityPrimaryKey 单独指定的回写实例主键；不参与查询收窄
     * @param externalParams            过滤条件里外部输入的参数名 -&gt; 值；没声明外部输入条件时不看这份值
     */
    private String runBasicFunctionAction(OntologyActionDetailVO detail, String requestObject,
                                          Object entityPrimaryKey, Map<String, Object> primaryKeyByObject,
                                          Object writeBackEntityPrimaryKey, Map<String, Object> externalParams) {
        var config = detail.getBasicFunction();
        PreconditionUtils.checkNotNull(config, "基础函数行为没有查询配置：" + detail.getActionApi(), HttpStatus.BAD_REQUEST);
        // 没配回写还传落点实例就是配了不生效，直接拒掉，别让调用方以为数据落库了
        PreconditionUtils.checkArgument(writeBackEntityPrimaryKey == null
                        || basicFunctionActionExecutor.writeBackTargetIdentifier(config) != null,
                "行为没有配置结果回写，不需要传 writeBackEntityPrimaryKey：" + detail.getActionApi(),
                HttpStatus.BAD_REQUEST);
        var targets = new LinkedHashMap<String, Object>();
        var identifiers = basicFunctionActionExecutor.configOntologyIdentifiers(config);
        var writeBackTarget = basicFunctionActionExecutor.writeBackTargetIdentifier(config);
        // 按对象给的实例主键原样收下：查询只认自己那几个对象的键，回写目标对象不在查询对象里时要从这儿拿它的实例
        MapUtils.emptyIfNull(primaryKeyByObject).forEach((objectId, primaryKey) -> {
            if (primaryKey != null) {
                targets.put(objectId, primaryKey);
            }
        });
        if (entityPrimaryKey == null) {
            return basicFunctionActionExecutor.run(config, targets, writeBackEntityPrimaryKey, externalParams);
        }
        if (identifiers.contains(requestObject)) {
            targets.putIfAbsent(requestObject, entityPrimaryKey);
        } else if (identifiers.size() == 1) {
            targets.putIfAbsent(identifiers.get(0), entityPrimaryKey);
        } else if (StringUtils.equals(requestObject, writeBackTarget)) {
            // 对象级主键给的不是查询对象，而是这次回写的落点对象：收下给回写用，不参与查询收窄
            targets.putIfAbsent(requestObject, entityPrimaryKey);
        } else {
            // 多对象时一个主键说不清查的是谁，让调用方按对象传，别默默查错对象
            throw new BusinessException("基础函数行为绑了多个对象，按实例查询时要按对象传实例主键"
                    + "（entityPrimaryKeys：对象id -> 实例主键）：" + detail.getActionApi(), HttpStatus.BAD_REQUEST);
        }
        return basicFunctionActionExecutor.run(config, targets, writeBackEntityPrimaryKey, externalParams);
    }

    /** 行为测试的入参形状与执行不同，这里把测试给的键包成执行用的单实体参数 */
    private EntityActionExecuteParam baseExecuteParam(OntologyAction action, Object entityPrimaryKey,
                                                      Object dataPrimaryKey, Object linkedEntityPrimaryKey) {
        return EntityActionExecuteParam.builder()
                .actionApi(action.getApi())
                .ontologySpaceId(action.getOntologySpaceId())
                // 属性值行为只作用在一个对象上；关系行为的两端在测试体里直接成对给出，不靠这里定侧
                .ontologyUniqueIdentifier(singleActionObject(action))
                .entityPrimaryKey(entityPrimaryKey)
                .dataPrimaryKey(dataPrimaryKey)
                .linkedEntityPrimaryKey(linkedEntityPrimaryKey)
                .build();
    }

    /** 属性值行为测试：对目标存储分组对应的物理表真实增删改一行 */
    private ActionTestResultVO testPropertyValueAction(OntologyAction action, OntologyActionDetailVO detail, ActionInvokeParam param) {
        var executeParam = baseExecuteParam(action, param.getEntityPrimaryKey(), param.getDataPrimaryKey(), null);
        // 挂了多个对象时请求里的实例能认出是哪个挂载对象；绑定行里有对象id时仍以绑定行为准
        executeParam.setOntologyUniqueIdentifier(requestActionObject(action, param));
        return testResult(action, runPropertyValueAction(action, detail.getMappingIns(), executeParam,
                withParamOverrides(param.getParams(), param.getParamValueOverrides())));
    }

    /**
     * 节点上绑定了取值来源的入参覆盖请求给的同名值。
     * <p>属性值行为不绑算子，取值直接落在这份外部输入上，没有 {@code resolveInputValue} 那道口子可以覆盖。</p>
     */
    private Map<String, Object> withParamOverrides(Map<String, Object> params, Map<String, Object> overrides) {
        if (MapUtils.isEmpty(overrides)) {
            return params;
        }
        Map<String, Object> merged = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        merged.putAll(overrides);
        return merged;
    }

    /**
     * 属性值行为作用的对象：绑定行里有对象id就以它为准，其次执行请求指定的对象，
     * 最后才用合并列表里的唯一对象（列表是绑定汇总，可能比实际写回的范围大）。
     */
    private String propertyValueObject(OntologyAction action, List<ActionParamMappingVO> mappings,
                                       EntityActionExecuteParam param) {
        if (CollectionUtils.isNotEmpty(mappings)) {
            for (var mapping : mappings) {
                if (StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())) {
                    return mapping.getOntologyUniqueIdentifier();
                }
            }
        }
        if (StringUtils.isNotEmpty(param.getOntologyUniqueIdentifier())) {
            return param.getOntologyUniqueIdentifier();
        }
        return singleActionObject(action);
    }

    /** 属性值行为执行体：对 action 的存储分组按基础操作增删改一行 */
    private String runPropertyValueAction(OntologyAction action, List<ActionParamMappingVO> mappings,
                                          EntityActionExecuteParam param, Map<String, Object> externalParams) {
        var ontologyIdentifier = propertyValueObject(action, mappings, param);
        PreconditionUtils.checkNotNull(ontologyIdentifier,
                "无法确定属性值行为作用的对象，请在绑定行或执行请求里指定对象：" + action.getApi(), HttpStatus.BAD_REQUEST);
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

    /** 固定值按完整 JSON 字面量解析，整串不是合法 JSON 时按原文本返回 */
    private Object parseFixedValue(String sourceConfig) {
        if (StringUtils.isEmpty(sourceConfig)) {
            return null;
        }
        try {
            return fixedValueMapper.readValue(sourceConfig, Object.class);
        } catch (Exception e) {
            return sourceConfig;
        }
    }

    /** 实例关系行为测试：真实写入/改状态/失效图库的一条边；两端实例由 entityPrimaryKeys 的 from/to 给出 */
    private ActionTestResultVO testLinkAction(OntologyAction action, OntologyActionDetailVO detail, ActionInvokeParam param) {
        var linkMapping = detail.getLinkMapping();
        PreconditionUtils.checkArgument(linkMapping != null, "行为没有绑定关系，无法执行", HttpStatus.BAD_REQUEST);
        var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkMapping.getOntologyLinkUniqIdentifier()));
        PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + linkMapping.getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
        var endpoints = resolveTestLinkEndpoints(param, action, linkGroup);
        return testResult(action, runLinkAction(action, linkMapping, endpoints.fromPrimaryKey, endpoints.toPrimaryKey));
    }

    /**
     * 关系测试的本端对象：先看调用方给的实例落在哪个对象上，看不出来再退回挂载对象。
     * <p>按对象 id 传了关系两端的实例就是本端；否则看挂载对象里哪一端在关系上，
     * 只有一端能对上时就是它，两端都挂了（关系行为同时读写两端）时按 From 处理。</p>
     */
    private String ownTestLinkObject(OntologyAction action, OntologyLinkGroup linkGroup, ActionInvokeParam param) {
        var fromObject = linkGroup.getOntologyUniqueIdentifierFrom();
        var toObject = linkGroup.getOntologyUniqueIdentifierTo();
        if (param.getEntityPrimaryKeys() != null) {
            for (var objectId : param.getEntityPrimaryKeys().keySet()) {
                if (StringUtils.equals(objectId, fromObject) || StringUtils.equals(objectId, toObject)) {
                    return objectId;
                }
            }
        }
        var identifiers = action.getOntologyUniqueIdentifier();
        if (identifiers != null) {
            var ends = identifiers.stream()
                    .filter(id -> StringUtils.equals(id, fromObject) || StringUtils.equals(id, toObject))
                    .distinct().collect(Collectors.toList());
            if (ends.size() == 1) {
                return ends.get(0);
            }
        }
        return fromObject;
    }

    /**
     * 关系测试的两端实例：entityPrimaryKeys 里的保留键 from、to 表示关系两端，与按对象 id 传的属性入参实例互不混用。
     * <p>没传 from/to 时兼容旧写法：按对象 id 选实例，entityPrimaryKey/linkEntityPrimaryKey 补单端。
     * 缺任一端直接报错，不再退化成遍历全部对端。</p>
     */
    private TestLinkEndpoints resolveTestLinkEndpoints(ActionInvokeParam param, OntologyAction action,
                                                      OntologyLinkGroup linkGroup) {
        var fromObject = linkGroup.getOntologyUniqueIdentifierFrom();
        var toObject = linkGroup.getOntologyUniqueIdentifierTo();
        var selfRelation = StringUtils.equals(fromObject, toObject);
        var objectKeys = new LinkedHashMap<String, Object>();
        Object fromPrimaryKey = null;
        Object toPrimaryKey = null;
        if (param.getEntityPrimaryKeys() != null) {
            fromPrimaryKey = param.getEntityPrimaryKeys().get(TEST_LINK_FROM_KEY);
            toPrimaryKey = param.getEntityPrimaryKeys().get(TEST_LINK_TO_KEY);
            param.getEntityPrimaryKeys().forEach((objectId, primaryKey) -> {
                if (!TEST_LINK_FROM_KEY.equals(objectId) && !TEST_LINK_TO_KEY.equals(objectId)
                        && StringUtils.isNotEmpty(objectId) && primaryKey != null) {
                    objectKeys.put(objectId, primaryKey);
                }
            });
        }
        if (fromPrimaryKey != null || toPrimaryKey != null) {
            if (fromPrimaryKey == null || toPrimaryKey == null) {
                throw new BusinessException("关系两端实例必须成对传：entityPrimaryKeys 里的 from 和 to 都要给",
                        HttpStatus.BAD_REQUEST);
            }
            // 自关系两端是同一对象，map 里保留本端，对端由执行器按 linkEntityPrimaryKey 遍历
            objectKeys.put(fromObject, fromPrimaryKey);
            if (!selfRelation) {
                objectKeys.put(toObject, toPrimaryKey);
            }
            return new TestLinkEndpoints(fromPrimaryKey, toPrimaryKey, objectKeys);
        }

        var ownObject = ownTestLinkObject(action, linkGroup, param);
        var counterpartObject = entityService.counterpartOntology(linkGroup, ownObject);
        var selfPrimaryKey = objectKeys.get(ownObject) != null ? objectKeys.get(ownObject) : param.getEntityPrimaryKey();
        // 自关系时 map 里同一个 key 已经是本端，对端只能取 linkEntityPrimaryKey
        var counterpartPrimaryKey = selfRelation
                ? param.getLinkEntityPrimaryKey()
                : (objectKeys.get(counterpartObject) != null
                ? objectKeys.get(counterpartObject) : param.getLinkEntityPrimaryKey());
        var selfIsFrom = StringUtils.equals(ownObject, fromObject);
        fromPrimaryKey = selfIsFrom ? selfPrimaryKey : counterpartPrimaryKey;
        toPrimaryKey = selfIsFrom ? counterpartPrimaryKey : selfPrimaryKey;
        var missingEnds = new ArrayList<String>();
        if (fromPrimaryKey == null) {
            missingEnds.add("from（From 对象[" + fromObject + "]）");
        }
        if (toPrimaryKey == null) {
            missingEnds.add("to（To 对象[" + toObject + "]）");
        }
        if (!missingEnds.isEmpty()) {
            throw new BusinessException("行为测试必须提供关系两端实例，缺少：" + String.join("、", missingEnds)
                    + "；请在 entityPrimaryKeys 里用 from、to 传两端实例主键", HttpStatus.BAD_REQUEST);
        }
        objectKeys.put(fromObject, fromPrimaryKey);
        if (!selfRelation) {
            objectKeys.put(toObject, toPrimaryKey);
        }
        return new TestLinkEndpoints(fromPrimaryKey, toPrimaryKey, objectKeys);
    }

    /** 关系测试归一后的两端实例；自关系时两端是同一对象 id，仍按角色区分 */
    private static final class TestLinkEndpoints {
        private final Object fromPrimaryKey;
        private final Object toPrimaryKey;
        private final Map<String, Object> objectPrimaryKeys;

        private TestLinkEndpoints(Object fromPrimaryKey, Object toPrimaryKey,
                                  Map<String, Object> objectPrimaryKeys) {
            this.fromPrimaryKey = fromPrimaryKey;
            this.toPrimaryKey = toPrimaryKey;
            this.objectPrimaryKeys = objectPrimaryKeys;
        }
    }

    /** 实例关系行为执行体：请求对象是本端，主键落在关系的那一端 */
    private String runLinkAction(OntologyAction action, ActionLinkMappingParam linkMapping, EntityActionExecuteParam param) {
        PreconditionUtils.checkArgument(linkMapping != null, "行为没有绑定关系，无法执行", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getLinkedEntityPrimaryKey() != null, "实例关系行为必须提供对端实体主键", HttpStatus.BAD_REQUEST);
        var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkMapping.getOntologyLinkUniqIdentifier()));
        PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + linkMapping.getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
        // 请求对象就是本端；没传或指向 To 之外的对象时按 From 侧处理（自关系与旧请求都落在默认方向）
        var selfIsFrom = !StringUtils.equals(param.getOntologyUniqueIdentifier(), linkGroup.getOntologyUniqueIdentifierTo());
        var primaryKeyFrom = selfIsFrom ? param.getEntityPrimaryKey() : param.getLinkedEntityPrimaryKey();
        var primaryKeyTo = selfIsFrom ? param.getLinkedEntityPrimaryKey() : param.getEntityPrimaryKey();
        return runLinkAction(action, linkMapping, primaryKeyFrom, primaryKeyTo);
    }

    /** 关系执行体：两端实例显式给出，方向由调用方决定 */
    private String runLinkAction(OntologyAction action, ActionLinkMappingParam linkMapping,
                                 Object primaryKeyFrom, Object primaryKeyTo) {
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
    private ActionTestResultVO testFunctionAction(OntologyAction action, OntologyActionDetailVO detail,
                                                  ActionInvokeParam param, Set<String> listParams) {
        // 行为可以挂载在多个对象上：请求只给了一个挂载对象的实例时它就是请求对象，
        // 按多个对象都给了实例时只能靠入参/出参各自给的数据位置
        var ownObject = requestActionObject(action, param);
        var singleInputTargets = new LinkedHashMap<String, ActionInstanceTargetParam>();
        var elementInputTargets = new LinkedHashMap<String, List<ActionInstanceTargetParam>>();
        resolveInputTargets(param.getInputParams(), listParams, param.getInputFieldParams(),
                singleInputTargets, elementInputTargets);
        var executeParamBuilder = EntityActionExecuteParam.builder()
                .actionApi(action.getApi())
                .ontologySpaceId(action.getOntologySpaceId())
                .ontologyUniqueIdentifier(ownObject)
                .entityPrimaryKey(param.getEntityPrimaryKey())
                .entityPrimaryKeys(param.getEntityPrimaryKeys())
                // 按对象方向给的数据位置：树执行的树级/节点级默认行主键走这里，对外测试请求不带
                .inputEntities(param.getInputEntities())
                .outputEntities(param.getOutputEntities())
                // 对象模式测试配的子表读取规则：入参名 -> 对象 -> 子表 -> 行数与排序，键已在入口按绑定行归位
                .listReadRules(param.getResolvedReadRules())
                // 按算子入参/出参指定的数据位置：入参读哪个实例/哪一行、出参写到哪个实例/哪一行
                .inputParams(singleInputTargets.isEmpty() ? null : singleInputTargets)
                .inputElementParams(elementInputTargets.isEmpty() ? null : elementInputTargets)
                // 按子级字段给的数据位置：各子级按自己的实例读，未给行按默认行数
                .inputFieldParams(param.getInputFieldParams())
                .outputParams(param.getOutputParams());
        // 涉及关系时两端实例都由调用方给：测试只跑这一对，不遍历关系下的全部实体
        if (detail.getLinkMapping() != null) {
            var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getUniqueIdentifier, detail.getLinkMapping().getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + detail.getLinkMapping().getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
            var endpoints = resolveTestLinkEndpoints(param, action, linkGroup);
            ownObject = ownTestLinkObject(action, linkGroup, param);
            // 对端是关系的另一端，不是固定的 To：本端可能落在 To 侧
            var counterpartObject = entityService.counterpartOntology(linkGroup, ownObject);
            // 自关系两端是同一对象，map 只能留一端，对端取 to
            var counterpartPrimaryKey = StringUtils.equals(linkGroup.getOntologyUniqueIdentifierFrom(), linkGroup.getOntologyUniqueIdentifierTo())
                    ? endpoints.toPrimaryKey
                    : (StringUtils.equals(counterpartObject, linkGroup.getOntologyUniqueIdentifierFrom())
                    ? endpoints.fromPrimaryKey : endpoints.toPrimaryKey);
            executeParamBuilder.ontologyUniqueIdentifier(ownObject)
                    .entityPrimaryKeys(endpoints.objectPrimaryKeys)
                    .linkedOntologyUniqueIdentifier(counterpartObject)
                    .linkedEntityPrimaryKey(counterpartPrimaryKey);
        }
        var executeParam = executeParamBuilder.build();

        var contextInfoDTO = entityService.initActionContextInfoDTO(executeParam);
        contextInfoDTO.setExternalParams(param.getParams());
        contextInfoDTO.setParamValueOverrides(param.getParamValueOverrides());
        // 行为树节点会传回填容器，执行侧把本次真正传给算子的入参写进去；对外测试不带
        contextInfoDTO.setResolvedInputs(param.getResolvedInputs());
        var result = entityService.executeEntityAction(executeParam, contextInfoDTO, entityService.getLinkedEntities(contextInfoDTO));
        // 出参可能是 WRITE_PROPERTY 落库，也可能只是 RESULT_ONLY 返回，提示不描述落库
        return testResult(action, "行为执行成功").setResult(jsonResult(result));
    }

    private ActionTestResultVO testResult(OntologyAction action, String message) {
        return ActionTestResultVO.builder()
                .actionApi(action.getApi())
                .message(message)
                .build();
    }

    /**
     * 执行结果是 JSON 文本时对外返回结构化对象：前端直接展示，不用再解析一层，也不会满屏转义。
     * 不是 JSON 的（提示文本）按文本返回，空串当没有结果。
     * <p>算子类行为的结果是一层字符串数组（每次算子调用一个返回体），数组元素同样解析成对象；
     * 只拆这一层，返回体里的字符串字段原样保留。</p>
     */
    private Object jsonResult(String result) {
        if (StringUtils.isEmpty(result)) {
            return null;
        }
        try {
            var parsed = jsonMapper.readValue(result, Object.class);
            if (parsed instanceof List<?> items) {
                return items.stream().map(this::payloadOrText).collect(Collectors.toList());
            }
            return parsed;
        } catch (Exception e) {
            return result;
        }
    }

    /** 返回体文本是 JSON 就转成对象，不是就保留文本；失败位置是空串，原样留着 */
    private Object payloadOrText(Object item) {
        if (!(item instanceof CharSequence text) || StringUtils.isBlank(text.toString())) {
            return item;
        }
        try {
            return jsonMapper.readValue(text.toString(), Object.class);
        } catch (Exception e) {
            return item;
        }
    }

    /** 结构化结果转回 JSON 文本：行为树按文本解析返回值（对端结果数组、出参取值路径） */
    private String resultText(Object result) {
        if (result == null || result instanceof String) {
            return (String) result;
        }
        try {
            return jsonMapper.writeValueAsString(result);
        } catch (Exception e) {
            return String.valueOf(result);
        }
    }

    /**
     * 把请求里的数据位置拆给执行侧：非 LIST 入参一次只有一个位置，LIST 入参按元素给，
     * 同一份列表的元素可以来自不同对象、不同实例（元素位置各自带 ontologyIdentifier、entityPrimaryKey）。
     * <p>批量测试到达这一层时每个入参已经只剩一组取值；二维候选还没展开的说明调用时机不对，直接拒。</p>
     */
    private void resolveInputTargets(Map<String, ActionInputPositionsParam> targets, Set<String> listParams,
                                     Map<String, Map<String, ActionInstanceTargetParam>> fieldTargets,
                                     Map<String, ActionInstanceTargetParam> single,
                                     Map<String, List<ActionInstanceTargetParam>> elements) {
        if (MapUtils.isEmpty(targets)) {
            return;
        }
        targets.forEach((paramName, shape) -> {
            if (shape == null) {
                return;
            }
            // 按子级给位置的入参走子级级读取，不再按入参/元素定位
            if (MapUtils.isNotEmpty(fieldTargets) && fieldTargets.containsKey(paramName)) {
                return;
            }
            var label = "入参[" + paramName + "]";
            PreconditionUtils.checkArgument(!shape.isEmpty(),
                    label + "的数据位置数组不能为空", HttpStatus.BAD_REQUEST);
            var listParam = listParams.contains(paramName);
            if (shape.isCandidates()) {
                PreconditionUtils.checkArgument(listParam,
                        label + "不是列表类型的入参，批量候选请写一维数组（每个元素是一个候选值）",
                        HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(shape.getCandidates().size() == 1,
                        label + "一次执行只能给一份列表，多个候选要先按 inputMode 展开成多组",
                        HttpStatus.BAD_REQUEST);
                var candidate = shape.getCandidates().get(0);
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(candidate),
                        label + "的候选值不能是空列表", HttpStatus.BAD_REQUEST);
                elements.put(paramName, listElementTargets(label, candidate));
                return;
            }
            var positions = shape.getPositions();
            if (listParam) {
                // 只给一个实例、没给行：整份列表按该实例的默认口径读（子表最近 10 行），不必逐元素点名
                if (positions.size() == 1 && MapUtils.isEmpty(positions.get(0).getDataPrimaryKeys())) {
                    checkInputTargetElement(label, positions.get(0));
                    single.put(paramName, positions.get(0));
                    return;
                }
                elements.put(paramName, listElementTargets(label, positions));
                return;
            }
            PreconditionUtils.checkArgument(positions.size() == 1,
                    label + "一次只能指定一个数据位置，数组里放一个元素即可", HttpStatus.BAD_REQUEST);
            checkInputTargetElement(label, positions.get(0));
            single.put(paramName, positions.get(0));
        });
    }

    /**
     * LIST 入参的元素位置：一个元素就是列表的一项，各自带对象、实例和分组行。
     * <p>分组行主键写成数组时按顺序展开成多个元素（同一对象同一实例）；同一行不能重复选，
     * 否则会拼出两个一样的元素。只有一个元素且没给分组行时按该实例的默认口径读（最近 10 行）。</p>
     */
    private List<ActionInstanceTargetParam> listElementTargets(String label,
                                                               List<ActionInstanceTargetParam> positions) {
        var elements = new ArrayList<ActionInstanceTargetParam>();
        Set<String> seenRows = new LinkedHashSet<>();
        for (var position : positions) {
            checkInputTargetElement(label, position, true);
            var groups = listTargetGroups(position.getDataPrimaryKeys());
            if (groups.isEmpty()) {
                elements.add(copyParamTarget(position));
                continue;
            }
            var rowsByGroup = new LinkedHashMap<String, List<Object>>();
            var rowCounts = new LinkedHashSet<Integer>();
            groups.forEach(group -> {
                var rows = expandRowKeys(position.getDataPrimaryKeys().get(group));
                rowsByGroup.put(group, rows);
                rowCounts.add(rows.size());
            });
            PreconditionUtils.checkArgument(rowCounts.size() == 1,
                    label + " 一个元素就是列表的一项：各分组给的行主键个数必须一致（按数组下标拼成同一个元素），当前是 "
                            + rowCounts + " 行", HttpStatus.BAD_REQUEST);
            int rowCount = rowsByGroup.values().iterator().next().size();
            for (int index = 0; index < rowCount; index++) {
                var dataPrimaryKeys = new LinkedHashMap<String, Object>();
                for (var entry : rowsByGroup.entrySet()) {
                    dataPrimaryKeys.put(entry.getKey(), entry.getValue().get(index));
                }
                var rowKey = elementRowKey(position, dataPrimaryKeys);
                PreconditionUtils.checkArgument(seenRows.add(rowKey),
                        label + " 里有两个元素选了同一行（" + rowKey + "），同一行只能拼成一个元素，请去掉重复的位置",
                        HttpStatus.BAD_REQUEST);
                var element = copyParamTarget(position);
                element.setDataPrimaryKeys(dataPrimaryKeys);
                elements.add(element);
            }
        }
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(elements),
                label + "的数据位置数组不能为空", HttpStatus.BAD_REQUEST);
        return elements;
    }

    /** 元素的唯一行标识：没给对象时只按实例 + 分组行，给了对象时带上对象，跨对象同号行不算重复 */
    private static String elementRowKey(ActionInstanceTargetParam position, Map<String, Object> dataPrimaryKeys) {
        return StringUtils.defaultString(position.getOntologyIdentifier()) + "|"
                + position.getEntityPrimaryKey() + "|" + dataPrimaryKeys;
    }

    /** 分组行主键可能是一个候选值的多行（数组按顺序就是列表的多个元素），单个值仍是一行 */
    private static List<Object> expandRowKeys(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>(List.of(value));
    }

    /** 一个位置里真正指定了行主键的存储分组（空分组名与 null 行值不算） */
    private static Set<String> listTargetGroups(Map<String, Object> dataPrimaryKeys) {
        if (MapUtils.isEmpty(dataPrimaryKeys)) {
            return Set.of();
        }
        return dataPrimaryKeys.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getKey()) && entry.getValue() != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 行为绑定的算子入参里 LIST 类型的参数名：这些入参的一维数据位置数组是列表元素，不是批量测试的候选值。
     * 行为不存在、不绑算子或查不到算子定义时返回空。
     */
    private Set<String> listInputParamNames(ActionInvokeParam param) {
        if (MapUtils.isEmpty(param.getInputParams())) {
            return Set.of();
        }
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, param.getOntologySpaceId()));
        return listInputParamNames(action);
    }

    private Set<String> listInputParamNames(OntologyAction action) {
        if (action == null || StringUtils.isEmpty(action.getFunctionApi())) {
            return Set.of();
        }
        var functionDetail = functionService.getFunctionDetailByApi(action.getOntologySpaceId(),
                action.getFunctionApi(), null);
        if (functionDetail == null || CollectionUtils.isEmpty(functionDetail.getParams())) {
            return Set.of();
        }
        return functionDetail.getParams().stream()
                .filter(p -> FunctionParamCategoryEnum.INPUT.equals(p.getCategory()))
                .filter(p -> p.paramTypeEnum() == FunctionParamTypeEnum.LIST)
                .map(FunctionParameterVO::getParamName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * 行为绑定行归位到算子入参所需的索引：入参定义、需要读实例的入参、字段名与路径的归属关系。
     * <p>数据位置和读取规则都要按同一套键写法认入参，所以这两处共用它，避免两套归位口径。</p>
     */
    private record InputParamKeyIndex(Map<String, FunctionParameterVO> defByName,
                                      Map<Long, FunctionParameterVO> defById,
                                      Set<Long> instanceParamIds,
                                      Set<Long> boundParamIds,
                                      Map<String, Set<String>> ownersByField,
                                      Map<String, Set<String>> pathsByOwner,
                                      List<ActionParamMappingVO> instanceRows) {
    }

    /** 按行为绑定的算子定义建索引；行为不存在、不绑算子（基础行为）、行为树或查不到定义时返回 null */
    private InputParamKeyIndex inputParamKeyIndex(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        if (action == null || action.getActionKind() == ActionKindEnum.BASE
                || action.getActionKind() == ActionKindEnum.TREE || StringUtils.isEmpty(action.getFunctionApi())) {
            return null;
        }
        var function = functionService.getFunctionDetailByApi(ontologySpaceId, action.getFunctionApi(), null);
        var detail = getActionByApi(ontologySpaceId, action.getApi());
        if (function == null || CollectionUtils.isEmpty(function.getParams())
                || detail == null || CollectionUtils.isEmpty(detail.getMappingIns())) {
            return null;
        }
        Map<Long, FunctionParameterVO> defById = new LinkedHashMap<>();
        Map<String, FunctionParameterVO> defByName = new LinkedHashMap<>();
        function.getParams().stream()
                .filter(p -> FunctionParamCategoryEnum.INPUT.equals(p.getCategory()))
                .forEach(p -> {
                    if (p.getParamId() != null) {
                        defById.putIfAbsent(p.getParamId(), p);
                    }
                    if (StringUtils.isNotBlank(p.getParamName())) {
                        defByName.putIfAbsent(p.getParamName(), p);
                    }
                });
        if (defByName.isEmpty()) {
            return null;
        }
        Set<Long> instanceParamIds = detail.getMappingIns().stream()
                .filter(OntologyActionServiceImpl::readNeedsInstance)
                .map(ActionParamMappingVO::getFunctionParamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> boundParamIds = detail.getMappingIns().stream()
                .map(ActionParamMappingVO::getFunctionParamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var instanceRows = detail.getMappingIns().stream()
                .filter(OntologyActionServiceImpl::readNeedsInstance)
                .filter(mapping -> mapping.getFunctionParamId() != null)
                .collect(Collectors.toList());
        Map<String, Set<String>> ownersByField = new LinkedHashMap<>();
        Map<String, Set<String>> pathsByOwner = new LinkedHashMap<>();
        for (var mapping : instanceRows) {
            if (StringUtils.isEmpty(mapping.getParamPath())) {
                continue;
            }
            var owner = defById.get(mapping.getFunctionParamId());
            if (owner == null || StringUtils.isBlank(owner.getParamName())) {
                continue;
            }
            addFieldOwner(ownersByField, mapping.getParameterName(), owner.getParamName());
            addFieldOwner(ownersByField, mapping.getParamPath(), owner.getParamName());
            addFieldOwner(ownersByField, StringUtils.substringAfterLast(mapping.getParamPath(), "."),
                    owner.getParamName());
            pathsByOwner.computeIfAbsent(owner.getParamName(), k -> new LinkedHashSet<>())
                    .add(mapping.getParamPath());
        }
        return new InputParamKeyIndex(defByName, defById, instanceParamIds, boundParamIds,
                ownersByField, pathsByOwner, instanceRows);
    }

    /**
     * 入参数据位置的键兼容「绑定行字段名」写法：前端按一条绑定行选位置时，LIST 的元素字段（longitude）、
     * 对象子字段（position.longitude）会各自成键，而执行侧只认顶层入参名（trajectories）。
     * 这里按绑定行反查所属顶层入参，把字段键合并回参数名：
     * <ul>
     *   <li>LIST 入参：各元素字段的位置按数组下标拼成同一个元素，合并成一次「同一实例多行」读取；</li>
     *   <li>非 LIST 的对象入参：各子字段必须指向同一个数据位置；</li>
     *   <li>外部输入、固定值来源的入参不读实例，按参数名或字段名给的位置没有数据可读，直接丢弃，
     *       值照旧从 params 与行为定义取。</li>
     * </ul>
     * 顶层参数名的写法原样保留，与改造前完全一致；同一字段名落在多个入参上时用「父参数名.字段路径」
     * （tracks.longitude、tracks.position.longitude）区分，仍对不上再要求改传顶层参数名。
     */
    private void normalizeInputParamKeys(ActionInvokeParam param) {
        var raw = param.getInputParams();
        if (MapUtils.isEmpty(raw) || StringUtils.isEmpty(param.getActionApi())) {
            return;
        }
        var index = inputParamKeyIndex(param.getOntologySpaceId(), param.getActionApi());
        if (index == null) {
            return;
        }
        var defById = index.defById();
        var defByName = index.defByName();
        var instanceParamIds = index.instanceParamIds();
        var boundParamIds = index.boundParamIds();
        // 字段名 -> 所属顶层入参名；同一字段名落在多个入参上时记下全部候选，用到时要求改传参数名
        var ownersByField = index.ownersByField();
        // 顶层入参名 -> 该入参各绑定行的字段路径，用来识别「父参数名.字段路径」写法
        var pathsByOwner = index.pathsByOwner();

        Map<String, ActionInputPositionsParam> normalized = new LinkedHashMap<>();
        Map<String, Map<String, ActionInstanceTargetParam>> fieldPositions = new LinkedHashMap<>();
        Set<String> directKeys = new LinkedHashSet<>();
        Map<String, Map<String, ActionInputPositionsParam>> fieldTargets = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            var key = StringUtils.trimToEmpty(entry.getKey());
            var def = defByName.get(key);
            var owners = ownersByField.get(key);
            if (def != null) {
                // 同名冲突：键既是顶层参数名、又是另一个入参的字段名，两种情况都能命中，不猜
                PreconditionUtils.checkArgument(owners == null
                                || owners.stream().allMatch(owner -> StringUtils.equals(owner, key)),
                        "inputParams 里的[" + key + "]同时是入参[" + key + "]的参数名和入参 " + owners
                                + " 的字段名，无法判断数据位置给哪个入参，请改传顶层入参名", HttpStatus.BAD_REQUEST);
                // 外部输入、固定值来源的入参不读实例，给位置没有数据可读：丢掉位置，值另有来源；
                // 完全没有绑定行的入参保留，让执行前的参数校验照旧报"没有取对象属性的行"
                if (def.getParamId() != null && boundParamIds.contains(def.getParamId())
                        && !instanceParamIds.contains(def.getParamId())) {
                    continue;
                }
                normalized.put(key, entry.getValue());
                directKeys.add(key);
                continue;
            }
            // 叶子名在多个入参上重复时用「父参数名.字段路径」指明归属：tracks.longitude、tracks.position.longitude
            var qualifiedOwner = resolveQualifiedOwner(key, pathsByOwner);
            if (qualifiedOwner != null
                    && (owners == null || owners.size() != 1 || !owners.contains(qualifiedOwner))) {
                var qualifiedDef = defByName.get(qualifiedOwner);
                if (qualifiedDef != null && qualifiedDef.getParamId() != null
                        && instanceParamIds.contains(qualifiedDef.getParamId())) {
                    fieldTargets.computeIfAbsent(qualifiedOwner, k -> new LinkedHashMap<>())
                            .put(key, entry.getValue());
                }
                continue;
            }
            if (owners == null || owners.isEmpty()) {
                // 认不出的键原样保留，交给执行前的参数名校验报错
                normalized.put(key, entry.getValue());
                continue;
            }
            PreconditionUtils.checkArgument(owners.size() == 1,
                    "inputParams 里的字段[" + key + "]同时属于入参 " + String.join("、", owners)
                            + "，无法判断数据位置给哪个入参，请直接按顶层入参名传", HttpStatus.BAD_REQUEST);
            var owner = owners.iterator().next();
            var ownerDef = defByName.get(owner);
            if (ownerDef == null || ownerDef.getParamId() == null
                    || !instanceParamIds.contains(ownerDef.getParamId())) {
                // 所属入参整包来自外部输入或固定值，字段位置没有可读的数据
                continue;
            }
            fieldTargets.computeIfAbsent(owner, k -> new LinkedHashMap<>()).put(key, entry.getValue());
        }
        var rowsByFieldKey = Map.<String, List<RuleRow>>of();
        for (var entry : fieldTargets.entrySet()) {
            var owner = entry.getKey();
            var byField = entry.getValue();
            PreconditionUtils.checkArgument(!directKeys.contains(owner),
                    "入参[" + owner + "]既按参数名又在字段 " + String.join("、", byField.keySet())
                            + " 上给了数据位置，同一入参只用一种写法", HttpStatus.BAD_REQUEST);
            var ownerDef = defByName.get(owner);
            var listParam = ownerDef != null && ownerDef.paramTypeEnum() == FunctionParamTypeEnum.LIST;
            if (listParam && eachFieldSinglePosition(byField)) {
                // 一个子级一个位置：子级各自定位，同一入参的子级可以来自不同对象、不同实例
                if (rowsByFieldKey.isEmpty()) {
                    rowsByFieldKey = listReadRuleRows(index);
                }
                fieldPositions.put(owner, fieldPositions(index, rowsByFieldKey, owner, byField));
                continue;
            }
            normalized.put(owner, mergeFieldPositions(owner, byField, listParam));
        }
        param.setInputParams(normalized.isEmpty() ? null : normalized);
        param.setInputFieldParams(fieldPositions.isEmpty() ? null : fieldPositions);
    }

    /** 每个子级只给一个位置：这组位置按子级各自读，不再按下标拼成元素 */
    private static boolean eachFieldSinglePosition(Map<String, ActionInputPositionsParam> byField) {
        return byField.values().stream().allMatch(shape -> shape != null && !shape.isCandidates()
                && shape.getPositions() != null && shape.getPositions().size() == 1);
    }

    /**
     * 子级位置归到属性：键按绑定行认（与子表读取规则同一套写法），一个子级可能命中多行绑定，
     * 这些行各自绑的属性都读这个位置；位置里点了对象时只留该对象的绑定行。
     */
    private Map<String, ActionInstanceTargetParam> fieldPositions(InputParamKeyIndex index,
                                                                  Map<String, List<RuleRow>> rowsByFieldKey,
                                                                  String owner,
                                                                  Map<String, ActionInputPositionsParam> byField) {
        var positions = new LinkedHashMap<String, ActionInstanceTargetParam>();
        byField.forEach((key, shape) -> {
            var label = "入参[" + owner + "]的子级[" + key + "]";
            var position = shape.getPositions().get(0);
            PreconditionUtils.checkNotNull(position, label + "数据位置不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(!(position.getEntityPrimaryKey() instanceof List<?>),
                    label + ".entityPrimaryKey 不能再嵌套数组，多组请在外层数组放多个位置", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(position.getEntityPrimaryKey() != null
                            || MapUtils.isNotEmpty(position.getDataPrimaryKeys()),
                    label + "没给实例主键，也没给分组行主键", HttpStatus.BAD_REQUEST);
            var rows = CollectionUtils.emptyIfNull(rowsByFieldKey.get(key)).stream()
                    .filter(row -> StringUtils.equals(owner, row.owner()))
                    .map(RuleRow::mapping)
                    .collect(Collectors.toList());
            PreconditionUtils.checkArgument(!rows.isEmpty(),
                    "inputParams 里的[" + key + "]不是入参[" + owner + "]绑定行的字段路径，无法定位要读的子表",
                    HttpStatus.BAD_REQUEST);
            // 读哪个对象由创建行为时的绑定行决定，测试时不再按位置点名
            PreconditionUtils.checkArgument(StringUtils.isBlank(position.getOntologyIdentifier()),
                    label + "不用传 ontologyIdentifier：读哪个对象由创建行为时的绑定行决定", HttpStatus.BAD_REQUEST);
            for (var row : rows) {
                PreconditionUtils.checkArgument(StringUtils.isNotBlank(row.getPropertyUniqueIdentifier()),
                        label + "的绑定行没有绑定对象属性，不能按子级指定数据位置", HttpStatus.BAD_REQUEST);
                positions.put(row.getPropertyUniqueIdentifier(), position);
            }
        });
        return positions;
    }

    /** 该绑定行取值是否要读实例：入参且来源为对象属性/对象/List；固定值与外部输入不读 */
    private static boolean readNeedsInstance(ActionParamMappingVO mapping) {
        var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
        return sourceType == ActionParamSourceEnum.PROPERTY || sourceType == ActionParamSourceEnum.OBJECT
                || sourceType == ActionParamSourceEnum.LIST;
    }

    private static void addFieldOwner(Map<String, Set<String>> ownersByField, String fieldName, String owner) {
        var name = StringUtils.trimToEmpty(fieldName);
        if (StringUtils.isEmpty(name) || StringUtils.equals(name, owner)) {
            return;
        }
        ownersByField.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(owner);
    }

    /**
     * 识别「父参数名.字段路径」写法：叶子名在多个入参上重复时，用它指明数据位置属于哪个入参。
     * 按点逐段尝试前缀，前缀是该行为的入参名、余下部分是该入参某条绑定行的字段路径才算命中。
     */
    private static String resolveQualifiedOwner(String key, Map<String, Set<String>> pathsByOwner) {
        int dot = key.indexOf('.');
        while (dot > 0 && dot < key.length() - 1) {
            var paths = pathsByOwner.get(key.substring(0, dot));
            if (paths != null && paths.contains(key.substring(dot + 1))) {
                return key.substring(0, dot);
            }
            dot = key.indexOf('.', dot + 1);
        }
        return null;
    }

    /**
     * 按绑定行字段名给的位置合并回所属入参：同一个元素的所有字段必须指向同一处
     * （同一对象、同一实例、同一分组行）。
     * <ul>
     *   <li>一维写法：LIST 入参的第 i 个位置就是第 i 个元素；非 LIST 入参的第 i 个位置是第 i 个批量候选。</li>
     *   <li>二维写法（只允许 LIST 入参）：外层第 c 个是第 c 个候选，内层第 i 个是该候选的第 i 个元素；
     *       所有字段的候选个数、每个候选里的元素个数都要一致。</li>
     * </ul>
     */
    private ActionInputPositionsParam mergeFieldPositions(
            String owner, Map<String, ActionInputPositionsParam> byField, boolean listParam) {
        var shapes = byField.entrySet().iterator();
        var first = shapes.next().getValue();
        boolean nested = first != null && first.isCandidates();
        for (var entry : byField.entrySet()) {
            var shape = entry.getValue();
            PreconditionUtils.checkNotNull(shape,
                    "入参[" + owner + "]的字段[" + entry.getKey() + "]数据位置不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(shape.isCandidates() == nested,
                    "入参[" + owner + "]的字段数据位置写法不一致：字段[" + entry.getKey() + "]和其他字段一个是二维候选、一个是一维列表，"
                            + "同一入参只用一种写法", HttpStatus.BAD_REQUEST);
        }
        if (!nested) {
            int count = -1;
            for (var entry : byField.entrySet()) {
                var positions = entry.getValue().getPositions();
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(positions),
                        "入参[" + owner + "]的字段[" + entry.getKey() + "]数据位置数组不能为空", HttpStatus.BAD_REQUEST);
                if (count < 0) {
                    count = positions.size();
                    continue;
                }
                PreconditionUtils.checkArgument(count == positions.size(),
                        "入参[" + owner + "]各字段的位置个数要一致：数组里第 i 个位置在所有字段上拼成同一个"
                                + (listParam ? "元素" : "数据位置") + "，字段[" + entry.getKey() + "]给了 "
                                + positions.size() + " 个，其他字段给了 " + count + " 个", HttpStatus.BAD_REQUEST);
            }
            var merged = new ArrayList<ActionInstanceTargetParam>(count);
            for (int index = 0; index < count; index++) {
                merged.add(mergeElementAt(owner, byField, index, 0, false));
            }
            return ActionInputPositionsParam.ofPositions(merged);
        }
        PreconditionUtils.checkArgument(listParam,
                "入参[" + owner + "]不是列表类型的入参，不能传二维数组：批量候选请写一维数组（每个元素是一个候选值）",
                HttpStatus.BAD_REQUEST);
        int candidateCount = -1;
        for (var entry : byField.entrySet()) {
            var candidates = entry.getValue().getCandidates();
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(candidates),
                    "入参[" + owner + "]的字段[" + entry.getKey() + "]候选数组不能为空", HttpStatus.BAD_REQUEST);
            if (candidateCount < 0) {
                candidateCount = candidates.size();
                continue;
            }
            PreconditionUtils.checkArgument(candidateCount == candidates.size(),
                    "入参[" + owner + "]各字段的候选个数要一致：外层第 c 个是同一个候选，字段[" + entry.getKey() + "]给了 "
                            + candidates.size() + " 个候选，其他字段给了 " + candidateCount + " 个", HttpStatus.BAD_REQUEST);
        }
        var mergedCandidates = new ArrayList<List<ActionInstanceTargetParam>>(candidateCount);
        for (int candidate = 0; candidate < candidateCount; candidate++) {
            int elementCount = -1;
            for (var entry : byField.entrySet()) {
                var elements = entry.getValue().getCandidates().get(candidate);
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(elements),
                        "入参[" + owner + "]的字段[" + entry.getKey() + "]第 " + (candidate + 1) + " 个候选不能是空列表",
                        HttpStatus.BAD_REQUEST);
                if (elementCount < 0) {
                    elementCount = elements.size();
                    continue;
                }
                PreconditionUtils.checkArgument(elementCount == elements.size(),
                        "入参[" + owner + "]第 " + (candidate + 1) + " 个候选里各字段的元素个数要一致，字段["
                                + entry.getKey() + "]给了 " + elements.size() + " 个元素，其他字段给了 " + elementCount + " 个",
                        HttpStatus.BAD_REQUEST);
            }
            var mergedElements = new ArrayList<ActionInstanceTargetParam>(elementCount);
            for (int index = 0; index < elementCount; index++) {
                mergedElements.add(mergeElementAt(owner, byField, index, candidate, true));
            }
            mergedCandidates.add(mergedElements);
        }
        return ActionInputPositionsParam.ofCandidates(mergedCandidates);
    }

    /**
     * 同一个元素/候选的各个字段位置合成一个位置：对象、实例必须一致，分组行不能互相矛盾。
     * 字段可以各自覆盖不同的存储分组，合起来是这个元素完整的行。
     */
    private ActionInstanceTargetParam mergeElementAt(String owner, Map<String, ActionInputPositionsParam> byField,
                                                     int index, int candidate, boolean nested) {
        String objectId = null;
        boolean objectFound = false;
        Object primaryKey = null;
        boolean primaryKeyFound = false;
        var rows = new LinkedHashMap<String, Object>();
        for (var entry : byField.entrySet()) {
            var label = "入参[" + owner + "]的字段[" + entry.getKey() + "]第 " + (index + 1) + " 个"
                    + (nested ? "元素（第 " + (candidate + 1) + " 个候选）" : "位置");
            var position = nested ? entry.getValue().getCandidates().get(candidate).get(index)
                    : entry.getValue().getPositions().get(index);
            checkInputTargetElement(label, position);
            if (StringUtils.isNotBlank(position.getOntologyIdentifier())) {
                if (!objectFound) {
                    objectId = position.getOntologyIdentifier();
                    objectFound = true;
                } else {
                    PreconditionUtils.checkArgument(Objects.equals(objectId, position.getOntologyIdentifier()),
                            label + " 指定的对象和同一元素的其他字段不一致：同一个元素只能读一个对象的数据",
                            HttpStatus.BAD_REQUEST);
                }
            }
            if (position.getEntityPrimaryKey() != null) {
                if (!primaryKeyFound) {
                    primaryKey = position.getEntityPrimaryKey();
                    primaryKeyFound = true;
                } else {
                    PreconditionUtils.checkArgument(Objects.equals(primaryKey, position.getEntityPrimaryKey()),
                            label + " 指定的实例和同一元素的其他字段不一致：同一个元素只能读一个实例上的行",
                            HttpStatus.BAD_REQUEST);
                }
            }
            if (MapUtils.isEmpty(position.getDataPrimaryKeys())) {
                continue;
            }
            for (var row : position.getDataPrimaryKeys().entrySet()) {
                if (StringUtils.isBlank(row.getKey()) || row.getValue() == null) {
                    continue;
                }
                var existing = rows.get(row.getKey());
                PreconditionUtils.checkArgument(existing == null || Objects.equals(existing, row.getValue()),
                        label + " 在分组[" + row.getKey() + "]上给的行和同一元素的其他字段不一致，"
                                + "同一个元素的所有字段必须读同一行", HttpStatus.BAD_REQUEST);
                rows.put(row.getKey(), row.getValue());
            }
        }
        var builder = ActionInstanceTargetParam.builder().ontologyIdentifier(objectId).entityPrimaryKey(primaryKey);
        if (!rows.isEmpty()) {
            builder.dataPrimaryKeys(rows);
        }
        return builder.build();
    }

    /**
     * 批量行为测试的取值轴：入参方向某个字段传了数组，就是这个字段的一组候选值。
     * <p>field 定位请求字段，key/subKey 定位字段里的具体一项，values 是校验过的标量候选值。</p>
     */
    private record TestAxis(String field, String key, String subKey, List<Object> values) {

        String label() {
            return axisLabel(field, key, subKey);
        }
    }

    /** 取值轴的显示名，批量结果里按它回显每组选中的取值 */
    private static String axisLabel(String field, String key, String subKey) {
        if (TEST_AXIS_FIELD_ENTITY_PRIMARY_KEYS.equals(field)) {
            // 关系两端在请求里就是保留键 from、to，不用再冠字段名
            return TEST_LINK_FROM_KEY.equals(key) || TEST_LINK_TO_KEY.equals(key)
                    ? key : field + "[" + key + "]";
        }
        if (subKey == null) {
            // 入参方向一个轴是一组完整数据位置；出参方向仍是单值，指到实例主键
            return TEST_AXIS_FIELD_INPUT_PARAMS.equals(field)
                    ? field + "[" + key + "]" : field + "[" + key + "].entityPrimaryKey";
        }
        return field + "[" + key + "].dataPrimaryKeys[" + subKey + "]";
    }

    /**
     * 收集入参方向的候选值：只有 entityPrimaryKeys（含关系两端 from、to）和 inputParams 参与。
     * <p>inputParams 一维数组里，非 LIST 入参的每个元素是一个批量候选（多个才成轴），
     * LIST 入参的元素是这一次执行的那份列表（各元素可以各自指定对象和实例，一次执行，不展开）；
     * 二维数组只在 LIST 入参上用，外层每个元素是一份完整的列表候选，多个候选才成轴。
     * params 不作为轴：LIST 类型入参的值本来就是数组，分不清是候选值还是参数本身。
     * 出参方向出现数组直接拦下，一次执行只能有一个写回位置。</p>
     */
    private List<TestAxis> collectTestAxes(ActionInvokeParam param, Set<String> listParams) {
        var axes = new ArrayList<TestAxis>();
        PreconditionUtils.checkArgument(!(param.getEntityPrimaryKey() instanceof List<?>),
                "entityPrimaryKey 不支持传数组，多组实例请改用 entityPrimaryKeys（关系两端用 from、to）或 inputParams",
                HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(!(param.getDataPrimaryKey() instanceof List<?>)
                        && !(param.getLinkEntityPrimaryKey() instanceof List<?>),
                "dataPrimaryKey、linkEntityPrimaryKey 不支持传数组，批量测试请用 entityPrimaryKeys 或 inputParams 传候选值",
                HttpStatus.BAD_REQUEST);
        checkOutputSideHasNoArray("outputParams", param.getOutputParams());
        if (param.getEntityPrimaryKeys() != null) {
            param.getEntityPrimaryKeys().forEach((objectId, value) ->
                    addTestAxis(axes, TEST_AXIS_FIELD_ENTITY_PRIMARY_KEYS, objectId, null, value));
        }
        addInputTargetAxes(axes, TEST_AXIS_FIELD_INPUT_PARAMS, param.getInputParams(), listParams);
        return axes;
    }

    /**
     * 入参方向的数据位置：一维数组里，LIST 入参是这次执行的列表元素（一次执行，不展开），
     * 非 LIST 入参每个元素是一个批量候选；二维数组只在 LIST 入参上用，外层一个元素是一组候选
     * （每个候选就是一份完整的列表），多个候选才展开成轴。
     */
    private void addInputTargetAxes(List<TestAxis> axes, String field,
                                    Map<String, ActionInputPositionsParam> targets,
                                    Set<String> listParams) {
        if (targets == null) {
            return;
        }
        targets.forEach((paramName, positions) -> {
            if (positions == null) {
                return;
            }
            var label = axisLabel(field, paramName, null);
            PreconditionUtils.checkArgument(!positions.isEmpty(), label + " 的数据位置数组不能为空", HttpStatus.BAD_REQUEST);
            var listParam = listParams.contains(paramName);
            if (positions.isCandidates()) {
                var candidates = positions.getCandidates();
                PreconditionUtils.checkArgument(listParam,
                        label + " 只有列表类型的入参能用二维数组给候选值，非 LIST 入参请用一维数组直接给候选值",
                        HttpStatus.BAD_REQUEST);
                for (var candidate : candidates) {
                    PreconditionUtils.checkArgument(!candidate.isEmpty(),
                            label + " 的一个候选值就是一份完整的列表元素，候选里不能是空数组", HttpStatus.BAD_REQUEST);
                    for (var target : candidate) {
                        checkInputTargetElement(label, target, true);
                    }
                }
                if (candidates.size() > 1) {
                    axes.add(new TestAxis(field, paramName, null, new ArrayList<>(candidates)));
                }
                return;
            }
            var points = positions.getPositions();
            for (var target : points) {
                checkInputTargetElement(label, target, listParam);
            }
            // LIST 入参的一维数组是同一份列表的元素，不是候选值，不参与展开
            if (!listParam && points.size() > 1) {
                axes.add(new TestAxis(field, paramName, null, new ArrayList<>(points)));
            }
        });
    }

    /** 一个数据位置元素自身必须是单值：数组只在外层表示多组候选值 */
    private void checkInputTargetElement(String label, ActionInstanceTargetParam target) {
        checkInputTargetElement(label, target, false);
    }

    /**
     * allowRowArrays：LIST 入参在批量展开时，一个候选值就是一个完整的列表，
     * 分组行主键可以写成数组，按数组顺序读多行，一个下标就是列表的一个元素。
     */
    private void checkInputTargetElement(String label, ActionInstanceTargetParam target, boolean allowRowArrays) {
        PreconditionUtils.checkNotNull(target, label + " 的数据位置数组里有 null", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(!(target.getEntityPrimaryKey() instanceof List<?>),
                label + ".entityPrimaryKey 不能再嵌套数组，一个元素就是一组数据位置，多组请在外层数组放多个元素",
                HttpStatus.BAD_REQUEST);
        if (target.getDataPrimaryKeys() == null) {
            return;
        }
        if (allowRowArrays) {
            checkListRowArrays(label, target);
            return;
        }
        target.getDataPrimaryKeys().forEach((group, value) -> PreconditionUtils.checkArgument(
                !(value instanceof List<?>),
                label + ".dataPrimaryKeys[" + group + "] 不能再嵌套数组，一个元素就是一组数据位置，多组请在外层数组放多个元素",
                HttpStatus.BAD_REQUEST));
    }

    /** 一个候选值里各分组读的行数必须一致：按数组下标拼元素，分组间行数不等就没有对应关系 */
    private void checkListRowArrays(String label, ActionInstanceTargetParam target) {
        var rowCounts = new LinkedHashSet<Integer>();
        target.getDataPrimaryKeys().forEach((group, value) -> {
            if (StringUtils.isBlank(group) || value == null) {
                return;
            }
            if (!(value instanceof List<?> rows)) {
                rowCounts.add(1);
                return;
            }
            var groupLabel = label + ".dataPrimaryKeys[" + group + "]";
            PreconditionUtils.checkArgument(!rows.isEmpty(), groupLabel + " 的行主键数组不能为空", HttpStatus.BAD_REQUEST);
            for (var row : rows) {
                PreconditionUtils.checkNotNull(row, groupLabel + " 的行主键里有 null", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(!(row instanceof List<?>),
                        groupLabel + " 的行主键只能是标量，不支持再嵌套数组", HttpStatus.BAD_REQUEST);
            }
            rowCounts.add(rows.size());
        });
        PreconditionUtils.checkArgument(rowCounts.size() <= 1,
                label + " 一个候选值就是一个完整的列表：各分组给的行主键个数必须一致（按数组下标拼成同一个元素），当前是 "
                        + rowCounts + " 行", HttpStatus.BAD_REQUEST);
    }

    private void checkOutputSideHasNoArray(String field, Map<String, ActionInstanceTargetParam> targets) {
        if (targets == null) {
            return;
        }
        targets.forEach((objectId, target) -> {
            if (target == null) {
                return;
            }
            rejectTestAxis(target.getEntityPrimaryKey(), axisLabel(field, objectId, null));
            if (target.getDataPrimaryKeys() != null) {
                target.getDataPrimaryKeys().forEach((group, value) ->
                        rejectTestAxis(value, axisLabel(field, objectId, group)));
            }
        });
    }

    private void rejectTestAxis(Object value, String label) {
        PreconditionUtils.checkArgument(!(value instanceof List<?>),
                label + " 不支持传数组：出参方向每组只能有一个写回位置", HttpStatus.BAD_REQUEST);
    }

    private void addTestAxis(List<TestAxis> axes, String field, String key, String subKey, Object value) {
        if (!(value instanceof List<?> items)) {
            return;
        }
        var label = axisLabel(field, key, subKey);
        PreconditionUtils.checkArgument(!items.isEmpty(), label + " 的候选值不能是空数组", HttpStatus.BAD_REQUEST);
        var values = new ArrayList<Object>(items.size());
        for (var item : items) {
            PreconditionUtils.checkNotNull(item, label + " 的候选值里有 null", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(!(item instanceof List<?>),
                    label + " 的候选值只能是标量，不支持嵌套数组", HttpStatus.BAD_REQUEST);
            values.add(item);
        }
        axes.add(new TestAxis(field, key, subKey, values));
    }

    /**
     * 展开批量组合：调用方没指定 inputMode 时的历史口径——入参方向的数据位置全部只选实例
     * （主存储分组的行就是实例本身）时按笛卡尔积组合；只要有数据位置选了非 main 分组的行，
     * 就改成按下标配对成组，第 i 组取每个轴的第 i 个候选值。
     * LIST 入参的多行是同一个列表的元素，不是候选值，不参与判断。
     */
    private List<List<Object>> expandTestCombos(ActionInvokeParam param, List<TestAxis> axes,
                                                Set<String> listParams) {
        return hasNonMainGroupTarget(param.getInputParams(), listParams)
                ? expandTestGroups(axes) : expandTestProduct(axes, true);
    }

    /**
     * 笛卡尔积展开，越靠后的取值轴变化越快。
     * capByMaxCombos=false 时不受 50 组上限约束（对象模式要把实例全部跑一遍）。
     */
    private List<List<Object>> expandTestProduct(List<TestAxis> axes, boolean capByMaxCombos) {
        var total = 1;
        for (var axis : axes) {
            total *= axis.values().size();
        }
        if (capByMaxCombos) {
            checkTestComboCount(total, axes);
        }
        var combos = new ArrayList<List<Object>>(total);
        var cursor = new int[axes.size()];
        for (int index = 0; index < total; index++) {
            var combo = new ArrayList<Object>(axes.size());
            for (int axisIndex = 0; axisIndex < axes.size(); axisIndex++) {
                combo.add(axes.get(axisIndex).values().get(cursor[axisIndex]));
            }
            combos.add(combo);
            for (int axisIndex = axes.size() - 1; axisIndex >= 0; axisIndex--) {
                if (++cursor[axisIndex] < axes.get(axisIndex).values().size()) {
                    break;
                }
                cursor[axisIndex] = 0;
            }
        }
        return combos;
    }

    /** 跨存储分组按下标配对：各轴候选值个数必须一致，多出来的位置没有配对对象，不能靠截断或补位凑组数 */
    private List<List<Object>> expandTestGroups(List<TestAxis> axes) {
        var total = axes.get(0).values().size();
        PreconditionUtils.checkArgument(axes.stream().allMatch(axis -> axis.values().size() == total),
                "inputMode=GROUP 时各取值轴按下标配对成组，候选值个数必须一致（"
                        + describeTestAxes(axes) + "），请补齐或减少候选值", HttpStatus.BAD_REQUEST);
        checkTestComboCount(total, axes);
        var combos = new ArrayList<List<Object>>(total);
        for (int index = 0; index < total; index++) {
            var combo = new ArrayList<Object>(axes.size());
            for (var axis : axes) {
                combo.add(axis.values().get(index));
            }
            combos.add(combo);
        }
        return combos;
    }

    private void checkTestComboCount(int total, List<TestAxis> axes) {
        PreconditionUtils.checkArgument(total <= MAX_TEST_COMBOS,
                "批量测试最多 " + MAX_TEST_COMBOS + " 组组合，当前候选值会展开出 " + total + " 组（"
                        + describeTestAxes(axes) + "），请减少候选值", HttpStatus.BAD_REQUEST);
    }

    /** 入参方向有没有数据位置选了非 main 分组的行：选了子表行就是跨分组，各取值轴不能再自由相乘；LIST 入参不算 */
    private static boolean hasNonMainGroupTarget(Map<String, ActionInputPositionsParam> targets,
                                                 Set<String> listParams) {
        return targets != null && targets.entrySet().stream()
                .filter(entry -> !listParams.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .map(ActionInputPositionsParam::getPositions)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(OntologyActionServiceImpl::hasNonMainGroupRow);
    }

    private static boolean hasNonMainGroupRow(ActionInstanceTargetParam target) {
        return MapUtils.emptyIfNull(target.getDataPrimaryKeys()).entrySet().stream()
                .anyMatch(entry -> entry.getValue() != null
                        && StringUtils.isNotBlank(entry.getKey())
                        && !MAIN_STORAGE_GROUP.equals(entry.getKey()));
    }

    private String describeTestAxes(List<TestAxis> axes) {
        return axes.stream()
                .map(axis -> axis.label() + "=" + axis.values().size())
                .collect(Collectors.joining("、"));
    }

    /**
     * 批量行为测试：逐组把标量化的请求交给单组测试，复用它的全部校验与执行链路。
     * <p>每组各自落库、各自记成败，一组失败不影响其余组合，也不回滚已经写进去的数据——
     * 单组测试本来就不加事务。</p>
     *
     * @param combos 已按 inputMode 展开好的各组取值
     * @param prefix 结果提示的前缀（如「对象模式」），为空时用默认提示
     */
    private ActionTestResultVO testActionCombos(ActionInvokeParam param, List<TestAxis> axes,
                                                List<List<Object>> combos, Set<String> listParams, String prefix) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, param.getOntologySpaceId()));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.BASE,
                "属性值/实例关系行为只操作单个实例，不支持批量测试", HttpStatus.BAD_REQUEST);
        var cases = new ArrayList<ActionTestCaseResultVO>(combos.size());
        var results = new ArrayList<Object>(combos.size());
        var failed = 0;
        for (int index = 0; index < combos.size(); index++) {
            var comboValues = combos.get(index);
            var single = copyTestParam(param);
            for (int axisIndex = 0; axisIndex < axes.size(); axisIndex++) {
                applyTestAxis(single, axes.get(axisIndex), comboValues.get(axisIndex));
            }
            Object result = null;
            String error = null;
            try {
                result = runTestAction(single, listParams).getResult();
                if (isBlankFunctionResult(result)) {
                    error = "算子没有返回结果，通常是算子内部报错后被执行链路吞掉，请看算子执行日志";
                }
            } catch (BusinessException e) {
                error = e.getMessage();
            } catch (Exception e) {
                log.error("批量行为测试单组执行失败，actionApi={}，组合={}", action.getApi(), comboValues, e);
                error = "执行异常：" + e.getMessage();
            }
            if (error != null) {
                result = null;
                failed++;
            }
            results.add(result);
            cases.add(ActionTestCaseResultVO.builder()
                    .seq(index + 1)
                    .combo(comboValuesMap(axes, comboValues))
                    .result(result)
                    .error(error)
                    .build());
        }
        var succeeded = combos.size() - failed;
        var message = (StringUtils.isEmpty(prefix) ? "" : prefix + "：")
                + "共 " + combos.size() + " 组组合，成功 " + succeeded + " / 失败 " + failed;
        return testResult(action, message)
                .setResult(results)
                .setCases(cases);
    }

    private Map<String, Object> comboValuesMap(List<TestAxis> axes, List<Object> values) {
        var combo = new LinkedHashMap<String, Object>();
        for (int index = 0; index < axes.size(); index++) {
            combo.put(axes.get(index).label(), values.get(index));
        }
        return combo;
    }

    /**
     * 算子返回是否整组皆空：执行链路会把算子自己抛的异常吞成空字符串，
     * 批量时不显式判出来就成了「算子报错却报成功」。
     * <p>结果已经按 JSON 解析过：执行链路返回的是 JSON 字符串数组，解析后就是列表，失败位置是空串或 null。</p>
     */
    private boolean isBlankFunctionResult(Object result) {
        if (result == null) {
            return true;
        }
        if (result instanceof CharSequence text) {
            return StringUtils.isEmpty(text.toString());
        }
        if (result instanceof Collection<?> items) {
            return items.stream().allMatch(item -> item == null
                    || (item instanceof CharSequence text && StringUtils.isEmpty(text.toString())));
        }
        return false;
    }

    /** 复制一份测试请求，只把会被逐组覆盖的入参结构换成可变副本，原请求保持不动 */
    private ActionInvokeParam copyTestParam(ActionInvokeParam param) {
        return ActionInvokeParam.builder()
                .ontologySpaceId(param.getOntologySpaceId())
                .actionApi(param.getActionApi())
                .entityPrimaryKey(param.getEntityPrimaryKey())
                .entityPrimaryKeys(param.getEntityPrimaryKeys() == null
                        ? null : new LinkedHashMap<>(param.getEntityPrimaryKeys()))
                .inputEntities(param.getInputEntities())
                .outputEntities(param.getOutputEntities())
                .inputParams(copyTestTargets(param.getInputParams()))
                .inputFieldParams(param.getInputFieldParams())
                .outputParams(param.getOutputParams())
                .dataPrimaryKey(param.getDataPrimaryKey())
                .linkEntityPrimaryKey(param.getLinkEntityPrimaryKey())
                .params(param.getParams())
                .inputMode(param.getInputMode())
                .listReadRules(param.getListReadRules())
                .resolvedReadRules(param.getResolvedReadRules())
                .paramValueOverrides(param.getParamValueOverrides() == null
                        ? null : new LinkedHashMap<>(param.getParamValueOverrides()))
                // 解析容器每组独立：执行侧往里回填，共用一个容器会让后一组覆盖前一组
                .resolvedInputs(param.getResolvedInputs() == null ? null : new LinkedHashMap<>())
                .build();
    }

    private Map<String, ActionInputPositionsParam> copyTestTargets(
            Map<String, ActionInputPositionsParam> targets) {
        if (targets == null) {
            return null;
        }
        var copy = new LinkedHashMap<String, ActionInputPositionsParam>();
        targets.forEach((paramName, positions) -> copy.put(paramName, positions == null ? null
                : positions.isCandidates()
                ? ActionInputPositionsParam.ofCandidates(positions.getCandidates().stream()
                .map(candidate -> copyTestTargetList(candidate))
                .collect(Collectors.toCollection(ArrayList::new)))
                : ActionInputPositionsParam.ofPositions(copyTestTargetList(positions.getPositions()))));
        return copy;
    }

    private List<ActionInstanceTargetParam> copyTestTargetList(List<ActionInstanceTargetParam> positions) {
        if (positions == null) {
            return null;
        }
        return positions.stream()
                .map(target -> target == null ? null : copyParamTarget(target))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void applyTestAxis(ActionInvokeParam param, TestAxis axis, Object value) {
        switch (axis.field()) {
            case TEST_AXIS_FIELD_ENTITY_PRIMARY_KEYS -> param.getEntityPrimaryKeys().put(axis.key(), value);
            case TEST_AXIS_FIELD_INPUT_PARAMS -> applyTestTargetAxis(param.getInputParams(), axis, value);
            default -> throw new BusinessException("未知的批量测试取值轴：" + axis.label(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 把选中的候选值放回入参位置：非 LIST 入参的候选是一个数据位置，LIST 入参的候选是一份完整列表的元素，
     * 落成单次执行的写法（LIST 的候选是二维的一份列表，仍按二维写回）。
     */
    @SuppressWarnings("unchecked")
    private void applyTestTargetAxis(Map<String, ActionInputPositionsParam> targets, TestAxis axis, Object value) {
        if (value instanceof List<?> outer && !outer.isEmpty() && outer.get(0) instanceof List) {
            targets.put(axis.key(), ActionInputPositionsParam.ofCandidates(outer.stream()
                    .map(candidate -> copyTestTargetList((List<ActionInstanceTargetParam>) candidate))
                    .collect(Collectors.toCollection(ArrayList::new))));
            return;
        }
        var positions = value instanceof List<?> elements
                ? copyTestTargetList((List<ActionInstanceTargetParam>) elements)
                : new ArrayList<>(List.of(copyParamTarget((ActionInstanceTargetParam) value)));
        targets.put(axis.key(), ActionInputPositionsParam.ofPositions(positions));
    }

    /**
     * 调度接口只认定时和规则两类；MANUAL 是手动执行日志的类型，传进来会一条都不改地走完，直接拒掉。
     */
    /**
     * 编辑行为前置：仍有 START 调度绑定时拒绝。暂停后的调度保留绑定关系但不执行，编辑不再拦。
     * 行为 api 只在空间内唯一，两张调度表都有 action_api + ontology_space_id，按空间反查即可。
     */
    private void checkNoRunningScheduling(Integer ontologySpaceId, String actionApi, String message) {
        var taskWrapper = new LambdaQueryWrapper<ActionHandleTask>()
                .eq(ActionHandleTask::getActionApi, actionApi)
                .eq(ActionHandleTask::getStatus, ScheduleStatus.START);
        var ruleWrapper = new LambdaQueryWrapper<ActionHandleRule>()
                .eq(ActionHandleRule::getActionApi, actionApi)
                .eq(ActionHandleRule::getStatus, ScheduleStatus.START);
        if (ontologySpaceId != null) {
            taskWrapper.eq(ActionHandleTask::getOntologySpaceId, ontologySpaceId);
            ruleWrapper.eq(ActionHandleRule::getOntologySpaceId, ontologySpaceId);
        }
        boolean running = actionHandleTaskService.count(taskWrapper) > 0
                || actionHandleRuleService.count(ruleWrapper) > 0;
        PreconditionUtils.checkArgument(!running, message, HttpStatus.BAD_REQUEST);
    }

    /** 删除行为前置：调度无论启停都绑定着行为 api，存在任何一条即拒绝删除（先删调度） */
    private void checkNoSchedulingAtAll(Integer ontologySpaceId, String actionApi) {
        var taskWrapper = new LambdaQueryWrapper<ActionHandleTask>()
                .eq(ActionHandleTask::getActionApi, actionApi);
        var ruleWrapper = new LambdaQueryWrapper<ActionHandleRule>()
                .eq(ActionHandleRule::getActionApi, actionApi);
        if (ontologySpaceId != null) {
            taskWrapper.eq(ActionHandleTask::getOntologySpaceId, ontologySpaceId);
            ruleWrapper.eq(ActionHandleRule::getOntologySpaceId, ontologySpaceId);
        }
        long taskCount = actionHandleTaskService.count(taskWrapper);
        long ruleCount = actionHandleRuleService.count(ruleWrapper);
        PreconditionUtils.checkArgument(taskCount == 0 && ruleCount == 0,
                "该行为已配置行为调度，不能直接删除：定时调度 " + taskCount + " 条、规则调度 " + ruleCount
                        + " 条，请先在调度管理中删除对应调度", HttpStatus.BAD_REQUEST);
    }

    /**
     * 调度链路按 api 定位行为：行为 api 只在空间内唯一，调用方已尽量带上 spaceId 过滤；
     * 拿不到空间时同名 api 命中多条要显式报错定位，不再让 selectOne 抛多条异常。
     */
    private OntologyAction resolveScheduleAction(List<OntologyAction> candidates, String actionApi) {
        if (candidates.size() > 1) {
            throw new BusinessException("行为 " + actionApi + " 在多个对象空间同时存在，请传 spaceId 定位",
                    HttpStatus.BAD_REQUEST);
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private void checkSchedulingType(ActionSchedulingTypeEnum type) {
        PreconditionUtils.checkArgument(type == ActionSchedulingTypeEnum.TASK || type == ActionSchedulingTypeEnum.RULE,
                "调度类型只支持 TASK 定时和 RULE 规则", HttpStatus.BAD_REQUEST);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public Long createScheduling(ActionSchedulingCreateParam param) {
        //todo 目前只考虑定时调度
        var schedulingType = param.getType();
        checkSchedulingType(schedulingType);
        // 调度只能绑定本空间的行为：spaceId 必传，行为按 api + 空间过滤
        PreconditionUtils.checkNotNull(param.getSpaceId(), "请选择行为所属空间", HttpStatus.BAD_REQUEST);
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, param.getSpaceId()));
        PreconditionUtils.checkNotNull(action, "本空间内action不存在：" + param.getActionApi());
        // 属性值、实例关系行为没有算子，执行时会对对象下全部实例逐个落库，定时跑会造成重复数据，不支持调度
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(action.getFunctionApi()),
                "不绑定算子的行为不支持配置调度：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        var ontologyIdentifier = scheduleObject(action, param.getOntologyIdentifier());
        Long id = null;
        //允许同一个行为配置多个调度策略
        if (schedulingType.equals(ActionSchedulingTypeEnum.TASK)) {
            var taskParam = param.getTask();
            PreconditionUtils.checkNotNull(taskParam, "type=TASK 时 task 对象不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(taskParam.getTaskCronExpression()),
                    "type=TASK 时 task.taskCronExpression 不能为空", HttpStatus.BAD_REQUEST);
            var actionHandleTask = ActionHandleTask.builder()
                    .actionApi(action.getApi())
                    .ontologyIdentifier(ontologyIdentifier)
                    .ontologySpaceId(action.getOntologySpaceId())
                    .cron(taskParam.getTaskCronExpression())
                    .periodType(taskParam.getPeriodType())
                    .entityJson(taskParam.getEntityJson())
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
                                .ontologyUniqueIdentifier(ontologyIdentifier)
                                .scheduleId(id)
                                .build());
                actionHandleTaskService.updateById(actionHandleTask.setRemark(jonInfoId));
            } catch (Exception e) {
                log.error("createScheduling failed", e);
                throw new BusinessException("远程调用xxl-job创建jobinfo失败");
            }
        } else if (schedulingType.equals(ActionSchedulingTypeEnum.RULE)) {
            var ruleParam = param.getRule();
            PreconditionUtils.checkNotNull(ruleParam, "规则调度参数不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ruleParam.getJudgeRules()),
                    "judgeRules 不能为空", HttpStatus.BAD_REQUEST);
            validateJudgeRules(ruleParam.getJudgeRules(), param.getSpaceId());
            // 监控对象来自条件操作数/执行目标，仅用于展示，不要求外部指定
            ontologyIdentifier = judgeMonitorObject(ruleParam.getJudgeRules(), ontologyIdentifier);
            var handleRule = ActionHandleRule.builder()
                    .actionApi(action.getApi())
                    .ontologyIdentifier(ontologyIdentifier)
                    .ontologySpaceId(action.getOntologySpaceId())
                    .name(param.getName())
                    .description(param.getDescription())
                    .judgeJson(writeJudgeJson(ruleParam.getJudgeRules()))
                    .flowDocument(ruleParam.getFlowDocument())
                    .status(ScheduleStatus.STOP)
                    .build();
            actionHandleRuleService.save(handleRule);
            id = handleRule.getId();
        }
        return id;
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateScheduling(ActionSchedulingUpdateParam param) {
        // 行为与对象解绑：按 actionApi 查行为；行为 api 只在空间内唯一，传了 spaceId 就按空间收敛，多条命中时要求定位
        var action = resolveScheduleAction(actionMapper.selectList(new LambdaQueryWrapper<OntologyAction>()
                        .eq(OntologyAction::getApi, param.getActionApi())
                        .eq(param.getSpaceId() != null, OntologyAction::getOntologySpaceId, param.getSpaceId())),
                param.getActionApi());
        PreconditionUtils.checkNotNull(action, "action不存在：" + param.getActionApi());
        var ontologyIdentifier = scheduleObject(action, param.getOntologyIdentifier());
        //todo 目前只考虑定时调度
        var schedulingType = param.getType();
        checkSchedulingType(schedulingType);
        if (schedulingType.equals(ActionSchedulingTypeEnum.TASK)) {
            var taskParam = param.getTask();
            PreconditionUtils.checkNotNull(taskParam, "type=TASK 时 task 对象不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(taskParam.getTaskCronExpression()),
                    "type=TASK 时 task.taskCronExpression 不能为空", HttpStatus.BAD_REQUEST);
            var handleTask = actionHandleTaskService.getById(param.getId());
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + param.getId());
            PreconditionUtils.checkArgument(!ScheduleStatus.START.equals(handleTask.getStatus()), "周期调度已开启，请先暂停后再编辑");
            PreconditionUtils.checkArgument(handleTask.getOntologySpaceId() == null
                            || action.getOntologySpaceId().equals(handleTask.getOntologySpaceId()),
                    "只能选择本空间的行为", HttpStatus.BAD_REQUEST);
            handleTask.setActionApi(action.getApi())
                    .setOntologyIdentifier(ontologyIdentifier)
                    .setOntologySpaceId(action.getOntologySpaceId())
                    .setDescription(param.getDescription())
                    .setCron(taskParam.getTaskCronExpression())
                    .setPeriodType(taskParam.getPeriodType())
                    .setEntityJson(taskParam.getEntityJson())
                    .setName(param.getName());
            actionHandleTaskService.updateById(handleTask);
            try {
                xxlJobClient.updateJobInfo(
                        handleTask.getRemark(),
                        param.getName(),
                        taskParam.getTaskCronExpression(),
                        XxlJobActionExecuteParam.builder()
                                .scheduleId(param.getId())
                                .actionApi(param.getActionApi())
                                .ontologyUniqueIdentifier(ontologyIdentifier)
                                .build());
            } catch (Exception e) {
                log.error("updateJobInfo failed", e);
                throw new BusinessException("远程调用xxl-job更新jobinfo失败");
            }
        } else if (schedulingType.equals(ActionSchedulingTypeEnum.RULE)) {
            var handleRule = actionHandleRuleService.getById(param.getId());
            PreconditionUtils.checkNotNull(handleRule, "规则调度不存在：" + param.getId());
            PreconditionUtils.checkArgument(!ScheduleStatus.START.equals(handleRule.getStatus()), "规则调度已开启，请先暂停后再编辑");
            PreconditionUtils.checkArgument(handleRule.getOntologySpaceId() == null
                            || action.getOntologySpaceId().equals(handleRule.getOntologySpaceId()),
                    "只能选择本空间的行为", HttpStatus.BAD_REQUEST);
            var ruleParam = param.getRule();
            PreconditionUtils.checkNotNull(ruleParam, "规则调度参数不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ruleParam.getJudgeRules()),
                    "judgeRules 不能为空", HttpStatus.BAD_REQUEST);
            validateJudgeRules(ruleParam.getJudgeRules(), action.getOntologySpaceId());
            ontologyIdentifier = judgeMonitorObject(ruleParam.getJudgeRules(), ontologyIdentifier);
            handleRule.setActionApi(action.getApi())
                    .setOntologyIdentifier(ontologyIdentifier)
                    .setOntologySpaceId(action.getOntologySpaceId())
                    .setName(param.getName())
                    .setDescription(param.getDescription())
                    .setJudgeJson(writeJudgeJson(ruleParam.getJudgeRules()))
                    .setFlowDocument(ruleParam.getFlowDocument());
            actionHandleRuleService.updateById(handleRule);
        }
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void removeScheduling(ActionSchedulingTypeEnum type, Long id) {
        checkSchedulingType(type);
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
        } else if (ActionSchedulingTypeEnum.RULE.equals(type)) {
            var handleRule = actionHandleRuleService.getById(id);
            PreconditionUtils.checkNotNull(handleRule, "规则调度不存在：" + id);
            actionHandleRuleService.removeById(handleRule.getId());
        }
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void startScheduling(ActionSchedulingTypeEnum type, Long id) {
        checkSchedulingType(type);
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
        } else if (ActionSchedulingTypeEnum.RULE.equals(type)) {
            var handleRule = actionHandleRuleService.getById(id);
            PreconditionUtils.checkNotNull(handleRule, "规则调度不存在：" + id);
            actionHandleRuleService.updateById(handleRule.setStatus(ScheduleStatus.START));
            // 启用即对条件绑定实例评估一遍存量数据，满足就执行；等状态真正落库后再异步跑，不阻塞启动接口，失败只记日志
            var ruleId = handleRule.getId();
            Runnable submitEvaluateStock = () -> {
                try {
                    taskExecutor.execute(() -> actionRuleTriggerService.evaluateRuleNow(ruleId));
                } catch (Exception e) {
                    log.warn("规则调度启动后存量评估提交失败, ruleId={}", ruleId, e);
                }
            };
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submitEvaluateStock.run();
                    }
                });
            } else {
                submitEvaluateStock.run();
            }
        }
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void stopScheduling(ActionSchedulingTypeEnum type, Long id) {
        checkSchedulingType(type);
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
        } else if (ActionSchedulingTypeEnum.RULE.equals(type)) {
            var handleRule = actionHandleRuleService.getById(id);
            PreconditionUtils.checkNotNull(handleRule, "规则调度不存在：" + id);
            actionHandleRuleService.updateById(handleRule.setStatus(ScheduleStatus.STOP));
        }

    }

    @Override
    public ActionSchedulingDetailVO getSchedulingDetailById(Long id, ActionSchedulingTypeEnum type) {
        checkSchedulingType(type);
        if (ActionSchedulingTypeEnum.TASK.equals(type)) {
            var handleTask = actionHandleTaskService.getById(id);
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + id);
            var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                    .eq(OntologyAction::getApi, handleTask.getActionApi())
                    .eq(OntologyAction::getOntologySpaceId, handleTask.getOntologySpaceId()));
            PreconditionUtils.checkNotNull(action, "调度绑定的行为不存在：" + handleTask.getActionApi(), HttpStatus.BAD_REQUEST);
            var ontology = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, handleTask.getOntologyIdentifier()));
            return ActionSchedulingDetailVO.builder()
                    .actionApi(action.getApi())
                    .actionName(action.getDisplayName())
                    .ontologyName(ontology == null ? null : ontology.getDisplayName())
                    .schedulingName(handleTask.getName())
                    .id(handleTask.getId())
                    .description(handleTask.getDescription())
                    .type(ActionSchedulingTypeEnum.TASK)
                    .ontologyIdentifier(handleTask.getOntologyIdentifier())
                    .taskVO(ActionHandleTaskInfoVO.builder().taskCronExpression(handleTask.getCron())
                            .periodType(handleTask.getPeriodType())
                            .entityJson(handleTask.getEntityJson()).build())
                    .status(handleTask.getStatus())
                    .build();
        } else if (ActionSchedulingTypeEnum.RULE.equals(type)) {
            var handleRule = actionHandleRuleService.getById(id);
            PreconditionUtils.checkNotNull(handleRule, "规则调度不存在：" + id);
            var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                    .eq(OntologyAction::getApi, handleRule.getActionApi())
                    .eq(OntologyAction::getOntologySpaceId, handleRule.getOntologySpaceId()));
            PreconditionUtils.checkNotNull(action, "调度绑定的行为不存在：" + handleRule.getActionApi(), HttpStatus.BAD_REQUEST);
            var ontology = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, handleRule.getOntologyIdentifier()));
            return ActionSchedulingDetailVO.builder()
                    .actionApi(action.getApi())
                    .actionName(action.getDisplayName())
                    .ontologyName(ontology == null ? null : ontology.getDisplayName())
                    .schedulingName(handleRule.getName())
                    .id(handleRule.getId())
                    .description(handleRule.getDescription())
                    .type(ActionSchedulingTypeEnum.RULE)
                    .ontologyIdentifier(handleRule.getOntologyIdentifier())
                    .ruleVO(ActionHandleRuleVO.builder()
                            .judgeRules(readJudgeJson(handleRule.getJudgeJson()))
                            .flowDocument(handleRule.getFlowDocument())
                            .build())
                    .status(handleRule.getStatus())
                    .build();
        }
        return null;
    }

    @Override
    public Page<ActionSchedulingInfoVO> listScheduling(Integer pageNum, Integer pageSize, String name, ActionSchedulingTypeEnum type, Integer ontologySpaceId) {
        if (type != null) {
            checkSchedulingType(type);
        }
        List<ActionHandleTask> tasks = List.of();
        List<ActionHandleRule> rules = List.of();
        if (type == null || ActionSchedulingTypeEnum.TASK.equals(type)) {
            tasks = actionHandleTaskService.list(new LambdaQueryWrapper<ActionHandleTask>()
                    .like(StringUtils.isNotBlank(name), ActionHandleTask::getName, name));
        }
        if (type == null || ActionSchedulingTypeEnum.RULE.equals(type)) {
            rules = actionHandleRuleService.list(new LambdaQueryWrapper<ActionHandleRule>()
                    .like(StringUtils.isNotBlank(name), ActionHandleRule::getName, name));
        }
        if (tasks.isEmpty() && rules.isEmpty()) {
            return new Page<ActionSchedulingInfoVO>(pageNum, pageSize);
        }

        List<String> actionApis = Stream.concat(tasks.stream().map(ActionHandleTask::getActionApi),
                        rules.stream().map(ActionHandleRule::getActionApi))
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        Map<String, OntologyAction> actionMap = actionApis.isEmpty() ? Map.of()
                : actionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getApi, actionApis)
                        .eq(ontologySpaceId != null, OntologyAction::getOntologySpaceId, ontologySpaceId))
                .stream().collect(Collectors.toMap(v -> v.getApi(), v -> v, (existingValue, newValue) -> existingValue));

        List<String> ontologyUniqIds = Stream.concat(
                        tasks.stream().map(ActionHandleTask::getOntologyIdentifier),
                        rules.stream().map(ActionHandleRule::getOntologyIdentifier))
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        Map<String, OntologyMeta> ontologyMap = ontologyUniqIds.isEmpty() ? Map.of()
                : ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, ontologyUniqIds))
                .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v, (existingValue, newValue) -> existingValue));

        List<ActionSchedulingInfoVO> all = new ArrayList<>();
        for (var v : tasks) {
            var action = v.getActionApi() == null ? null : actionMap.get(v.getActionApi());
            if (action == null) {
                continue;
            }
            var ontologyMeta = v.getOntologyIdentifier() == null ? null : ontologyMap.get(v.getOntologyIdentifier());
            all.add(ActionSchedulingInfoVO.builder()
                    .actionApi(action.getApi())
                    .actionName(action.getDisplayName())
                    .ontologyName(ontologyMeta == null ? null : ontologyMeta.getDisplayName())
                    .schedulingName(v.getName())
                    .id(v.getId())
                    .description(v.getDescription())
                    .type(ActionSchedulingTypeEnum.TASK)
                    .ontologyIdentifier(v.getOntologyIdentifier())
                    .periodType(v.getPeriodType())
                    .status(v.getStatus())
                    .createTime(v.getCreateTime())
                    .updateTime(v.getUpdateTime())
                    .build());
        }
        for (var v : rules) {
            var action = v.getActionApi() == null ? null : actionMap.get(v.getActionApi());
            // 指定空间时 actionMap 只含该空间行为，查不到即不属于该空间
            if (ontologySpaceId != null && action == null) {
                continue;
            }
            // 监控对象可能推导为空，仅影响展示名称，不应把规则行整条丢掉
            var ontologyMeta = v.getOntologyIdentifier() == null ? null : ontologyMap.get(v.getOntologyIdentifier());
            all.add(ActionSchedulingInfoVO.builder()
                    .actionApi(v.getActionApi())
                    .actionName(action == null ? null : action.getDisplayName())
                    .ontologyName(ontologyMeta == null ? null : ontologyMeta.getDisplayName())
                    .schedulingName(v.getName())
                    .id(v.getId())
                    .description(v.getDescription())
                    .type(ActionSchedulingTypeEnum.RULE)
                    .ontologyIdentifier(v.getOntologyIdentifier())
                    .status(v.getStatus())
                    .createTime(v.getCreateTime())
                    .updateTime(v.getUpdateTime())
                    .build());
        }
        all.sort(Comparator.comparing(ActionSchedulingInfoVO::getId).reversed());

        int total = all.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return new Page<ActionSchedulingInfoVO>(pageNum, pageSize)
                .setTotal(total)
                .setRecords(all.subList(from, to));
    }

    @Override
    public Page<SchedulingResultVO> getSchedulingResult(Long id, ActionSchedulingTypeEnum type, TaskStatusEnum taskStatus,
                                                        Date beginTime, Date endTime, Integer pageNum, Integer pageSize) {
        //todo 只考虑定时任务
        var pageInfo = new Page<ActionHandleLog>(pageNum, pageSize).addOrder(OrderItem.desc("id"));
        var pages = actionHandleLogMapper.selectPage(pageInfo, new LambdaQueryWrapper<ActionHandleLog>()
                .eq(ActionHandleLog::getActionHandleId, id)
                .eq(ActionHandleLog::getActionHandleType, type)
                .eq(taskStatus != null, ActionHandleLog::getTaskStatus, taskStatus)
                .ge(beginTime != null, ActionHandleLog::getTriggerTime, beginTime)
                .le(endTime != null, ActionHandleLog::getTriggerTime, endTime));
        var records = pages.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            return new Page<>(pageNum, pageSize);
        }
        var vo = records.stream().map(v -> SchedulingResultVO.builder()
                        .id(v.getId())
                        .msg(v.getMsg())
                        .executeResult(v.getExecuteResult())
                        .taskStatus(v.getTaskStatus())
                        .requestParam(v.getRequestParam())
                        .triggerTime(v.getTriggerTime())
                        .executeStartTime(v.getExecuteStartTime())
                        .executorIp(v.getExecutorIp())
                        .executorHost(v.getExecutorHost())
                        .completeTime(v.getCompleteTime())
                        .build())
                .collect(Collectors.toList());

        return new Page<SchedulingResultVO>(pageNum, pageSize)
                .setTotal(pages.getTotal())
                .setRecords(vo);
    }

    /** 定稿规则校验：行为存在且绑算子、操作数完整、比较符合法、linkFlag/括号配对 */
    private void validateJudgeRules(List<RuleJudgeGroupParam> groups, Integer spaceId) {
        for (var group : groups) {
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(group.getJudgeParams()),
                    "judgeParams 不能为空", HttpStatus.BAD_REQUEST);
            var actionParam = group.getActionParam();
            PreconditionUtils.checkNotNull(actionParam, "actionParam 不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(actionParam.getActionApi()),
                    "actionParam.action_api 不能为空", HttpStatus.BAD_REQUEST);
            // 行为 api 只在空间内唯一，按调度所属空间收敛，避免跨空间同名行为误命中
            var branchAction = resolveScheduleAction(actionMapper.selectList(new LambdaQueryWrapper<OntologyAction>()
                            .eq(OntologyAction::getApi, actionParam.getActionApi())
                            .eq(spaceId != null, OntologyAction::getOntologySpaceId, spaceId)),
                    actionParam.getActionApi());
            PreconditionUtils.checkNotNull(branchAction, "规则绑定的行为不存在：" + actionParam.getActionApi(), HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(StringUtils.isNotEmpty(branchAction.getFunctionApi()),
                    "规则绑定的行为未绑算子，不支持调度：" + actionParam.getActionApi(), HttpStatus.BAD_REQUEST);

            int depth = 0;
            var items = group.getJudgeParams();
            var groupMonitorType = group.getMonitorType();
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i);
                // 事件类型可选在组上选一次；条件项自带的类型须与组级一致
                PreconditionUtils.checkArgument(item.getMonitorType() == null || groupMonitorType == null
                                || item.getMonitorType() == groupMonitorType,
                        "judgeParams[" + i + "] 的 monitorType 与规则组 monitorType 不一致："
                                + item.getMonitorType() + " != " + groupMonitorType, HttpStatus.BAD_REQUEST);
                var monitorType = item.getMonitorType() != null ? item.getMonitorType() : groupMonitorType;
                if (monitorType == RuleMonitorTypeEnum.INSERT || monitorType == RuleMonitorTypeEnum.DELETE) {
                    // 新增/删除：选对象+属性+输入固定值比较，分组由属性推导
                    validateEventOperate(item, i);
                } else {
                    // 修改/旧属性条件：左操作数取实例属性当前值，右操作数固定值或另一实例属性
                    validateJudgeOperate(item.getLeftOperate(), "leftOperate");
                    validateJudgeOperate(item.getRightOperate(), "rightOperate");
                    PreconditionUtils.checkNotNull(item.getCondition(), "judgeParams 缺少比较操作符 condition", HttpStatus.BAD_REQUEST);
                    PreconditionUtils.checkArgument(item.getCondition() != ActionHandleRuleAddConditionEnum.CH,
                            "规则调度不支持 CH（变化）比较", HttpStatus.BAD_REQUEST);
                }
                int leftBrackets = item.getIsLeft() == null ? 0 : item.getIsLeft();
                int rightBrackets = item.getIsRight() == null ? 0 : item.getIsRight();
                PreconditionUtils.checkArgument(leftBrackets >= 0 && rightBrackets >= 0,
                        "judgeParams 括号个数不能为负", HttpStatus.BAD_REQUEST);
                depth += leftBrackets;
                depth -= rightBrackets;
                PreconditionUtils.checkArgument(depth >= 0, "judgeParams 右括号多余", HttpStatus.BAD_REQUEST);
                if (i < items.size() - 1) {
                    PreconditionUtils.checkNotNull(item.getLinkFlag(), "非末条 judgeParams 必须给 linkFlag", HttpStatus.BAD_REQUEST);
                }
            }
            PreconditionUtils.checkArgument(depth == 0, "judgeParams 括号不配对", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateJudgeOperate(RuleOperateParam operate, String name) {
        PreconditionUtils.checkNotNull(operate, name + " 不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkNotNull(operate.getType(), name + ".type 不能为空（dynamic|fixed）", HttpStatus.BAD_REQUEST);
        if (operate.getType() == RuleOperateType.DYNAMIC) {
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(operate.getOntologyId())
                            && StringUtils.isNotBlank(operate.getEntityId())
                            && (StringUtils.isNotBlank(operate.getPropertyId()) || StringUtils.isNotBlank(operate.getPropertyName())),
                    name + " 为 dynamic 时必须给 OntologyId、entityId 和 propertyId/propertyName 之一", HttpStatus.BAD_REQUEST);
        } else {
            PreconditionUtils.checkNotNull(operate.getFixValue(), name + " 为 fixed 时必须给 fixValue", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 新增/删除条件：选对象 + 属性 + 输入固定值比较，不再选存储分组（分组由属性推导）。
     * 对象必须启用、属性必须在该对象上启用并绑定数据源列，避免配了但永远收不到事件。
     * 属性在主表(main)分组时不能指定实例：事件行就是实例本身，指定实例会让新增几乎永远不命中。
     */
    private void validateEventOperate(RuleJudgeItemParam item, int index) {
        String name = "judgeParams[" + index + "]";
        var operate = item.getLeftOperate();
        PreconditionUtils.checkNotNull(operate, name + " leftOperate 不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(operate.getType() == RuleOperateType.DYNAMIC,
                name + " 新增/删除条件 leftOperate.type 必须为 dynamic", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(operate.getOntologyId()),
                name + " 新增/删除条件 leftOperate 必须给 OntologyId", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(operate.getPropertyId())
                        || StringUtils.isNotBlank(operate.getPropertyName()),
                name + " 新增/删除条件必须选属性（propertyId/propertyName）", HttpStatus.BAD_REQUEST);
        var right = item.getRightOperate();
        PreconditionUtils.checkNotNull(right, name + " 新增/删除条件 rightOperate 不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(right.getType() == RuleOperateType.FIXED,
                name + " 新增/删除条件 rightOperate.type 必须为 fixed（输入比较值）", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkNotNull(right.getFixValue(), name + " 新增/删除条件缺少比较值 fixValue", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkNotNull(item.getCondition(), name + " 新增/删除条件缺少比较操作符 condition", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(item.getCondition() != ActionHandleRuleAddConditionEnum.CH,
                "规则调度不支持 CH（变化）比较", HttpStatus.BAD_REQUEST);
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, operate.getOntologyId())
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkNotNull(meta, "监测对象不存在或未启用：" + operate.getOntologyId(), HttpStatus.BAD_REQUEST);
        var wrapper = new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, operate.getOntologyId())
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue());
        if (StringUtils.isNotBlank(operate.getPropertyId())) {
            wrapper.eq(OntologyProperty::getUniqueIdentifier, operate.getPropertyId());
        } else {
            wrapper.eq(OntologyProperty::getApiName, operate.getPropertyName());
        }
        var property = propertyMapper.selectList(wrapper).stream().findFirst().orElse(null);
        PreconditionUtils.checkNotNull(property, name + " 监测属性不存在或未启用", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(property.getDatasourceColumnName()),
                name + " 监测属性未绑定数据源列，无法按事件行取值", HttpStatus.BAD_REQUEST);
        // 主表行就是实例本身：新增/删除的实例由事件行决定，指定实例没有意义（新增几乎永远不命中）
        var storageGroup = StringUtils.defaultIfBlank(property.getStorageGroup(), MAIN_STORAGE_GROUP);
        PreconditionUtils.checkArgument(!(MAIN_STORAGE_GROUP.equals(storageGroup)
                        && StringUtils.isNotBlank(operate.getEntityId())),
                name + " 监测主表(main)属性时不能指定实例：新增/删除的实例即事件行本身，entityId 请留空",
                HttpStatus.BAD_REQUEST);
    }

    /** 定稿模式的展示用监控对象：优先执行目标对象，其次首个条件操作数对象 */
    private String judgeMonitorObject(List<RuleJudgeGroupParam> groups, String fallback) {
        for (var group : groups) {
            if (group.getActionParam() != null && StringUtils.isNotBlank(group.getActionParam().getOntologyIdentifier())) {
                return group.getActionParam().getOntologyIdentifier();
            }
        }
        for (var group : groups) {
            for (var item : group.getJudgeParams()) {
                for (var operate : new RuleOperateParam[]{item.getLeftOperate(), item.getRightOperate()}) {
                    if (operate != null && operate.getType() == RuleOperateType.DYNAMIC
                            && StringUtils.isNotBlank(operate.getOntologyId())) {
                        return operate.getOntologyId();
                    }
                }
            }
        }
        return fallback;
    }

    @SneakyThrows
    private String writeJudgeJson(List<RuleJudgeGroupParam> judgeRules) {
        if (CollectionUtils.isEmpty(judgeRules)) {
            return null;
        }
        return jsonMapper.writeValueAsString(judgeRules);
    }

    @SneakyThrows
    private List<RuleJudgeGroupParam> readJudgeJson(String judgeJson) {
        if (StringUtils.isBlank(judgeJson)) {
            return null;
        }
        return jsonMapper.readValue(judgeJson, new TypeReference<List<RuleJudgeGroupParam>>() {
        });
    }


}
