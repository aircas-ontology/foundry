package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Decorator;
import com.github.silencesu.behavior3java.core.Tick;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

/**
 * 每个定义节点外面包一层 Guard，统一处理 enabled / skipIf，并记录节点状态。
 * <p>behavior3java 只关心返回状态，不维护逐节点结果；结果树靠这里记录的状态还原。
 * 状态与说明分开记：子节点写说明，Guard 只写状态，避免互相覆盖。</p>
 */
@Slf4j
public class OntologyGuardNode extends Decorator {

    private String nodeId;

    private boolean enabled = true;

    private String skipIf;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.enabled = !"false".equalsIgnoreCase(props.get("enabled"));
        this.skipIf = props.get("skipIf");
    }

    @Override
    public B3Status onTick(Tick tick) {
        var runtime = OntologyTreeNodes.runtime(tick);
        if (!enabled) {
            runtime.setNodeSkipped(nodeId, "节点未启用，按跳过处理");
            return B3Status.SUCCESS;
        }
        if (StringUtils.isNotBlank(skipIf)) {
            var hit = runtime.evaluateExpression(nodeId, skipIf);
            if (hit == null) {
                runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
                return B3Status.FAILURE;
            }
            if (hit) {
                runtime.setNodeSkipped(nodeId, "跳过条件成立");
                return B3Status.SUCCESS;
            }
        }
        var child = getChild();
        if (child == null) {
            runtime.setNodeMessage(nodeId, "节点没有可执行的子节点");
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            return B3Status.FAILURE;
        }
        B3Status status;
        try {
            status = child.execute(tick);
        } catch (Exception e) {
            // 节点异常只影响自己的结果，不能让整棵树抛出去拿不到逐节点说明
            var message = e instanceof BusinessException business ? business.getMessage() : e.getMessage();
            runtime.setNodeMessage(nodeId, "节点执行异常：" + message);
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            log.warn("行为树节点执行异常, nodeId={}", nodeId, e);
            return B3Status.FAILURE;
        }
        runtime.setNodeState(nodeId, OntologyTreeNodes.toState(status));
        return status;
    }
}
