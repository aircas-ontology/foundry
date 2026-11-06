package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorParallelPolicyEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorParallelConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Composite;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 并行节点：behavior3java 没有内置并行容器，这里按配置的成败判定策略补齐。
 * <p>单轮引擎串行跑完所有子节点后再判定，最大并发数只保存不生效。</p>
 */
public class OntologyParallelNode extends Composite {

    private String nodeId;

    private BehaviorParallelConfig config;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.config = OntologyTreeNodes.config(props, BehaviorParallelConfig.class);
    }

    @Override
    public B3Status onTick(Tick tick) {
        var successCount = 0;
        var failureCount = 0;
        for (var i = 0; i < getChildCount(); i++) {
            var status = getChild(i).execute(tick);
            if (status == B3Status.SUCCESS) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        var allSuccess = failureCount == 0;
        var anySuccess = successCount > 0;
        var allFailure = successCount == 0;
        var anyFailure = failureCount > 0;
        var policy = config == null || config.getPolicy() == null
                ? BehaviorParallelPolicyEnum.ALL_SUCCESS : config.getPolicy();
        var hit = switch (policy) {
            case ALL_SUCCESS -> allSuccess;
            case ANY_SUCCESS -> anySuccess;
            case ALL_FAILURE -> allFailure;
            case ANY_FAILURE -> anyFailure;
        };
        var state = hit ? BehaviorStateEnum.SUCCESS : BehaviorStateEnum.FAILURE;
        var runtime = OntologyTreeNodes.runtime(tick);
        runtime.setNodeState(nodeId, state);
        if (config != null && config.getMaxConcurrency() != null) {
            runtime.setNodeMessage(nodeId, "单轮执行：子节点串行执行，最大并发数不生效");
        }
        return OntologyTreeNodes.toB3(state);
    }
}
