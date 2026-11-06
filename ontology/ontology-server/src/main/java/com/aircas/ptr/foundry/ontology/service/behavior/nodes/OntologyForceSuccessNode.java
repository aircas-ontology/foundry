package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 强制成功：子节点失败时改成成功，其余状态原样透传。
 * <p>不用库里的 Succeeder，它把 RUNNING 也改成 SUCCESS，多轮续跑时语义会错。</p>
 */
public class OntologyForceSuccessNode extends Decorator {

    private String nodeId;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        this.nodeId = OntologyTreeNodes.props(cfg).get("nodeId");
    }

    @Override
    public B3Status onTick(Tick tick) {
        var child = getChild();
        if (child == null) {
            return B3Status.ERROR;
        }
        var childStatus = child.execute(tick);
        var status = childStatus == B3Status.FAILURE ? B3Status.SUCCESS : childStatus;
        OntologyTreeNodes.runtime(tick).setNodeState(nodeId, OntologyTreeNodes.toState(status));
        return status;
    }
}
