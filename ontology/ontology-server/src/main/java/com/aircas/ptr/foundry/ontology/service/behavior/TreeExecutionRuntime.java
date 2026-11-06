package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorCompareOpEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorActionConfig;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorBlackboardConditionConfig;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorBlackboardItem;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorTreeDefinition;
import com.github.silencesu.behavior3java.core.Blackboard;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一次树执行（单条实例）的运行时：黑板、节点执行痕迹、条件求值。
 * <p>黑板是节点唯一的数据来源，变量默认值打底、执行入参覆盖；行为执行后按黑板项绑定的对象属性回填。
 * 每次执行独立一个实例，behavior3java 的 Blackboard 只承载库内置节点的记忆状态。</p>
 */
@Slf4j
public class TreeExecutionRuntime implements BehaviorTreeRuntime {

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    /** 黑板是 Map，表达式按黑板键取值靠它；禁写，防止表达式把黑板当变量池改 */
    private static final MapAccessor MAP_ACCESSOR = new MapAccessor(false);

    private final BehaviorTreeDefinition definition;

    private final Object entityPrimaryKey;

    private final Integer spaceId;

    private final TreeActionInvoker actionInvoker;

    /** 黑板键名 -> 当前值 */
    private final Map<String, Object> blackboard = new LinkedHashMap<>();

    /** 黑板键名 -> 黑板项声明，行为写后按 bindPropertyUniqueIdentifier 找 */
    private final Map<String, BehaviorBlackboardItem> blackboardItems = new LinkedHashMap<>();

    /** 节点标识 -> 执行痕迹 */
    private final Map<String, BehaviorNodeTrace> traces = new LinkedHashMap<>();

    private final Blackboard behaviorBlackboard = new Blackboard();

    public TreeExecutionRuntime(BehaviorTreeDefinition definition, Object entityPrimaryKey, Map<String, Object> inputs,
                                Integer spaceId, TreeActionInvoker actionInvoker) {
        this.definition = definition;
        this.entityPrimaryKey = entityPrimaryKey;
        this.spaceId = spaceId;
        this.actionInvoker = actionInvoker;
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

    public Map<String, Object> getBlackboard() {
        return blackboard;
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
        trace.setMessage(StringUtils.isBlank(trace.getMessage()) ? message : trace.getMessage() + "；" + message);
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

    private boolean isEmptyValue(Object value) {
        return value == null || StringUtils.isBlank(String.valueOf(value));
    }

    private boolean isContained(Object actual, Object expect) {
        if (actual instanceof List<?> list) {
            return list.stream().anyMatch(item -> Objects.equals(String.valueOf(item), String.valueOf(expect)));
        }
        return String.valueOf(actual).contains(String.valueOf(expect));
    }
}
