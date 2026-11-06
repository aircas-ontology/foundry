package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorBlackboardConditionConfig;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorConditionConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Condition;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 条件叶子节点：黑板条件按配置比较，表达式条件用 SpEL 求值，成立即成功。
 * <p>求值本身出错（表达式非法、黑板值类型对不上）按失败处理，并把原因写进节点说明。</p>
 */
public class OntologyConditionNode extends Condition {

    private String nodeId;

    private BehaviorNodeKindEnum kind;

    private BehaviorBlackboardConditionConfig blackboardConfig;

    private BehaviorConditionConfig expressionConfig;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.kind = BehaviorNodeKindEnum.valueOf(props.get("kind"));
        if (kind == BehaviorNodeKindEnum.BLACKBOARD_CONDITION) {
            this.blackboardConfig = OntologyTreeNodes.config(props, BehaviorBlackboardConditionConfig.class);
        } else {
            this.expressionConfig = OntologyTreeNodes.config(props, BehaviorConditionConfig.class);
        }
    }

    @Override
    public B3Status onTick(Tick tick) {
        var runtime = OntologyTreeNodes.runtime(tick);
        if (blackboardConfig == null && expressionConfig == null) {
            runtime.setNodeMessage(nodeId, "条件节点缺少配置");
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            return B3Status.FAILURE;
        }
        var hit = kind == BehaviorNodeKindEnum.BLACKBOARD_CONDITION
                ? runtime.evaluateBlackboardCondition(nodeId, blackboardConfig)
                : runtime.evaluateExpression(nodeId, expressionConfig.getExpression());
        var state = hit == null ? BehaviorStateEnum.FAILURE
                : (hit ? BehaviorStateEnum.SUCCESS : BehaviorStateEnum.FAILURE);
        runtime.setNodeState(nodeId, state);
        return OntologyTreeNodes.toB3(state);
    }
}
