package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorCooldownConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 冷却节点：距上次执行不足间隔时不再执行子节点，直接上报配置的结果状态。
 * <p>上次执行时间按「树 + 节点 + 执行实例主键」持久化，跨请求、跨实例有效；
 * 占位发生在子节点执行前，子节点失败也不退还冷却时间，避免失败后立即重试打满下游。</p>
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
        var runtime = OntologyTreeNodes.runtime(tick);
        var child = getChild();
        if (child == null) {
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            runtime.setNodeMessage(nodeId, "冷却节点没有可执行的子节点");
            return B3Status.FAILURE;
        }
        var intervalSeconds = config == null ? null : config.getIntervalSeconds();
        if (intervalSeconds != null && !runtime.tryRunCooldown(nodeId, intervalSeconds)) {
            var configured = config.getHitResult() == null ? BehaviorStateEnum.FAILURE : config.getHitResult();
            // 单轮引擎不会恢复 RUNNING，历史定义里配了也按失败上报
            var hitResult = OntologyTreeNodes.singleTickState(configured);
            runtime.setNodeState(nodeId, hitResult);
            runtime.setNodeMessage(nodeId, "冷却中：距上次执行未满 " + intervalSeconds + " 秒，本次不执行子节点"
                    + (configured == BehaviorStateEnum.RUNNING ? "（配置的 RUNNING 在单轮执行里按 FAILURE 上报）" : ""));
            return OntologyTreeNodes.toB3(hitResult);
        }
        var status = child.execute(tick);
        runtime.setNodeState(nodeId, OntologyTreeNodes.toState(status));
        return status;
    }
}
