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
import com.aircas.ptr.foundry.ontology.service.behavior.TreeBranchRunner;
import com.aircas.ptr.foundry.ontology.service.behavior.TreeCooldownTracker;
import com.aircas.ptr.foundry.ontology.service.behavior.TreeExecutionRuntime;
import com.aircas.ptr.foundry.ontology.utils.FunctionOutputValueReader;
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
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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

    /** 冷却表缺失只提示一次，避免每个冷却节点、每次保存都刷日志 */
    private final AtomicBoolean behaviorTreeCooldownWarned = new AtomicBoolean();


    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public void removeByOntologyIdentifier(String ontologyIdentifier) {
        var actions = list(new LambdaQueryWrapper<OntologyAction>()
                .apply(OBJECT_IDENTIFIER_CONTAINS, ontologyIdentifier));
        if (CollectionUtils.isEmpty(actions)) {
            return;
        }
        // 删对象会连带删行为，与单个删除保持同一口径：被行为树引用的行为先拦下，
        // 否则树里只剩一个永远解析不到的 api，要等到执行才暴露
        var referenced = new ArrayList<String>();
        var spaces = new LinkedHashSet<Integer>();
        for (var action : actions) {
            if (action.getOntologySpaceId() != null) {
                spaces.add(action.getOntologySpaceId());
            }
        }
        for (var spaceId : spaces) {
            var spaceApis = actions.stream()
                    .filter(action -> Objects.equals(action.getOntologySpaceId(), spaceId))
                    // 树节点只能绑非树行为，树自身的定义不参与引用判定
                    .filter(action -> action.getActionKind() != ActionKindEnum.TREE)
                    .map(OntologyAction::getApi)
                    .collect(Collectors.toSet());
            if (!spaceApis.isEmpty()) {
                referenced.addAll(findTreesReferencingActions(spaceId, spaceApis));
            }
        }
        PreconditionUtils.checkArgument(referenced.isEmpty(),
                "对象下的行为被行为树引用，不能删除对象：" + String.join("；", referenced), HttpStatus.BAD_REQUEST);

        var actionIds = actions.stream().map(v -> v.getId()).collect(Collectors.toList());
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().in(OntologyActionLink::getOntologyActionId, actionIds));
        remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, actionIds));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, actionIds));
        // 调度行靠 action_id 关联行为，该列已在 alter_table_20260909.sql 中删除，此处无法再按行为清理调度；
        // 遗留的调度任务会在下次触发时因取不到行为而失败，待确认该列是否为误删后补回
        spaces.forEach(btTreeManager::invalidateSpace);
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
        if (action.getActionKind() == ActionKindEnum.TREE && cooldownTableReady()) {
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
            // 校验会按绑定行为把缺的外部参数补成黑板键，发布时把归一后的定义写回库里
            action.setDefinition(writeTreeDefinition(param.getTreeDefinition()));
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
            checkSubtableFullWrite(definition);
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
            } else {
                PreconditionUtils.checkArgument(param.getOperation() == null && StringUtils.isEmpty(param.getStorageGroup()),
                        "基础算子行为不需要基础操作和存储分组", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(param.getOutputMode() != ActionOutputModeEnum.WRITE_PROPERTY,
                        "基础算子行为只支持仅返回结果", HttpStatus.BAD_REQUEST);
                // 基础算子行为仍不要求关系：它没有归属对象，执行链路也不落关系边
                checkFunctionAction(param, definition, Collections.singletonList(FunctionTypeEnum.BASIC_QUERY),
                        spaceId, false);
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
        action.setOntologySpaceId(spaceId);
        action.setActionCategoryId(param.getCategoryId());
        action.setActionKind(kind);
        action.setSubjectType(param.getSubjectType());
        action.setActionOperation(param.getOperation());
        action.setStorageGroup(param.getStorageGroup());
        action.setOntologyUniqueIdentifier(new ArrayList<>(definition.objectIdentifiers));
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

        // 绑定行为要的外部参数先补成树自己的黑板键，后面黑板/变量/节点校验才能看到完整契约
        completeTreeExternalInputs(tree, spaceId);
        var blackboardTypes = checkTreeBlackboard(tree);
        checkTreeVariables(tree, blackboardTypes);
        var roots = tree.getRoots();
        PreconditionUtils.checkArgument(roots.size() == 1, "行为树必须且只能有一个根节点，当前 " + roots.size() + " 个", HttpStatus.BAD_REQUEST);
        var boundApis = new HashSet<String>();
        checkTreeNode(roots.get(0), spaceId, blackboardTypes, new HashSet<>(), boundApis);

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
        action.setDefinition(writeTreeDefinition(tree));
        // 树自己不指向对象，它的挂载对象就是各节点绑定的行为的挂载对象
        action.setOntologyUniqueIdentifier(new ArrayList<>(treeObjectIdentifiers(boundApis, spaceId)));
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
                if (StringUtils.isNotBlank(config.getEntityPrimaryKeyKey())) {
                    PreconditionUtils.checkArgument(blackboardTypes.containsKey(config.getEntityPrimaryKeyKey()),
                            "冷却节点 " + nodeId + " 引用了未声明的黑板键：" + config.getEntityPrimaryKeyKey(), HttpStatus.BAD_REQUEST);
                }
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
                if (actionApis.add(boundApi)) {
                    checkTreeBoundAction(nodeId, boundApi, spaceId);
                }
            }
        }
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
                    ? mapping.getOntologyUniqueIdentifier() : singleActionObject(bound);
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
        var link = actionLinkMapper.selectOne(new LambdaQueryWrapper<OntologyActionLink>()
                .eq(OntologyActionLink::getOntologyActionId, bound.getId()));
        if (link == null || StringUtils.isBlank(link.getOntologyLinkUniqueIdentifier())) {
            return null;
        }
        var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, link.getOntologyLinkUniqueIdentifier()));
        return linkGroup == null ? null : entityService.counterpartOntology(linkGroup, ownTreeObject(bound));
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
     * 绑定行为声明为「外部输入」的入参在保存期补成树自己的黑板键。
     * <p>键名取行为参数名——执行时按同名从执行入参取值；类型取算子参数类型，不绑算子的行为取属性类型。
     * 已有同名键不动，类型以作者声明的为准。</p>
     */
    private void completeTreeExternalInputs(BehaviorTreeDefinition tree, Integer spaceId) {
        var actionNodes = collectTreeActionNodes(tree.getRoots());
        if (actionNodes.isEmpty()) {
            return;
        }
        var blackboard = tree.getBlackboard();
        var variables = tree.getVariables();
        Set<String> declaredKeys = blackboard.stream()
                .map(BehaviorBlackboardItem::getKey)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(HashSet::new));
        for (var actionApi : actionNodes.keySet()) {
            var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                    .eq(OntologyAction::getApi, actionApi)
                    .eq(OntologyAction::getOntologySpaceId, spaceId));
            // 行为不存在、未发布由 checkTreeBoundAction 报错，这里不抢先中断
            if (bound == null) {
                continue;
            }
            for (var param : treeExternalParamDefs(bound)) {
                if (!declaredKeys.add(param.name())) {
                    continue;
                }
                blackboard.add(BehaviorBlackboardItem.builder()
                        .key(param.name())
                        .valueType(param.type())
                        .description("自动补全：行为 " + actionApi + " 的外部输入参数")
                        .build());
                variables.add(BehaviorVariableItem.builder()
                        .name(param.name())
                        .valueType(param.type())
                        .scope(BehaviorVariableScopeEnum.RUNTIME)
                        .build());
            }
        }
        tree.setBlackboard(blackboard);
        tree.setVariables(variables);
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
        var mappings = ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>()
                .eq(OntologyActionMappingIn::getOntologyActionId, bound.getId()));
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

        List<TreeExternalParam> params = new ArrayList<>();
        for (var mapping : mappings) {
            var funcParam = mapping.getFunctionParamId() == null ? null : funcParams.get(mapping.getFunctionParamId());
            if (funcParam != null && funcParam.getCategory() != FunctionParamCategoryEnum.INPUT) {
                continue;
            }
            if (mapping.getSourceType() != null && mapping.getSourceType() != ActionParamSourceEnum.EXTERNAL) {
                continue;
            }
            if (StringUtils.isBlank(mapping.getParameterName())) {
                continue;
            }
            FunctionParamTypeEnum type = null;
            String description = null;
            if (funcParam != null) {
                type = funcParam.getParamType();
                description = funcParam.getDescription();
            } else if (StringUtils.isNotBlank(mapping.getPropertyUniqueIdentifier())) {
                var property = properties.get(mapping.getPropertyUniqueIdentifier());
                type = property == null ? null : toBlackboardValueType(property.getPropertyType());
                description = property == null ? null : property.getDisplayName();
            }
            params.add(new TreeExternalParam(mapping.getParameterName(),
                    type == null ? FunctionParamTypeEnum.STRING : type, description));
        }
        return params;
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
     * 按绑定行为反推执行入参要求：外部参数没有默认值就是必填，实例要求按对象逐条列出。
     * <p>绑了对象属性的黑板键不参与行为执行，同一个键既是属性绑定又是外部入参时，执行前按冲突报错。</p>
     */
    private TreeExecuteInputsVO buildTreeExecuteInputs(BehaviorTreeDefinition tree, Integer spaceId) {
        Map<String, BehaviorBlackboardItem> items = new LinkedHashMap<>();
        tree.getBlackboard().forEach(item -> {
            if (StringUtils.isNotBlank(item.getKey())) {
                items.putIfAbsent(item.getKey(), item);
            }
        });
        Map<String, Object> defaults = new LinkedHashMap<>();
        tree.getVariables().forEach(variable -> {
            if (StringUtils.isNotBlank(variable.getName())) {
                defaults.putIfAbsent(variable.getName(), variable.getDefaultValue());
            }
        });

        Map<String, TreeExecuteInputVO> inputParams = new LinkedHashMap<>();
        List<TreeInstanceRequirementVO> instanceRequirements = new ArrayList<>();
        for (var entry : collectTreeActionNodes(tree.getRoots()).entrySet()) {
            var bound = getOne(new LambdaQueryWrapper<OntologyAction>()
                    .eq(OntologyAction::getApi, entry.getKey())
                    .eq(OntologyAction::getOntologySpaceId, spaceId));
            // 悬空引用由节点执行时上报，这里只描述能用的行为
            if (bound == null) {
                continue;
            }
            var actionNodes = entry.getValue();
            for (var param : treeExternalParamDefs(bound)) {
                var vo = inputParams.get(param.name());
                if (vo == null) {
                    var item = items.get(param.name());
                    var bindProperty = item != null && StringUtils.isNotBlank(item.getBindPropertyUniqueIdentifier());
                    var defaultValue = defaults.get(param.name());
                    vo = TreeExecuteInputVO.builder()
                            .key(param.name())
                            .valueType(item == null ? param.type() : item.getValueType())
                            .defaultValue(defaultValue)
                            .bindProperty(bindProperty)
                            // 外部入参的值只可能来自执行入参或变量默认值；黑板按属性刷新不参与行为执行
                            .required(defaultValue == null)
                            .description(param.description())
                            .nodeIds(new ArrayList<>())
                            .actionApis(new ArrayList<>())
                            .build();
                    inputParams.put(param.name(), vo);
                }
                var inputVO = vo;
                actionNodes.forEach(actionNode -> {
                    if (!inputVO.getNodeIds().contains(actionNode.getId())) {
                        inputVO.getNodeIds().add(actionNode.getId());
                    }
                });
                if (!inputVO.getActionApis().contains(entry.getKey())) {
                    inputVO.getActionApis().add(entry.getKey());
                }
            }
            var requirement = treeInstanceRequirement(bound);
            if (requirement == null) {
                continue;
            }
            // 关系对端也可以按对象选实例；自关系（对端就是本对象）不能用同一个实例顶替，仍要显式传对端主键
            var counterpartObject = requirement.isLinkEntityPrimaryKeyRequired()
                    ? treeActionCounterpartObject(bound) : null;
            if (StringUtils.equals(counterpartObject, ownTreeObject(bound))) {
                counterpartObject = null;
            }
            // 一个行为可能读写多个对象类型：按对象展开，每个对象要哪条实例都由执行请求按对象给
            var objectIds = new ArrayList<>(treeActionKeyRequiredObjects(bound));
            if (objectIds.isEmpty()) {
                // 存量映射行没写对象标识，只能按行为作用的对象描述；挂了多个对象或认不出时为空，
                // 不往列表里塞 null，否则前端会拿到一条没有对象的实例要求
                var ownObject = ownTreeObject(bound);
                if (StringUtils.isNotBlank(ownObject)) {
                    objectIds.add(ownObject);
                }
            }
            var nodeIds = actionNodes.stream()
                    .map(BehaviorTreeNode::getId)
                    .distinct()
                    .collect(Collectors.toList());
            for (var objectId : objectIds) {
                instanceRequirements.add(TreeInstanceRequirementVO.builder()
                        .actionApi(requirement.getActionApi())
                        .actionDisplayName(requirement.getActionDisplayName())
                        .objectIdentifier(objectId)
                        .nodeIds(nodeIds)
                        .linkEntityPrimaryKeyRequired(requirement.isLinkEntityPrimaryKeyRequired())
                        .linkObjectIdentifier(counterpartObject)
                        .build());
            }
        }
        return TreeExecuteInputsVO.builder()
                .entityPrimaryKeyRequired(!instanceRequirements.isEmpty())
                .inputParams(new ArrayList<>(inputParams.values()))
                .instanceRequirements(instanceRequirements)
                .build();
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

    /** 属性值/实例关系行为必须有实例；绑算子的行为按映射判断是否要从实例取属性 */
    private TreeInstanceRequirementVO treeInstanceRequirement(OntologyAction bound) {
        var needsLink = bound.getActionKind() == ActionKindEnum.BASE
                && bound.getSubjectType() == ActionSubjectTypeEnum.LINK;
        var needsEntity = needsLink
                || (bound.getActionKind() == ActionKindEnum.BASE
                && bound.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE)
                || treeActionNeedsInstance(bound);
        if (!needsEntity) {
            return null;
        }
        return TreeInstanceRequirementVO.builder()
                .actionApi(bound.getApi())
                .actionDisplayName(bound.getDisplayName())
                .objectIdentifier(ownTreeObject(bound))
                .linkEntityPrimaryKeyRequired(needsLink)
                .build();
    }

    /** 绑算子的行为是否要从实例数据取值：入参来源是属性/对象/List，或出参写入对象属性 */
    private boolean treeActionNeedsInstance(OntologyAction bound) {
        var mappings = ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>()
                .eq(OntologyActionMappingIn::getOntologyActionId, bound.getId()));
        if (CollectionUtils.isEmpty(mappings)) {
            return false;
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
            if (isInput) {
                var source = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
                if (source == ActionParamSourceEnum.PROPERTY || source == ActionParamSourceEnum.OBJECT
                        || source == ActionParamSourceEnum.LIST) {
                    return true;
                }
            } else if (mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行前把定义归一：旧树可能还没把绑定行为的外部参数补成黑板键，按现规则补一份再跑，
     * 否则详情要求传参、运行时却因为键未声明拒绝。编译缓存里的定义不能改，这里用副本。
     */
    private BehaviorTreeDefinition prepareTreeDefinition(BehaviorTreeDefinition definition, Integer spaceId) {
        var prepared = readTreeDefinition(writeTreeDefinition(definition));
        completeTreeExternalInputs(prepared, spaceId);
        return prepared;
    }

    /**
     * 执行前按节点校验行为要的数据是否配齐：节点自己传了算节点的，没传算执行请求里的全树默认值，
     * 缺什么一次列清楚，避免行为拿着 null 跑出误导结果。
     *
     * @param requireInstances 实例是否必须由调用方给；批量执行的实例由遍历提供时传 false
     */
    private void checkTreeExecuteInputs(BehaviorTreeDefinition tree, Integer spaceId, TreeExecutionRuntime runtime,
                                        boolean requireInstances) {
        var problems = new ArrayList<String>();
        var conflicted = new LinkedHashSet<String>();
        var missingParams = new LinkedHashSet<String>();
        var unknownParams = new LinkedHashSet<String>();
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
                for (var paramName : declaredParams) {
                    var item = runtime.getBlackboardItems().get(paramName);
                    if (item != null && StringUtils.isNotBlank(item.getBindPropertyUniqueIdentifier())) {
                        conflicted.add(paramName + "（节点 " + nodeId + " 的行为 " + bound.getApi() + "）");
                        continue;
                    }
                    if (runtime.nodeParamValue(nodeId, paramName) == null) {
                        missingParams.add(paramName + "（节点 " + nodeId + "）");
                    }
                }
                runtime.nodeParamNames(nodeId).stream()
                        .filter(paramName -> !declaredParams.contains(paramName))
                        .forEach(paramName -> unknownParams.add(paramName + "（节点 " + nodeId + "）"));
                if (!requireInstances) {
                    continue;
                }
                for (var objectId : treeActionKeyRequiredObjects(bound)) {
                    if (runtime.nodeEntityPrimaryKey(nodeId, objectId) == null) {
                        missingInstances.add("节点 " + nodeId + " 的对象 " + objectId);
                    }
                }
                if (treeInstanceNeedLink(bound) && runtime.nodeLinkEntityPrimaryKey(nodeId) == null
                        && runtime.nodeMappedEntityPrimaryKey(nodeId, treeActionCounterpartObject(bound)) == null) {
                    missingLinks.add("节点 " + nodeId);
                }
            }
        }
        if (!conflicted.isEmpty()) {
            problems.add("入参冲突：" + String.join("、", conflicted)
                    + " 既是绑定行为的外部入参又绑了对象属性，属性键的值由条件求值时读取，不接受执行入参；"
                    + "请取消该黑板键的属性绑定，或让行为换一个参数名");
        }
        if (!missingParams.isEmpty()) {
            problems.add("缺少行为入参：" + String.join("、", missingParams)
                    + "；按参数名在执行请求的 inputs 或 nodeExecutes 里传");
        }
        if (!unknownParams.isEmpty()) {
            problems.add("行为没有这些外部入参：" + String.join("、", unknownParams)
                    + "；nodeExecutes 的 params 只能传该行为声明的外部输入参数名");
        }
        if (!missingInstances.isEmpty()) {
            problems.add("缺少实例：" + String.join("、", missingInstances)
                    + "；请用 entityPrimaryKey、按对象的 entityPrimaryKeys 或 nodeExecutes 传");
        }
        if (!missingLinks.isEmpty()) {
            problems.add("缺少关系对端：" + String.join("、", missingLinks)
                    + "；请用 linkEntityPrimaryKey 或按对端对象传实例");
        }
        if (!problems.isEmpty()) {
            throw new BusinessException("行为树执行前数据没配齐：" + String.join("；", problems), HttpStatus.BAD_REQUEST);
        }
    }

    /** 该行为是否要关系对端主键；对端是绑定关系时的对端对象，自关系只能显式传 */
    private boolean treeInstanceNeedLink(OntologyAction bound) {
        var requirement = treeInstanceRequirement(bound);
        return requirement != null && requirement.isLinkEntityPrimaryKeyRequired();
    }

    /**
     * 批量执行按一个对象的实例逐条跑，实例主键只对该对象类型有效：
     * 树里若还有别的对象类型要用实例数据，批量请求没有任何地方能给，只能在执行前拦下。
     */
    private void checkTreeBatchInstanceScope(TreeExecuteInputsVO plan, TreeBatchExecuteParam param) {
        var traversalObject = param.getOntologyUniqueIdentifier();
        var mismatched = plan.getInstanceRequirements().stream()
                .filter(requirement -> StringUtils.isNotBlank(requirement.getObjectIdentifier()))
                .filter(requirement -> !StringUtils.equals(requirement.getObjectIdentifier(), traversalObject))
                .collect(Collectors.toList());
        if (mismatched.isEmpty()) {
            return;
        }
        var detail = mismatched.stream()
                .map(requirement -> "行为 " + requirement.getActionApi()
                        + "（对象 " + requirement.getObjectIdentifier()
                        + "，节点 " + String.join("、", requirement.getNodeIds()) + "）")
                .distinct()
                .collect(Collectors.joining("；"));
        throw new BusinessException("批量执行遍历的对象是 " + traversalObject
                + "，每条实例只给这个对象的实例主键，但树里这些行为还要按各自对象取实例：" + detail
                + "；请改用单次执行，在 entityPrimaryKeys 里按对象传实例主键", HttpStatus.BAD_REQUEST);
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

    /** 行为的一个外部输入参数：名字用于黑板同名匹配，类型用于自动补键 */
    private record TreeExternalParam(String name, FunctionParamTypeEnum type, String description) {
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
        var definition = prepareTreeDefinition(compiled.definition(), param.getOntologySpaceId());
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
        var definition = prepareTreeDefinition(compiled.definition(), param.getOntologySpaceId());
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
        var definition = prepareTreeDefinition(compiled.definition(), param.getOntologySpaceId());
        checkTreeBatchNodeExecutes(param);
        var plan = buildTreeExecuteInputs(definition, param.getOntologySpaceId());
        checkTreeExecuteInputs(definition, param.getOntologySpaceId(),
                buildTreeRuntime(compiled, definition, null, param), false);
        checkTreeBatchInstanceScope(plan, param);
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

    /** 可绑定行为的参数按算子定义给出：外部输入参数由执行请求按参数名给，前端据此生成执行表单 */
    private TreeAvailableActionVO toTreeAvailableAction(OntologyAction action) {
        var builder = TreeAvailableActionVO.builder()
                .actionApi(action.getApi())
                .displayName(action.getDisplayName())
                .actionKind(action.getActionKind())
                .subjectType(action.getSubjectType())
                .functionApi(action.getFunctionApi())
                // 执行时全树共用一个实例主键，目录里先标明哪些行为离不开实例
                .requiresInstance(treeInstanceRequirement(action) != null)
                .objectIdentifier(ownTreeObject(action))
                .instanceObjects(new ArrayList<>(treeActionKeyRequiredObjects(action)))
                // 外部输入参数才是执行时要调用方补的，属性/固定值来源不需要，目录里单独列出来
                .externalParams(toTreeActionParamVOs(treeExternalParamDefs(action)));
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

    /** 单次执行/试跑的运行时：执行请求里的全套数据原样带进来，执行前的校验和真正执行共用这一份 */
    private TreeExecutionRuntime buildTreeRuntime(CompiledBehaviorTree compiled, BehaviorTreeDefinition definition,
                                                  TreeExecuteParam param) {
        return buildTreeRuntime(compiled, definition, param.getEntityPrimaryKey(),
                param.getEntityPrimaryKeys(), param.getInputs(), param.getDataPrimaryKey(),
                param.getInputDataPrimaryKeys(), param.getOutputDataPrimaryKeys(),
                singleInputTargets(param.getInputParams()), param.getOutputParams(),
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
                                                  Map<String, ActionInstanceTargetParam> inputParams,
                                                  Map<String, ActionInstanceTargetParam> outputParams,
                                                  Object linkEntityPrimaryKey,
                                                  Map<String, TreeNodeExecuteParam> nodeExecutes) {
        return new TreeExecutionRuntime(definition, compiled.api(), entityPrimaryKey, entityPrimaryKeys, inputs,
                dataPrimaryKey, inputDataPrimaryKeys, outputDataPrimaryKeys, inputParams, outputParams,
                linkEntityPrimaryKey, nodeExecutes, compiled.spaceId(),
                this::tryRunTreeCooldown, this::runTreeBranches, this::invokeTreeNodeAction,
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
     * 冷却占位：同一棵树、同一节点、同一执行实例只保留最近一次执行时间。
     * upsert 自带间隔判断，并发执行时不会出现两次都读到旧时间后同时放行。
     * <p>冷却状态表缺失时按「不可用」返回，由冷却节点放行并在结果里提示；
     * 不把辅助表的缺失升级成整棵树执行失败。</p>
     */
    private TreeCooldownTracker.CooldownDecision tryRunTreeCooldown(Integer spaceId, String treeApi, String nodeId,
                                                                    Object entityPrimaryKey, int intervalSeconds) {
        var scopeKey = entityPrimaryKey == null ? "" : String.valueOf(entityPrimaryKey);
        if (scopeKey.length() > TREE_COOLDOWN_SCOPE_MAX_LENGTH) {
            scopeKey = scopeKey.substring(0, TREE_COOLDOWN_SCOPE_MAX_LENGTH);
        }
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
     * 行为节点：节点只决定绑哪个行为和重试次数，行为需要的实例、分组行主键、关系对端、
     * 外部入参全部取执行请求给的值——按节点传的优先，没传的沿用全树默认，执行结果不回填黑板。
     */
    private TreeActionInvoker.TreeActionOutcome invokeTreeNodeAction(String nodeId, Integer spaceId,
                                                                     BehaviorActionConfig config,
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
        var entityPrimaryKeys = runtime.nodeEntityPrimaryKeys(nodeId);
        var testParam = ActionInvokeParam.builder()
                .ontologySpaceId(spaceId)
                .actionApi(boundApi)
                // 应用行为没有所属对象，单靠 entityPrimaryKey 定位不到实例，按对象把主键传下去
                .entityPrimaryKey(runtime.nodeEntityPrimaryKey(nodeId, ownTreeObject(bound)))
                .entityPrimaryKeys(entityPrimaryKeys.isEmpty() ? null : entityPrimaryKeys)
                .dataPrimaryKey(runtime.nodeDataPrimaryKey(nodeId))
                .inputEntities(toInstanceTargets(runtime.nodeInputDataPrimaryKeys(nodeId)))
                .outputEntities(toInstanceTargets(runtime.nodeOutputDataPrimaryKeys(nodeId)))
                .inputParams(wrapInputTargets(runtime.nodeInputParams(nodeId)))
                .outputParams(runtime.nodeOutputParams(nodeId))
                .linkEntityPrimaryKey(treeLinkEntityPrimaryKey(nodeId, bound, runtime))
                .params(treeExternalParams(nodeId, bound, runtime))
                .build();
        // 树节点的数据位置都来自执行请求，走内部入口，别绕回对外请求再剥掉一次
        var testResult = runTestAction(testParam);
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
                .entityPrimaryKey(source.getEntityPrimaryKey())
                .dataPrimaryKeys(source.getDataPrimaryKeys() == null ? null
                        : new LinkedHashMap<>(source.getDataPrimaryKeys()))
                .build();
    }

    /** 树节点按参数只定位一个数据位置，测试请求里这个字段是候选数组，包成单元素数组传下去 */
    private Map<String, List<ActionInstanceTargetParam>> wrapInputTargets(
            Map<String, ActionInstanceTargetParam> targets) {
        if (MapUtils.isEmpty(targets)) {
            return null;
        }
        Map<String, List<ActionInstanceTargetParam>> wrapped = new LinkedHashMap<>();
        targets.forEach((paramName, target) -> wrapped.put(paramName, new ArrayList<>(List.of(target))));
        return wrapped;
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
     * 行为声明的外部输入参数按参数名取值：节点单独传了用节点的，否则用全树同名值，
     * 和详情接口列出的必填入参同一套定义。
     * <p>黑板不参与行为执行：属性绑定的键会被条件求值刷新，拿它当参数值会让同一个行为每轮取到不同的值。</p>
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
     * 条件求值前把绑定了对象属性的黑板键按库里的当前值重读一遍。
     * <p>行为不写黑板，属性值只能通过这条路进黑板：绑同一个对象的键合并成一次回读，
     * 定位不到实例或行里没有这个属性时不动黑板值，把原因写进节点说明，
     * 否则条件会拿着变量默认值判成执行。</p>
     */
    private void refreshTreeBlackboardProperties(String nodeId, TreeExecutionRuntime runtime) {
        var boundItems = runtime.getBlackboardItems().values().stream()
                .filter(item -> StringUtils.isNotBlank(item.getBindPropertyUniqueIdentifier()))
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
            var entityPrimaryKey = runtime.nodeEntityPrimaryKey(nodeId, objectId);
            if (entityPrimaryKey == null) {
                noInstanceObjects.add(objectId);
                return;
            }
            latestValues.putAll(readTreeRowPropertyValues(objectId, entityPrimaryKey,
                    runtime.nodeInputDataPrimaryKeys(nodeId).get(objectId), runtime.nodeDataPrimaryKey(nodeId), props));
        });
        if (!noInstanceObjects.isEmpty()) {
            runtime.appendNodeMessage(nodeId, "对象 " + String.join("、", noInstanceObjects)
                    + " 本次执行没给实例，绑定其属性的黑板键没有刷新");
        }
        runtime.getBlackboardItems().forEach((key, item) -> {
            var bind = item.getBindPropertyUniqueIdentifier();
            if (StringUtils.isNotBlank(bind) && latestValues.containsKey(bind)) {
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
     * 出参是数组（LIST）且写回目标是子表时，给取值路径补上 {@code [*]}。
     * <p>参数树点出来的路径只有 {@code $.result.longitude} 这种形状，而 {@code result} 声明成 LIST 时它下面那些字段
     * 是「元素字段」：按单值取只会拿到第一个元素，返回 5 条就静默少落 4 行，从落库数据上看不出是被截断还是本来只有 1 条。
     * 这里按参数声明的形状补成 {@code $.result[*].longitude}，执行侧才走逐元素插行。</p>
     * <p>不动的几种：主表只有实例那一行，取第一个元素就是它的值，补了反而会被「通配只能写子表」拒掉；
     * 已经写了 {@code [*]} 或写了 {@code [0]} 这类下标的按用户原意保留；路径停在数组层本身
     * （{@code $.result} 整包给 Array 属性）没有逐元素可言；下一段不是该出参声明的元素字段时认不出数组层，
     * 宁可不补也不猜路径。</p>
     */
    private String wildcardListExpression(String expression, FunctionParamPO funcParam, OntologyProperty property) {
        if (StringUtils.isBlank(expression) || funcParam.getParamType() != FunctionParamTypeEnum.LIST
                || FunctionOutputValueReader.hasWildcard(expression)) {
            return expression;
        }
        var storageGroup = property == null ? null : property.getStorageGroup();
        var onMainTable = StringUtils.isBlank(storageGroup) || MAIN_STORAGE_GROUP.equals(storageGroup);
        if (onMainTable) {
            return expression;
        }
        var elementFieldNames = parseParamFields(funcParam).stream()
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
            currentType = field.getParamType();
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
                        paramByRow.get(row).getParamType() == FunctionParamTypeEnum.LIST))
                .forEach(rows -> rows.forEach(row -> row.setFunctionParamExpression(
                        wildcardListExpression(row.getFunctionParamExpression(), paramByRow.get(row), propByRow.get(row)))));
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

    /** 调度必须落到一个对象上：调用方指定优先，没指定时只有挂载单个对象的行为才推得出 */
    private String scheduleObject(OntologyAction action, String requested) {
        if (StringUtils.isNotBlank(requested)) {
            return requested;
        }
        var object = singleActionObject(action);
        PreconditionUtils.checkArgument(object != null,
                "行为挂载了多个对象，配置调度时必须指定 ontologyIdentifier：" + action.getApi(), HttpStatus.BAD_REQUEST);
        return object;
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
        /** 本次绑定里能认出的挂载对象id，校验顺路收集，落库到行为的挂载对象列 */
        private final Set<String> objectIdentifiers = new LinkedHashSet<>();
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
            var tree = readTreeDefinition(action.getDefinition());
            // 早于自动补键规则保存的草稿在详情里按现行规则补全，保证详情与执行期用的是同一份契约
            completeTreeExternalInputs(tree, action.getOntologySpaceId());
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

    @SneakyThrows
    @Override
    public String executeAction(OntologyActionExecuteParam param) {
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
                        .orderByDesc(ActionHandleLog::getId)
                        .last("limit 1"));
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
        var invokeParam = toInvokeParam(param);
        // 入参方向把某个字段写成数组就是批量测试：展开成组合后逐组跑一遍。不传数组时与改造前完全一致
        var testAxes = collectTestAxes(invokeParam);
        return testAxes.isEmpty() ? runTestAction(invokeParam) : testActionCombos(invokeParam, testAxes);
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
                .inputParams(param.getInputParams())
                .outputParams(param.getOutputParams())
                .dataPrimaryKey(param.getDataPrimaryKey())
                .linkEntityPrimaryKey(param.getLinkEntityPrimaryKey())
                .params(param.getParams())
                .build();
    }

    /**
     * 行为测试：用一条真实数据把行为定义跑一遍并落库。
     * <p>
     * 不加事务：属性值行为写数据湖、实例关系行为写图库、算子行为跨两者，
     * 且发现必填属性缺失时的失效标记必须独立提交，不能被随后的异常回滚掉。
     * <p>
     * 批量展开只在对外的 {@link #testAction(ActionTestParam)} 做：行为树节点也是走这里执行单条行为，
     * 黑板里的值偶尔本来就是集合，不能在那条链路上悄悄炸成多组。
     */
    private ActionTestResultVO runTestAction(ActionInvokeParam param) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, param.getOntologySpaceId()));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        // 草稿可以试跑（发布前正是要拿它验证定义）
        // 行为树编排的是已有行为，没有单条行为的入参绑定，走行为测试只会得到"算子不存在"这类误导提示
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.TREE,
                "行为树不支持按单个行为测试", HttpStatus.BAD_REQUEST);
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
            // 属性值/实例关系行为不绑算子，没有算子入参可指向
            if (MapUtils.isNotEmpty(param.getInputParams()) || MapUtils.isNotEmpty(param.getOutputParams())) {
                throw new BusinessException("属性值/实例关系行为不绑算子，不支持用 inputParams、outputParams 按参数指定数据位置",
                        HttpStatus.BAD_REQUEST);
            }
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
        return testResult(action, runPropertyValueAction(action, detail.getMappingIns(), executeParam, param.getParams()));
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
    private ActionTestResultVO testFunctionAction(OntologyAction action, OntologyActionDetailVO detail, ActionInvokeParam param) {
        // 行为可以挂载在多个对象上：请求只给了一个挂载对象的实例时它就是请求对象，
        // 按多个对象都给了实例时只能靠入参/出参各自给的数据位置
        var ownObject = requestActionObject(action, param);
        var executeParamBuilder = EntityActionExecuteParam.builder()
                .actionApi(action.getApi())
                .ontologySpaceId(action.getOntologySpaceId())
                .ontologyUniqueIdentifier(ownObject)
                .entityPrimaryKey(param.getEntityPrimaryKey())
                .entityPrimaryKeys(param.getEntityPrimaryKeys())
                // 按对象方向给的数据位置：树执行的树级/节点级默认行主键走这里，对外测试请求不带
                .inputEntities(param.getInputEntities())
                .outputEntities(param.getOutputEntities())
                // 按算子入参/出参指定的数据位置：入参读哪个实例/哪一行、出参写到哪个实例/哪一行
                .inputParams(singleInputTargets(param.getInputParams()))
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

    /**
     * 单次执行每个入参只能落在一个数据位置上：批量测试已经展开成单元素数组，
     * 树执行/树试跑一次也只跑一个位置，这里取唯一元素交给执行侧。
     */
    private Map<String, ActionInstanceTargetParam> singleInputTargets(
            Map<String, List<ActionInstanceTargetParam>> targets) {
        if (MapUtils.isEmpty(targets)) {
            return null;
        }
        Map<String, ActionInstanceTargetParam> single = new LinkedHashMap<>();
        targets.forEach((paramName, candidates) -> {
            if (candidates == null) {
                return;
            }
            PreconditionUtils.checkArgument(!candidates.isEmpty(),
                    "入参[" + paramName + "]的数据位置数组不能为空，只用一个位置时数组里放一个元素",
                    HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(candidates.size() == 1,
                    "入参[" + paramName + "]一次只能指定一个数据位置，数组里放一个元素即可",
                    HttpStatus.BAD_REQUEST);
            checkInputTargetElement("入参[" + paramName + "]", candidates.get(0));
            single.put(paramName, candidates.get(0));
        });
        return single.isEmpty() ? null : single;
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
     * <p>inputParams 每个入参本身就是数据位置数组，一个元素是一组完整取值：只有一个元素时就是单次执行，
     * 多个元素才作为一个轴参与组合。params 不作为轴：LIST 类型入参的值本来就是数组，分不清是候选值还是参数本身。
     * 出参方向出现数组直接拦下，一次执行只能有一个写回位置。</p>
     */
    private List<TestAxis> collectTestAxes(ActionInvokeParam param) {
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
        addInputTargetAxes(axes, TEST_AXIS_FIELD_INPUT_PARAMS, param.getInputParams());
        return axes;
    }

    /** 入参方向的数据位置数组：一个元素就是一组完整取值，多个元素才展开成轴 */
    private void addInputTargetAxes(List<TestAxis> axes, String field,
                                    Map<String, List<ActionInstanceTargetParam>> targets) {
        if (targets == null) {
            return;
        }
        targets.forEach((paramName, candidates) -> {
            if (candidates == null) {
                return;
            }
            var label = axisLabel(field, paramName, null);
            PreconditionUtils.checkArgument(!candidates.isEmpty(), label + " 的数据位置数组不能为空", HttpStatus.BAD_REQUEST);
            for (var target : candidates) {
                checkInputTargetElement(label, target);
            }
            if (candidates.size() > 1) {
                axes.add(new TestAxis(field, paramName, null, new ArrayList<>(candidates)));
            }
        });
    }

    /** 一个数据位置元素自身必须是单值：数组只在外层表示多组候选值 */
    private void checkInputTargetElement(String label, ActionInstanceTargetParam target) {
        PreconditionUtils.checkNotNull(target, label + " 的数据位置数组里有 null", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(!(target.getEntityPrimaryKey() instanceof List<?>),
                label + ".entityPrimaryKey 不能再嵌套数组，一个元素就是一组数据位置，多组请在外层数组放多个元素",
                HttpStatus.BAD_REQUEST);
        if (target.getDataPrimaryKeys() != null) {
            target.getDataPrimaryKeys().forEach((group, value) -> PreconditionUtils.checkArgument(
                    !(value instanceof List<?>),
                    label + ".dataPrimaryKeys[" + group + "] 不能再嵌套数组，一个元素就是一组数据位置，多组请在外层数组放多个元素",
                    HttpStatus.BAD_REQUEST));
        }
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

    /** 按笛卡尔积展开组合，越靠后的取值轴变化越快 */
    private List<List<Object>> expandTestCombos(List<TestAxis> axes) {
        var total = 1;
        for (var axis : axes) {
            total *= axis.values().size();
        }
        PreconditionUtils.checkArgument(total <= MAX_TEST_COMBOS,
                "批量测试最多 " + MAX_TEST_COMBOS + " 组组合，当前候选值会展开出 " + total + " 组（"
                        + describeTestAxes(axes) + "），请减少候选值", HttpStatus.BAD_REQUEST);
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

    private String describeTestAxes(List<TestAxis> axes) {
        return axes.stream()
                .map(axis -> axis.label() + "=" + axis.values().size())
                .collect(Collectors.joining("、"));
    }

    /**
     * 批量行为测试：逐组把标量化的请求交给单组测试，复用它的全部校验与执行链路。
     * <p>每组各自落库、各自记成败，一组失败不影响其余组合，也不回滚已经写进去的数据——
     * 单组测试本来就不加事务。</p>
     */
    private ActionTestResultVO testActionCombos(ActionInvokeParam param, List<TestAxis> axes) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, param.getOntologySpaceId()));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(action.getActionKind() != ActionKindEnum.BASE,
                "属性值/实例关系行为只操作单个实例，不支持批量测试", HttpStatus.BAD_REQUEST);
        var combos = expandTestCombos(axes);
        var cases = new ArrayList<ActionTestCaseResultVO>(combos.size());
        var results = new ArrayList<String>(combos.size());
        var failed = 0;
        for (int index = 0; index < combos.size(); index++) {
            var comboValues = combos.get(index);
            var single = copyTestParam(param);
            for (int axisIndex = 0; axisIndex < axes.size(); axisIndex++) {
                applyTestAxis(single, axes.get(axisIndex), comboValues.get(axisIndex));
            }
            String result = null;
            String error = null;
            try {
                result = runTestAction(single).getResult();
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
        return testResult(action, "共 " + combos.size() + " 组组合，成功 " + succeeded + " / 失败 " + failed)
                .setResult(toResultsJson(results))
                .setCases(cases);
    }

    private Map<String, Object> comboValuesMap(List<TestAxis> axes, List<Object> values) {
        var combo = new LinkedHashMap<String, Object>();
        for (int index = 0; index < axes.size(); index++) {
            combo.put(axes.get(index).label(), values.get(index));
        }
        return combo;
    }

    @SneakyThrows
    private String toResultsJson(List<String> results) {
        return jsonMapper.writeValueAsString(results);
    }

    /**
     * 算子返回是否整组皆空：执行链路会把算子自己抛的异常吞成空字符串，
     * 批量时不显式判出来就成了「算子报错却报成功」。
     */
    private boolean isBlankFunctionResult(String result) {
        if (StringUtils.isEmpty(result)) {
            return true;
        }
        try {
            var items = jsonMapper.readValue(result, new TypeReference<List<String>>() {
            });
            return items.stream().allMatch(StringUtils::isEmpty);
        } catch (Exception e) {
            return false;
        }
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
                .inputParams(copyTestTargetLists(param.getInputParams()))
                .outputParams(param.getOutputParams())
                .dataPrimaryKey(param.getDataPrimaryKey())
                .linkEntityPrimaryKey(param.getLinkEntityPrimaryKey())
                .params(param.getParams())
                .build();
    }

    private Map<String, List<ActionInstanceTargetParam>> copyTestTargetLists(
            Map<String, List<ActionInstanceTargetParam>> targets) {
        if (targets == null) {
            return null;
        }
        var copy = new LinkedHashMap<String, List<ActionInstanceTargetParam>>();
        targets.forEach((paramName, candidates) -> copy.put(paramName, candidates == null ? null
                : candidates.stream()
                .map(target -> target == null ? null : copyParamTarget(target))
                .collect(Collectors.toCollection(ArrayList::new))));
        return copy;
    }

    private void applyTestAxis(ActionInvokeParam param, TestAxis axis, Object value) {
        switch (axis.field()) {
            case TEST_AXIS_FIELD_ENTITY_PRIMARY_KEYS -> param.getEntityPrimaryKeys().put(axis.key(), value);
            case TEST_AXIS_FIELD_INPUT_PARAMS -> applyTestTargetAxis(param.getInputParams(), axis, value);
            default -> throw new BusinessException("未知的批量测试取值轴：" + axis.label(), HttpStatus.BAD_REQUEST);
        }
    }

    private void applyTestTargetAxis(Map<String, List<ActionInstanceTargetParam>> targets, TestAxis axis, Object value) {
        targets.put(axis.key(), new ArrayList<>(List.of(copyParamTarget((ActionInstanceTargetParam) value))));
    }

    @Override
    @Transactional
    public Long createScheduling(ActionSchedulingCreateParam param) {
        //todo 目前只考虑定时调度
        var schedulingType = param.getType();
        // 行为与对象解绑：直接按 actionApi 查行为，用行为自身的空间，不再依赖对象 id
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi()));
        PreconditionUtils.checkNotNull(action, "action不存在：" + param.getActionApi());
        // 属性值、实例关系行为没有算子，执行时会对对象下全部实例逐个落库，定时跑会造成重复数据，不支持调度
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(action.getFunctionApi()),
                "不绑定算子的行为不支持配置调度：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        var ontologyIdentifier = scheduleObject(action, param.getOntologyIdentifier());
        Long id = null;
        //允许同一个行为配置多个调度策略
        if (schedulingType.equals(ActionSchedulingTypeEnum.TASK)) {
            var taskParam = param.getTask();
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
        }
        return id;
    }

    @Override
    @Transactional
    public void updateScheduling(ActionSchedulingUpdateParam param) {
        // 行为与对象解绑：直接按 actionApi 查行为，不再校验对象 id
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi()));
        PreconditionUtils.checkNotNull(action, "action不存在：" + param.getActionApi());
        var ontologyIdentifier = scheduleObject(action, param.getOntologyIdentifier());
        //todo 目前只考虑定时调度
        var schedulingType = param.getType();
        if (schedulingType.equals(ActionSchedulingTypeEnum.TASK)) {
            var handleTask = actionHandleTaskService.getById(param.getId());
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + param.getId());
            handleTask.setActionApi(action.getApi())
                    .setOntologyIdentifier(ontologyIdentifier)
                    .setOntologySpaceId(action.getOntologySpaceId())
                    .setDescription(param.getDescription())
                    .setCron(param.getTask().getTaskCronExpression())
                    .setPeriodType(param.getTask().getPeriodType())
                    .setEntityJson(param.getTask().getEntityJson())
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
                                .ontologyUniqueIdentifier(ontologyIdentifier)
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
        }
        return null;
    }

    @Override
    public Page<ActionSchedulingInfoVO> listScheduling(Integer pageNum, Integer pageSize, String name, ActionSchedulingTypeEnum type) {
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
                : actionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getApi, actionApis))
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
            var action = actionMap.get(v.getActionApi());
            if (action == null) {
                continue;
            }
            var ontologyMeta = ontologyMap.get(v.getOntologyIdentifier());
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
            var action = actionMap.get(v.getActionApi());
            var ontologyMeta = ontologyMap.get(v.getOntologyIdentifier());
            if (ontologyMeta == null) {
                continue;
            }
            all.add(ActionSchedulingInfoVO.builder()
                    .actionApi(v.getActionApi())
                    .actionName(action == null ? null : action.getDisplayName())
                    .ontologyName(ontologyMeta.getDisplayName())
                    .schedulingName(v.getName())
                    .id(v.getId())
                    .description(v.getDescription())
                    .type(ActionSchedulingTypeEnum.RULE)
                    .ontologyIdentifier(ontologyMeta.getUniqueIdentifier())
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
                        .completeTime(v.getCompleteTime())
                        .build())
                .collect(Collectors.toList());

        return new Page<SchedulingResultVO>(pageNum, pageSize)
                .setTotal(pages.getTotal())
                .setRecords(vo);
    }


}
