package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorTimeLimitConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 限时节点：超过时长后不再发起新的行为，并按配置的超时状态上报。
 * <p>已经开始的行为无法安全中断（它可能正在落库），所以限时是协作式的：子树里的行为节点在每次执行前
 * 检查截止时间，已超时就直接失败，避免超时后继续产生写操作；子节点跑完后只要已过期，本节点统一改报超时状态。</p>
 */
public class OntologyTimeLimitNode extends Decorator {

    private String nodeId;

    private BehaviorTimeLimitConfig config;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.config = OntologyTreeNodes.config(props, BehaviorTimeLimitConfig.class);
    }

    @Override
    public B3Status onTick(Tick tick) {
        var runtime = OntologyTreeNodes.runtime(tick);
        var child = getChild();
        if (child == null) {
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            runtime.setNodeMessage(nodeId, "限时节点没有可执行的子节点");
            return B3Status.FAILURE;
        }
        var limitSeconds = config == null ? null : config.getLimitSeconds();
        Long deadline = limitSeconds == null ? null : System.currentTimeMillis() + limitSeconds * 1000L;
        B3Status status;
        if (deadline == null) {
            status = child.execute(tick);
        } else {
            runtime.pushDeadline(deadline);
            try {
                status = child.execute(tick);
            } finally {
                runtime.popDeadline();
            }
            if (System.currentTimeMillis() > deadline) {
                var timeoutState = OntologyTreeNodes.singleTickState(config.getTimeoutState());
                runtime.setNodeState(nodeId, timeoutState);
                runtime.setNodeMessage(nodeId, "已超过限时 " + limitSeconds
                        + " 秒：已开始的行为无法中断，超时后不再执行后续行为，本节点按"
                        + timeoutState.getName() + "上报");
                return OntologyTreeNodes.toB3(timeoutState);
            }
        }
        runtime.setNodeState(nodeId, OntologyTreeNodes.toState(status));
        return status;
    }
}
