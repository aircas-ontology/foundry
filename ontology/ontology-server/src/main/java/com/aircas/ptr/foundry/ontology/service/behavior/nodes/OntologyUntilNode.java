package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorUntilConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.BaseNode;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;
import org.apache.commons.lang.StringUtils;

/**
 * 直到成功 / 直到失败：在单次请求内同步重试子节点，直到达成或次数耗尽。
 * <p>maxTimes 是重试次数，空或 0 表示只跑一次；子节点行为是真实落库的，重试会重复写入，
 * 配置该节点时要确认被包裹的子树可以重复执行。重试次数同样受兜底上限约束。</p>
 * <p>配了持续执行条件时每次重试前重新求值，不成立就停止重试并按成功收尾；子节点被跳过不算达成，
 * 直接结束重试并按跳过上报，避免「什么都没执行」被当成目标已达成。</p>
 */
public class OntologyUntilNode extends Decorator {

    private String nodeId;

    private BehaviorNodeKindEnum kind;

    private BehaviorUntilConfig config;

    private String whileCondition;

    /** 被包裹子节点的定义标识，用来回查本轮是否被跳过 */
    private String childNodeId;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.kind = BehaviorNodeKindEnum.valueOf(props.get("kind"));
        this.config = OntologyTreeNodes.config(props, BehaviorUntilConfig.class);
        this.whileCondition = props.get("whileCondition");
        var childNodeIds = OntologyTreeNodes.stringList(props, "childNodeIds");
        this.childNodeId = childNodeIds.isEmpty() ? null : childNodeIds.get(0);
    }

    @Override
    public B3Status onTick(Tick tick) {
        var child = getChild();
        var runtime = OntologyTreeNodes.runtime(tick);
        if (child == null) {
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            runtime.setNodeMessage(nodeId, "直到节点没有可执行的子节点");
            return B3Status.FAILURE;
        }
        var retries = config == null || config.getMaxTimes() == null || config.getMaxTimes() <= 0
                ? 0 : config.getMaxTimes();
        var wantAttempts = (long) retries + 1;
        var maxAttempts = Math.min(wantAttempts, OntologyTreeNodes.MAX_LOOP_ITERATIONS);
        var attempt = 0;
        var timedOut = false;
        var budgetShort = false;
        while (attempt < maxAttempts) {
            // 外层限时已过就不再重试，避免超时后继续产生写操作
            if (runtime.deadlineExceeded()) {
                timedOut = true;
                break;
            }
            if (!runtime.tryConsumeStep()) {
                budgetShort = true;
                break;
            }
            // 持续执行条件每次重试前重新求值，与重复节点的循环口径保持一致
            if (StringUtils.isNotBlank(whileCondition)) {
                var hit = runtime.evaluateExpression(nodeId, whileCondition);
                if (hit == null) {
                    runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
                    return B3Status.FAILURE;
                }
                if (!hit) {
                    runtime.setNodeState(nodeId, BehaviorStateEnum.SUCCESS);
                    runtime.appendNodeMessage(nodeId, "持续执行条件不成立，停止重试，已尝试 " + attempt + " 次");
                    return B3Status.SUCCESS;
                }
            }
            attempt++;
            var childStatus = child.execute(tick);
            if (childStatus == B3Status.RUNNING) {
                runtime.setNodeState(nodeId, BehaviorStateEnum.RUNNING);
                return B3Status.RUNNING;
            }
            // 跳过不等于达成：子节点本轮没有执行，不能再按「直到成功/失败」判断结果
            if (StringUtils.isNotBlank(childNodeId) && runtime.isNodeSkipped(childNodeId)) {
                runtime.setNodeSkipped(nodeId, "子节点被跳过，停止重试");
                return B3Status.SUCCESS;
            }
            var achieved = kind == BehaviorNodeKindEnum.UNTIL_SUCCESS
                    ? childStatus == B3Status.SUCCESS
                    : childStatus == B3Status.FAILURE;
            if (achieved) {
                runtime.setNodeState(nodeId, BehaviorStateEnum.SUCCESS);
                return B3Status.SUCCESS;
            }
        }
        var state = OntologyTreeNodes.singleTickState(config == null ? null : config.getFinishState());
        runtime.setNodeState(nodeId, state);
        if (timedOut) {
            runtime.setNodeMessage(nodeId, "已超过限时，停止重试，按收尾状态上报");
        } else if (budgetShort) {
            runtime.setNodeMessage(nodeId, "尝试 " + attempt + " 次后达到本次执行的总步数上限 "
                    + runtime.getStepBudget() + "，按收尾状态上报");
        } else {
            runtime.setNodeMessage(nodeId, "尝试 " + attempt + " 次仍未达成"
                    + (wantAttempts > maxAttempts ? "（达到单次执行上限 " + OntologyTreeNodes.MAX_LOOP_ITERATIONS + "）" : "")
                    + "，按收尾状态上报");
        }
        return OntologyTreeNodes.toB3(state);
    }
}
