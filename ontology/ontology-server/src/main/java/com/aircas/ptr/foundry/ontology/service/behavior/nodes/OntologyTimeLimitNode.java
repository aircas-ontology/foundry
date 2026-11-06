package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.param.BehaviorTimeLimitConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 限时节点：单轮执行为同步串行，子节点跑完才返回，限时不会触发。
 * <p>行为已经真实落库，事后把结果改写成超时会造成与数据不一致，所以先只保存配置。</p>
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
        var child = getChild();
        if (child == null) {
            return B3Status.ERROR;
        }
        var status = child.execute(tick);
        var runtime = OntologyTreeNodes.runtime(tick);
        runtime.setNodeState(nodeId, OntologyTreeNodes.toState(status));
        if (config != null) {
            runtime.setNodeMessage(nodeId, "单轮执行：单轮执行为同步串行，限时不生效");
        }
        return status;
    }
}
