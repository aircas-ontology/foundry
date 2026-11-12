package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorCompareOpEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionInstanceTargetParam;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorActionConfig;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorBlackboardConditionConfig;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorBlackboardItem;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorTreeDefinition;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorTreeNode;
import com.aircas.ptr.foundry.ontology.model.param.TreeNodeExecuteParam;
import com.aircas.ptr.foundry.ontology.service.behavior.nodes.OntologyTreeNodes;
import com.github.silencesu.behavior3java.core.Blackboard;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.http.HttpStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 一次树执行（单条实例）的运行时：黑板、节点执行痕迹、条件求值。
 * <p>黑板只是全局变量，用来决定节点执行不执行：变量默认值打底、执行入参覆盖，
 * 绑了对象属性的键在每次条件求值前按库里的当前值重新读一遍。行为既不读黑板也不写黑板，
 * 它需要的参数全部由执行请求传：全树共用一份按对象/参数名给，绑同一个行为的多个节点用 nodeExecutes 各给一套。
 * 每次执行独立一个实例，behavior3java 的 Blackboard 只承载库内置节点的记忆状态。
 * 并行节点的多个分支共用一个运行时，所以黑板与节点痕迹按并发访问处理。</p>
 */
@Slf4j
public class TreeExecutionRuntime implements BehaviorTreeRuntime {

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    /** 黑板是 Map，表达式按黑板键取值靠它；禁写，防止表达式把黑板当变量池改 */
    private static final MapAccessor MAP_ACCESSOR = new MapAccessor(false);

    /** 节点没配执行数据时的空壳，字段全空即全部回退全树默认值 */
    private static final TreeNodeExecuteParam EMPTY_NODE_EXECUTE = new TreeNodeExecuteParam();

    private final BehaviorTreeDefinition definition;

    /** 树 api，冷却节点按「树 + 节点 + 实例」定位持久化状态 */
    private final String treeApi;

    private final Object entityPrimaryKey;

    /** 按对象分开给的实例主键，对象优先于 entityPrimaryKey */
    private final Map<String, Object> entityPrimaryKeys;

    /** 执行级的分组表行主键 */
    private final Object dataPrimaryKey;

    /** 按对象给的入参分组行主键：对象id -> (存储分组 -> 行主键)，应用行为按行取属性入参 */
    private final Map<String, Map<String, Object>> inputDataPrimaryKeys;

    /** 按对象给的出参分组行主键：对象id -> (存储分组 -> 行主键)，应用行为按行写出参 */
    private final Map<String, Map<String, Object>> outputDataPrimaryKeys;

    /** 按算子入参给的数据位置：入参名 -> {实例主键, 分组 -> 行主键} */
    private final Map<String, ActionInstanceTargetParam> inputParams;

    /** 按算子出参给的数据位置：出参名 -> {实例主键, 分组 -> 行主键} */
    private final Map<String, ActionInstanceTargetParam> outputParams;

    /** 执行请求给的关系对端实体主键，全树共用 */
    private final Object linkEntityPrimaryKey;

    /**
     * 按行为节点给的一整套数据（节点标识 -> 节点执行数据）。
     * <p>两个节点绑同一个行为时靠它各传一套；每个字段单独回退，节点没给的沿用执行请求里的全树默认值。</p>
     */
    private final Map<String, TreeNodeExecuteParam> nodeExecutes;

    /**
     * 行为参数的取值：变量默认值打底、执行入参覆盖，一次执行内不再变。
     * <p>与 {@link #blackboard} 分开存：黑板会被条件求值按属性刷新，行为参数不能被那次刷新污染。</p>
     */
    private final Map<String, Object> paramInputs;

    private final Integer spaceId;

    private final TreeCooldownTracker cooldownTracker;

    private final TreeBranchRunner branchRunner;

    private final TreeActionInvoker actionInvoker;

    private final TreeBlackboardRefresher blackboardRefresher;

    /** 黑板键名 -> 当前值；条件求值前按绑定属性刷新，并行分支同时读，用同步包装保留插入顺序 */
    private final Map<String, Object> blackboard = Collections.synchronizedMap(new LinkedHashMap<>());

    /** 黑板键名 -> 黑板项声明，条件求值前按 bindPropertyUniqueIdentifier 找要重读的属性 */
    private final Map<String, BehaviorBlackboardItem> blackboardItems = new LinkedHashMap<>();

    /** 节点标识 -> 执行痕迹；并行分支各写各的节点 */
    private final Map<String, BehaviorNodeTrace> traces = new ConcurrentHashMap<>();

    /**
     * 当前线程上生效的限时截止时间。限时节点在本线程压栈，子树在并行分支线程执行时把快照带过去；
     * 只用于「超时后不再发起新的行为」，不中断已经开始的行为。
     */
    private final ThreadLocal<Deque<Long>> deadlines = new ThreadLocal<>();

    /** 当前线程最近一次并行等待是否因限时结束；并行节点读它补节点说明 */
    private final ThreadLocal<Boolean> branchTimedOut = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 单次执行的总步数预算：按节点数放大，让正常的多次循环不被误伤，同时拦住嵌套循环的乘性放大 */
    private final int stepBudget;

    private final AtomicInteger consumedSteps = new AtomicInteger();

    private final Blackboard behaviorBlackboard = new Blackboard();

    public TreeExecutionRuntime(BehaviorTreeDefinition definition, String treeApi, Object entityPrimaryKey,
                                Map<String, Object> entityPrimaryKeys,
                                Map<String, Object> inputs, Object dataPrimaryKey,
                                Map<String, Map<String, Object>> inputDataPrimaryKeys,
                                Map<String, Map<String, Object>> outputDataPrimaryKeys,
                                Map<String, ActionInstanceTargetParam> inputParams,
                                Map<String, ActionInstanceTargetParam> outputParams,
                                Object linkEntityPrimaryKey, Map<String, TreeNodeExecuteParam> nodeExecutes,
                                Integer spaceId, TreeCooldownTracker cooldownTracker,
                                TreeBranchRunner branchRunner, TreeActionInvoker actionInvoker,
                                TreeBlackboardRefresher blackboardRefresher) {
        this.definition = definition;
        this.treeApi = treeApi;
        this.entityPrimaryKey = entityPrimaryKey;
        this.entityPrimaryKeys = entityPrimaryKeys == null ? Map.of() : entityPrimaryKeys;
        this.dataPrimaryKey = dataPrimaryKey;
        this.inputDataPrimaryKeys = inputDataPrimaryKeys == null ? Map.of() : inputDataPrimaryKeys;
        this.outputDataPrimaryKeys = outputDataPrimaryKeys == null ? Map.of() : outputDataPrimaryKeys;
        this.inputParams = inputParams == null ? Map.of() : inputParams;
        this.outputParams = outputParams == null ? Map.of() : outputParams;
        this.linkEntityPrimaryKey = linkEntityPrimaryKey;
        this.nodeExecutes = nodeExecutes == null ? Map.of() : Map.copyOf(nodeExecutes);
        this.spaceId = spaceId;
        this.cooldownTracker = cooldownTracker;
        this.branchRunner = branchRunner;
        this.actionInvoker = actionInvoker;
        this.blackboardRefresher = blackboardRefresher;
        this.stepBudget = OntologyTreeNodes.MAX_LOOP_ITERATIONS * (countNodes(definition.getRoots()) + 1);
        definition.getBlackboard().forEach(item -> blackboardItems.put(item.getKey(), item));
        Map<String, Object> paramValues = new LinkedHashMap<>();
        for (var variable : definition.getVariables()) {
            blackboard.put(variable.getName(), variable.getDefaultValue());
            paramValues.put(variable.getName(), variable.getDefaultValue());
        }
        if (inputs != null) {
            for (var entry : inputs.entrySet()) {
                PreconditionUtils.checkArgument(blackboard.containsKey(entry.getKey()),
                        "执行入参 " + entry.getKey() + " 不是本树声明的黑板键", HttpStatus.BAD_REQUEST);
                var item = blackboardItems.get(entry.getKey());
                // 绑了对象属性的键每次条件求值都按库里的当前值重读，接受外部入参只会让传进来的值被覆盖
                PreconditionUtils.checkArgument(item == null || StringUtils.isBlank(item.getBindPropertyUniqueIdentifier()),
                        "黑板键 " + entry.getKey() + " 绑的是对象属性，条件每次取的是库里的当前值，不接受执行入参",
                        HttpStatus.BAD_REQUEST);
                blackboard.put(entry.getKey(), entry.getValue());
                paramValues.put(entry.getKey(), entry.getValue());
            }
        }
        this.paramInputs = Collections.unmodifiableMap(paramValues);
        checkNodeExecutesTargetsActionNodes(definition, this.nodeExecutes);
    }

    /**
     * 按节点给的数据只认本树行为节点的标识：节点 id 写错时这份值谁都拿不到，静默忽略等于让调用方以为已经传进去。
     */
    private static void checkNodeExecutesTargetsActionNodes(BehaviorTreeDefinition definition,
                                                            Map<String, TreeNodeExecuteParam> nodeExecutes) {
        if (nodeExecutes.isEmpty()) {
            return;
        }
        var actionNodeIds = new LinkedHashSet<String>();
        collectActionNodeIds(definition.getRoots(), actionNodeIds);
        var unknown = nodeExecutes.keySet().stream()
                .filter(nodeId -> StringUtils.isBlank(nodeId) || !actionNodeIds.contains(nodeId))
                .collect(Collectors.joining("、"));
        PreconditionUtils.checkArgument(StringUtils.isBlank(unknown),
                "按节点传的执行数据里这些标识不是本树的行为节点：" + unknown
                        + "；nodeExecutes 的键要用行为节点的标识（详情接口 executeInputs 里的 nodeIds）", HttpStatus.BAD_REQUEST);
    }

    private static void collectActionNodeIds(List<BehaviorTreeNode> nodes, Set<String> collector) {
        for (var node : nodes) {
            if (node.getKind() == BehaviorNodeKindEnum.ACTION) {
                collector.add(node.getId());
            }
            collectActionNodeIds(node.getChildren(), collector);
        }
    }

    public BehaviorTreeDefinition getDefinition() {
        return definition;
    }

    /** 活的黑板视图；单次读写是线程安全的，遍历要改用 {@link #snapshotBlackboard()} */
    public Map<String, Object> getBlackboard() {
        return blackboard;
    }

    /** 黑板快照：并行分支写入结束后按顺序复制一份，避免遍历时被并发修改 */
    public Map<String, Object> snapshotBlackboard() {
        synchronized (blackboard) {
            return new LinkedHashMap<>(blackboard);
        }
    }

    public Map<String, BehaviorBlackboardItem> getBlackboardItems() {
        return blackboardItems;
    }

    /** behavior3java 需要的黑板实例，库内置节点用它保存 RUNNING 记忆，不复用 */
    public Blackboard getBehaviorBlackboard() {
        return behaviorBlackboard;
    }

    public BehaviorNodeTrace getTrace(String nodeId) {
        return traces.get(nodeId);
    }

    @Override
    public Object getEntityPrimaryKey() {
        return entityPrimaryKey;
    }

    /** 某个对象本次执行用的实例主键：按对象给的优先，没给回退执行级的 entityPrimaryKey */
    private Object entityPrimaryKeyFor(String objectId) {
        if (objectId != null) {
            var value = entityPrimaryKeys.get(objectId);
            if (value != null) {
                return value;
            }
        }
        return entityPrimaryKey;
    }

    /** 只取按对象传的实例主键，不回退执行级主键：关系对端必须是那个对象自己的实例 */
    private Object mappedEntityPrimaryKey(String objectId) {
        return objectId == null ? null : entityPrimaryKeys.get(objectId);
    }

    /** 本节点配的执行数据；没配返回空对象，调用方按字段逐个回退全树默认值 */
    private TreeNodeExecuteParam nodeData(String nodeId) {
        var data = nodeId == null ? null : nodeExecutes.get(nodeId);
        return data == null ? EMPTY_NODE_EXECUTE : data;
    }

    /** 本节点用的实例主键：节点直接传了用节点的，否则按节点按对象的映射取，最后回退全树默认 */
    public Object nodeEntityPrimaryKey(String nodeId, String objectId) {
        var data = nodeData(nodeId);
        var value = data.getEntityPrimaryKey();
        if (value != null) {
            return value;
        }
        var byObject = objectId == null || data.getEntityPrimaryKeys() == null
                ? null : data.getEntityPrimaryKeys().get(objectId);
        return byObject != null ? byObject : entityPrimaryKeyFor(objectId);
    }

    /** 本节点用的按对象实例主键：节点没配就用全树那份 */
    public Map<String, Object> nodeEntityPrimaryKeys(String nodeId) {
        var value = nodeData(nodeId).getEntityPrimaryKeys();
        return value == null || value.isEmpty() ? entityPrimaryKeys : value;
    }

    /** 本节点用的分组表行主键 */
    public Object nodeDataPrimaryKey(String nodeId) {
        var value = nodeData(nodeId).getDataPrimaryKey();
        return value != null ? value : dataPrimaryKey;
    }

    /** 本节点用的入参分组行主键（对象id -> 分组 -> 行主键） */
    public Map<String, Map<String, Object>> nodeInputDataPrimaryKeys(String nodeId) {
        var value = nodeData(nodeId).getInputDataPrimaryKeys();
        return value == null || value.isEmpty() ? inputDataPrimaryKeys : value;
    }

    /** 本节点用的出参分组行主键（对象id -> 分组 -> 行主键） */
    public Map<String, Map<String, Object>> nodeOutputDataPrimaryKeys(String nodeId) {
        var value = nodeData(nodeId).getOutputDataPrimaryKeys();
        return value == null || value.isEmpty() ? outputDataPrimaryKeys : value;
    }

    /** 本节点用的按算子入参数据位置 */
    public Map<String, ActionInstanceTargetParam> nodeInputParams(String nodeId) {
        var value = nodeData(nodeId).getInputParams();
        return value == null || value.isEmpty() ? inputParams : value;
    }

    /** 本节点用的按出参写回位置 */
    public Map<String, ActionInstanceTargetParam> nodeOutputParams(String nodeId) {
        var value = nodeData(nodeId).getOutputParams();
        return value == null || value.isEmpty() ? outputParams : value;
    }

    /** 本节点用的关系对端主键 */
    public Object nodeLinkEntityPrimaryKey(String nodeId) {
        var value = nodeData(nodeId).getLinkEntityPrimaryKey();
        return value != null ? value : linkEntityPrimaryKey;
    }

    /** 本节点用的关系对端对象实例：只认按对象给的，不回退共用主键 */
    public Object nodeMappedEntityPrimaryKey(String nodeId, String objectId) {
        var byObject = nodeData(nodeId).getEntityPrimaryKeys();
        var value = byObject == null ? null : byObject.get(objectId);
        return value != null ? value : mappedEntityPrimaryKey(objectId);
    }

    /**
     * 本节点行为外部入参的取值：节点传了用节点的，否则用全树同名值。
     * <p>节点没配 params 或该参数名没传值时都回退，传了 null 等同没传。</p>
     */
    public Object nodeParamValue(String nodeId, String paramName) {
        var params = nodeData(nodeId).getParams();
        var value = params == null ? null : params.get(paramName);
        return value != null ? value : paramInputValue(paramName);
    }

    /** 本节点单独传的行为入参名，供执行前校验：传了行为没有的参数名要报错 */
    public Set<String> nodeParamNames(String nodeId) {
        var params = nodeData(nodeId).getParams();
        return params == null ? Set.of() : params.keySet();
    }

    @Override
    public Integer getSpaceId() {
        return spaceId;
    }

    /**
     * 行为参数的取值：执行入参覆盖变量默认值，一次执行内固定。
     * <p>黑板会按绑定属性刷新，行为参数不能跟着变，否则同一个行为在循环里每轮拿到不同的值。</p>
     */
    private Object paramInputValue(String key) {
        return paramInputs.get(key);
    }

    /** 只给属性刷新用：节点和行为的执行链路都不通过这里改黑板 */
    public void setBlackboardValue(String key, Object value) {
        blackboard.put(key, value);
    }

    @Override
    public void setNodeMessage(String nodeId, String message) {
        trace(nodeId).setMessage(message);
    }

    @Override
    public void appendNodeMessage(String nodeId, String message) {
        if (StringUtils.isBlank(message)) {
            return;
        }
        var trace = trace(nodeId);
        // 并行节点的分支会同时追加说明，读改写必须串行
        synchronized (trace) {
            trace.setMessage(StringUtils.isBlank(trace.getMessage()) ? message : trace.getMessage() + "；" + message);
        }
    }

    @Override
    public void setNodeState(String nodeId, BehaviorStateEnum state) {
        trace(nodeId).setState(state);
    }

    @Override
    public void setNodeSkipped(String nodeId, String message) {
        var trace = trace(nodeId);
        trace.setState(BehaviorStateEnum.SUCCESS);
        trace.setSkipped(true);
        trace.setMessage(message);
    }

    @Override
    public void resetNodeTrace(String nodeId) {
        var trace = trace(nodeId);
        trace.setState(null);
        trace.setSkipped(false);
        trace.setMessage(null);
    }

    @Override
    public boolean isNodeSkipped(String nodeId) {
        var trace = traces.get(nodeId);
        return trace != null && trace.isSkipped();
    }

    @Override
    public void pushDeadline(long deadlineMillis) {
        deadlineStack().push(deadlineMillis);
    }

    @Override
    public void popDeadline() {
        var stack = deadlines.get();
        if (stack == null) {
            return;
        }
        stack.pop();
        if (stack.isEmpty()) {
            deadlines.remove();
        }
    }

    @Override
    public boolean deadlineExceeded() {
        var stack = deadlines.get();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var now = System.currentTimeMillis();
        for (var deadline : stack) {
            if (now > deadline) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Long> snapshotDeadlines() {
        var stack = deadlines.get();
        return stack == null ? new ArrayList<>() : new ArrayList<>(stack);
    }

    @Override
    public void applyDeadlines(List<Long> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            deadlines.remove();
            return;
        }
        var stack = deadlineStack();
        stack.clear();
        stack.addAll(snapshot);
    }

    private Deque<Long> deadlineStack() {
        var stack = deadlines.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            deadlines.set(stack);
        }
        return stack;
    }

    @Override
    public Boolean evaluateExpression(String nodeId, String expression) {
        if (StringUtils.isBlank(expression)) {
            return false;
        }
        blackboardRefresher.refresh(nodeId, this);
        try {
            var evaluationContext = SimpleEvaluationContext
                    .forPropertyAccessors(MAP_ACCESSOR, DataBindingPropertyAccessor.forReadOnlyAccess())
                    .withRootObject(blackboard)
                    .build();
            return Boolean.TRUE.equals(EXPRESSION_PARSER.parseExpression(expression).getValue(evaluationContext));
        } catch (Exception e) {
            setNodeMessage(nodeId, "表达式求值失败：" + expression);
            log.warn("行为树表达式求值失败, expression={}", expression, e);
            return null;
        }
    }

    @Override
    public Boolean evaluateBlackboardCondition(String nodeId, BehaviorBlackboardConditionConfig config) {
        blackboardRefresher.refresh(nodeId, this);
        var reason = new String[1];
        var hit = compare(config.getOp(), blackboard.get(config.getKey()), config.getValue(), reason);
        if (reason[0] != null) {
            setNodeMessage(nodeId, reason[0]);
            return null;
        }
        if (!hit) {
            setNodeMessage(nodeId, "黑板条件不成立：" + config.getKey() + " " + config.getOp().getName() + " " + config.getValue());
        }
        return hit;
    }

    @Override
    public BehaviorStateEnum executeAction(String nodeId, BehaviorActionConfig config) {
        try {
            var outcome = actionInvoker.invoke(nodeId, spaceId, config, this);
            if (StringUtils.isNotBlank(outcome.message())) {
                setNodeMessage(nodeId, outcome.message());
            }
            return outcome.state();
        } catch (Exception e) {
            var message = e instanceof BusinessException business ? business.getMessage() : e.getMessage();
            setNodeMessage(nodeId, "行为执行失败：" + message);
            log.warn("行为树节点执行失败, nodeId={}, actionApi={}", nodeId, config == null ? null : config.getActionApi(), e);
            return BehaviorStateEnum.FAILURE;
        }
    }

    @Override
    public TreeCooldownTracker.CooldownDecision tryRunCooldown(String nodeId, int intervalSeconds, String entityPrimaryKeyKey) {
        var scope = StringUtils.isBlank(entityPrimaryKeyKey) ? entityPrimaryKey : blackboard.get(entityPrimaryKeyKey);
        return cooldownTracker.tryRun(spaceId, treeApi, nodeId, scope, intervalSeconds);
    }

    @Override
    public boolean tryConsumeStep() {
        return consumedSteps.incrementAndGet() <= stepBudget;
    }

    @Override
    public int getStepBudget() {
        return stepBudget;
    }

    private static int countNodes(List<BehaviorTreeNode> nodes) {
        int count = 0;
        for (var node : nodes) {
            count += 1 + countNodes(node.getChildren());
        }
        return count;
    }

    @Override
    public List<BehaviorStateEnum> runBranches(List<Supplier<BehaviorStateEnum>> branches, int maxConcurrency)
            throws InterruptedException {
        var stack = deadlines.get();
        long earliest = Long.MAX_VALUE;
        if (stack != null) {
            for (var deadline : stack) {
                if (deadline != null && deadline < earliest) {
                    earliest = deadline;
                }
            }
        }
        // 外层限时还在时，等待分支不能越过截止时间；没有限时传 -1
        var result = branchRunner.run(branches, maxConcurrency, earliest == Long.MAX_VALUE ? -1L : earliest);
        branchTimedOut.set(result.timedOut());
        return result.states();
    }

    @Override
    public boolean branchWaitTimedOut() {
        return Boolean.TRUE.equals(branchTimedOut.get());
    }

    private BehaviorNodeTrace trace(String nodeId) {
        return traces.computeIfAbsent(nodeId, key -> new BehaviorNodeTrace());
    }

    /**
     * 按比较方式比黑板值与配置值；类型对不上只把原因写进 reason，由调用方定节点状态，不打断整棵树。
     */
    private boolean compare(BehaviorCompareOpEnum op, Object actual, Object expect, String[] reason) {
        if (op == BehaviorCompareOpEnum.IS_EMPTY) {
            return isEmptyValue(actual);
        }
        if (op == BehaviorCompareOpEnum.IS_NOT_EMPTY) {
            return !isEmptyValue(actual);
        }
        if (actual == null) {
            reason[0] = "黑板值为空，无法参与 " + op.getName() + " 比较";
            return false;
        }
        if (op == BehaviorCompareOpEnum.CONTAINS || op == BehaviorCompareOpEnum.NOT_CONTAINS) {
            var contained = isContained(actual, expect);
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

    /** 集合/映射/数组按元素判空；空 List 的 toString 是「[]」，不能拿字符串判断 */
    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.toString().isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0;
        }
        return false;
    }

    private boolean isContained(Object actual, Object expect) {
        if (actual instanceof List<?> list) {
            return list.stream().anyMatch(item -> Objects.equals(String.valueOf(item), String.valueOf(expect)));
        }
        return String.valueOf(actual).contains(String.valueOf(expect));
    }
}
