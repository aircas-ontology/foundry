package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorLoopModeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorRepeaterConfig;
import com.aircas.ptr.foundry.ontology.service.behavior.BehaviorTreeRuntime;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.BaseNode;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;
import org.apache.commons.lang.StringUtils;

/**
 * 重复节点：在单次请求内真实循环子节点。
 * <p>固定次数按 times 循环；按条件与无限循环都按 whileCondition 判断是否继续，所以保存期要求填持续执行条件。
 * 子节点失败立即结束整轮循环；循环次数有兜底上限，防止条件永远成立时单次请求停不下来。</p>
 */
public class OntologyRepeaterNode extends Decorator {

    private String nodeId;

    private BehaviorRepeaterConfig config;

    private String whileCondition;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.config = OntologyTreeNodes.config(props, BehaviorRepeaterConfig.class);
        this.whileCondition = props.get("whileCondition");
    }

    @Override
    public B3Status onTick(Tick tick) {
        var child = getChild();
        var runtime = OntologyTreeNodes.runtime(tick);
        if (child == null) {
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            runtime.setNodeMessage(nodeId, "重复节点没有可执行的子节点");
            return B3Status.FAILURE;
        }
        var loopMode = config == null || config.getLoopMode() == null
                ? BehaviorLoopModeEnum.FIXED_TIMES : config.getLoopMode();
        return loopMode == BehaviorLoopModeEnum.FIXED_TIMES
                ? repeatFixedTimes(tick, child, runtime)
                : repeatWhileCondition(tick, child, runtime);
    }

    private B3Status repeatFixedTimes(Tick tick, BaseNode child, BehaviorTreeRuntime runtime) {
        var times = config == null ? null : config.getTimes();
        if (times == null || times <= 0) {
            // 保存期已拦，这里只兜历史数据，避免 null 被当成无限循环
            runtime.setNodeMessage(nodeId, "未配置有效循环次数，按单次执行");
            return finish(runtime, child.execute(tick));
        }
        var executed = Math.min(times, OntologyTreeNodes.MAX_LOOP_ITERATIONS);
        for (var i = 0; i < executed; i++) {
            if (runtime.deadlineExceeded()) {
                return stopByDeadline(runtime, i);
            }
            // 固定次数也可以配持续执行条件，条件先不成立就提前收尾
            if (StringUtils.isNotBlank(whileCondition)) {
                var hit = runtime.evaluateExpression(nodeId, whileCondition);
                if (hit == null) {
                    runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
                    return B3Status.FAILURE;
                }
                if (!hit) {
                    runtime.setNodeState(nodeId, BehaviorStateEnum.SUCCESS);
                    runtime.setNodeMessage(nodeId, "已执行 " + i + " 次，持续执行条件不成立，提前结束循环");
                    return B3Status.SUCCESS;
                }
            }
            var status = child.execute(tick);
            if (status != B3Status.SUCCESS) {
                runtime.setNodeMessage(nodeId, "第 " + (i + 1) + " 次循环未成功，提前结束");
                return finish(runtime, status);
            }
        }
        if (times > executed) {
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            runtime.setNodeMessage(nodeId, "循环次数超过单次执行上限 " + OntologyTreeNodes.MAX_LOOP_ITERATIONS
                    + "，未能按配置次数完成，按失败处理");
            return B3Status.FAILURE;
        }
        runtime.setNodeState(nodeId, BehaviorStateEnum.SUCCESS);
        return B3Status.SUCCESS;
    }

    private B3Status repeatWhileCondition(Tick tick, BaseNode child, BehaviorTreeRuntime runtime) {
        if (StringUtils.isBlank(whileCondition)) {
            runtime.setNodeMessage(nodeId, "循环模式缺少持续执行条件，按单次执行");
            return finish(runtime, child.execute(tick));
        }
        for (var i = 0; i < OntologyTreeNodes.MAX_LOOP_ITERATIONS; i++) {
            if (runtime.deadlineExceeded()) {
                return stopByDeadline(runtime, i);
            }
            var hit = runtime.evaluateExpression(nodeId, whileCondition);
            if (hit == null) {
                runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
                return B3Status.FAILURE;
            }
            if (!hit) {
                runtime.setNodeState(nodeId, BehaviorStateEnum.SUCCESS);
                return B3Status.SUCCESS;
            }
            var status = child.execute(tick);
            if (status != B3Status.SUCCESS) {
                runtime.setNodeMessage(nodeId, "第 " + (i + 1) + " 轮循环未成功，提前结束");
                return finish(runtime, status);
            }
        }
        runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
        runtime.setNodeMessage(nodeId, "循环次数达到单次执行上限 " + OntologyTreeNodes.MAX_LOOP_ITERATIONS
                + " 且持续执行条件仍成立，按失败处理");
        return B3Status.FAILURE;
    }

    private B3Status finish(BehaviorTreeRuntime runtime, B3Status status) {
        runtime.setNodeState(nodeId, OntologyTreeNodes.toState(status));
        return status;
    }

    /** 外层限时已过：停止循环，外层限时节点会把结果改报为超时状态 */
    private B3Status stopByDeadline(BehaviorTreeRuntime runtime, int executed) {
        runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
        runtime.setNodeMessage(nodeId, "已执行 " + executed + " 轮后超过限时，提前结束循环");
        return B3Status.FAILURE;
    }
}
