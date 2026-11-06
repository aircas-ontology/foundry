package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.param.BehaviorCooldownConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 冷却节点：单轮执行按未命中处理，直接跑子节点。
 * <p>冷却期要记住上次执行时间，而变量只有 RUNTIME 作用域、不跨执行持久，只能等 P2 补状态持久化。</p>
 */
public class OntologyCooldownNode extends Decorator {

    private String nodeId;

    private BehaviorCooldownConfig config;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.config = OntologyTreeNodes.config(props, BehaviorCooldownConfig.class);
    }

    @Override
    public B3Status onTick(Tick tick) {
        var child = getChild();
        if (child == null) {
            return B3Status.ERROR;
        }
        var status = child.execute(tick);
        var runtime = OntologyTreeNodes.runtime(tick);
        runtime.setNodeState(nodeId, OntologyTreeNodes.toState(status));
        if (config != null) {
            runtime.setNodeMessage(nodeId, "单轮执行：变量不跨执行持久，冷却期无法记住上次执行时间，按未命中处理");
        }
        return status;
    }
}
