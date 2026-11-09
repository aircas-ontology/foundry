package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorUntilConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.BaseNode;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 直到成功 / 直到失败：在单次请求内同步重试子节点，直到达成或次数耗尽。
 * <p>maxTimes 是重试次数，空或 0 表示只跑一次；子节点行为是真实落库的，重试会重复写入，
 * 配置该节点时要确认被包裹的子树可以重复执行。重试次数同样受兜底上限约束。</p>
 */
public class OntologyUntilNode extends Decorator {

    private String nodeId;

    private BehaviorNodeKindEnum kind;

    private BehaviorUntilConfig config;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.kind = BehaviorNodeKindEnum.valueOf(props.get("kind"));
        this.config = OntologyTreeNodes.config(props, BehaviorUntilConfig.class);
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
        while (attempt < maxAttempts) {
            // 外层限时已过就不再重试，避免超时后继续产生写操作
            if (runtime.deadlineExceeded()) {
                timedOut = true;
                break;
            }
            attempt++;
            var childStatus = child.execute(tick);
            if (childStatus == B3Status.RUNNING) {
                runtime.setNodeState(nodeId, BehaviorStateEnum.RUNNING);
                return B3Status.RUNNING;
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
        } else {
            runtime.setNodeMessage(nodeId, "尝试 " + attempt + " 次仍未达成"
                    + (wantAttempts > maxAttempts ? "（达到单次执行上限 " + OntologyTreeNodes.MAX_LOOP_ITERATIONS + "）" : "")
                    + "，按收尾状态上报");
        }
        return OntologyTreeNodes.toB3(state);
    }
}
