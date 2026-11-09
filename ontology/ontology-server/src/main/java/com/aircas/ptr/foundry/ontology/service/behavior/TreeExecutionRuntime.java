package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorCompareOpEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorActionConfig;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorBlackboardConditionConfig;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorBlackboardItem;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorTreeDefinition;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorTreeNode;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 一次树执行（单条实例）的运行时：黑板、节点执行痕迹、条件求值。
 * <p>黑板是节点唯一的数据来源，变量默认值打底、执行入参覆盖；行为执行后按黑板项绑定的对象属性回填。
 * 每次执行独立一个实例，behavior3java 的 Blackboard 只承载库内置节点的记忆状态。
 * 并行节点的多个分支共用一个运行时，所以黑板与节点痕迹按并发访问处理。</p>
 */
@Slf4j
public class TreeExecutionRuntime implements BehaviorTreeRuntime {

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    /** 黑板是 Map，表达式按黑板键取值靠它；禁写，防止表达式把黑板当变量池改 */
    private static final MapAccessor MAP_ACCESSOR = new MapAccessor(false);

    private final BehaviorTreeDefinition definition;

    /** 树 api，冷却节点按「树 + 节点 + 实例」定位持久化状态 */
    private final String treeApi;

    private final Object entityPrimaryKey;

    /** 按对象分开给的实例主键，树里多个对象类型都没配节点实例键时用；对象优先于 entityPrimaryKey */
    private final Map<String, Object> entityPrimaryKeys;

    /** 执行级默认的分组表行主键，节点没配黑板键时用它 */
    private final Object dataPrimaryKey;

    /** 执行级默认的关系对端实体主键，节点没配黑板键时用它 */
    private final Object linkEntityPrimaryKey;

    private final Integer spaceId;

    private final TreeCooldownTracker cooldownTracker;

    private final TreeBranchRunner branchRunner;

    private final TreeActionInvoker actionInvoker;

    /** 黑板键名 -> 当前值；并行分支会同时读写，用同步包装保留插入顺序 */
    private final Map<String, Object> blackboard = Collections.synchronizedMap(new LinkedHashMap<>());

    /** 黑板键名 -> 黑板项声明，行为写后按 bindPropertyUniqueIdentifier 找 */
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
                                Map<String, Object> inputs, Object dataPrimaryKey, Object linkEntityPrimaryKey,
                                Integer spaceId, TreeCooldownTracker cooldownTracker,
                                TreeBranchRunner branchRunner, TreeActionInvoker actionInvoker) {
        this.definition = definition;
        this.treeApi = treeApi;
        this.entityPrimaryKey = entityPrimaryKey;
        this.entityPrimaryKeys = entityPrimaryKeys == null ? Map.of() : entityPrimaryKeys;
        this.dataPrimaryKey = dataPrimaryKey;
        this.linkEntityPrimaryKey = linkEntityPrimaryKey;
        this.spaceId = spaceId;
        this.cooldownTracker = cooldownTracker;
        this.branchRunner = branchRunner;
        this.actionInvoker = actionInvoker;
        this.stepBudget = OntologyTreeNodes.MAX_LOOP_ITERATIONS * (countNodes(definition.getRoots()) + 1);
        definition.getBlackboard().forEach(item -> blackboardItems.put(item.getKey(), item));
        definition.getVariables().forEach(variable -> blackboard.put(variable.getName(), variable.getDefaultValue()));
        if (inputs != null) {
            for (var entry : inputs.entrySet()) {
                PreconditionUtils.checkArgument(blackboard.containsKey(entry.getKey()),
                        "执行入参 " + entry.getKey() + " 不是本树声明的黑板键", HttpStatus.BAD_REQUEST);
                var item = blackboardItems.get(entry.getKey());
                // 绑了对象属性的键由行为写入，再接受外部入参会出现「传了但马上被覆盖」的假象
                PreconditionUtils.checkArgument(item == null || StringUtils.isBlank(item.getBindPropertyUniqueIdentifier()),
                        "黑板键 " + entry.getKey() + " 绑的是对象属性，值由行为写入，不接受执行入参", HttpStatus.BAD_REQUEST);
                blackboard.put(entry.getKey(), entry.getValue());
            }
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
    public Object entityPrimaryKeyFor(String objectId) {
        if (objectId != null) {
            var value = entityPrimaryKeys.get(objectId);
            if (value != null) {
                return value;
            }
        }
        return entityPrimaryKey;
    }

    /** 只取按对象传的实例主键，不回退执行级主键：关系对端必须是那个对象自己的实例 */
    public Object mappedEntityPrimaryKey(String objectId) {
        return objectId == null ? null : entityPrimaryKeys.get(objectId);
    }

    public Object getDataPrimaryKey() {
        return dataPrimaryKey;
    }

    public Object getLinkEntityPrimaryKey() {
        return linkEntityPrimaryKey;
    }

    @Override
    public Integer getSpaceId() {
        return spaceId;
    }

    @Override
    public Object blackboardValue(String key) {
        return blackboard.get(key);
    }

    @Override
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
            var outcome = actionInvoker.invoke(spaceId, config, this);
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
