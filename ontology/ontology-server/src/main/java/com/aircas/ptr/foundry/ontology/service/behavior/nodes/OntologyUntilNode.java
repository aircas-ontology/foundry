package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorUntilConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 直到成功 / 直到失败：单轮只尝试一次，未达成时按配置的收尾状态上报。
 * <p>重试次数要跨 Tick 计数，P2 接多轮 tick 后再打开。</p>
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
        if (child == null) {
            return B3Status.ERROR;
        }
        var childStatus = child.execute(tick);
        var runtime = OntologyTreeNodes.runtime(tick);
        var achieved = kind == BehaviorNodeKindEnum.UNTIL_SUCCESS
                ? childStatus == B3Status.SUCCESS
                : childStatus == B3Status.FAILURE;
        BehaviorStateEnum state;
        if (achieved) {
            state = BehaviorStateEnum.SUCCESS;
        } else {
            state = config == null || config.getFinishState() == null
                    ? BehaviorStateEnum.FAILURE : config.getFinishState();
            runtime.setNodeMessage(nodeId, "单轮执行：只尝试一次，未达成即按收尾状态上报");
        }
        runtime.setNodeState(nodeId, state);
        return OntologyTreeNodes.toB3(state);
    }
}
