package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.param.BehaviorRepeaterConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 重复节点：单轮执行只跑一遍子节点，循环次数与循环模式只保存不生效。
 * <p>循环要跨 Tick 记住已执行次数，P2 接多轮 tick 后再打开。</p>
 */
public class OntologyRepeaterNode extends Decorator {

    private String nodeId;

    private BehaviorRepeaterConfig config;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.config = OntologyTreeNodes.config(props, BehaviorRepeaterConfig.class);
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
            runtime.setNodeMessage(nodeId, "单轮执行：本节点的跨 Tick 配置未生效");
        }
        return status;
    }
}
