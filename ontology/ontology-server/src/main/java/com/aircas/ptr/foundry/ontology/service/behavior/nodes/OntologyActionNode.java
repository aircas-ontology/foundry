package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.param.BehaviorActionConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Action;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 行为树叶子节点：引用本空间内一个已发布行为，走行为执行链路真实落库。
 * <p>入参按参数名从黑板取，执行完把绑定对象属性的黑板键换成写回后的新值；重试与超时只保存不生效。</p>
 */
public class OntologyActionNode extends Action {

    private String nodeId;

    private BehaviorActionConfig config;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.config = OntologyTreeNodes.config(props, BehaviorActionConfig.class);
    }

    @Override
    public B3Status onTick(Tick tick) {
        var runtime = OntologyTreeNodes.runtime(tick);
        var state = runtime.executeAction(nodeId, config);
        if (config != null && (config.getTimeoutSeconds() != null || config.getRetryTimes() != null)) {
            runtime.appendNodeMessage(nodeId, "单轮执行：行为是真实落库的，重试会重复写一条数据，超时与重试配置不生效");
        }
        return OntologyTreeNodes.toB3(state);
    }
}
