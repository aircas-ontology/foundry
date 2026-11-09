package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.service.behavior.BehaviorTreeRuntime;
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
 * <p>跳过不是「成功」：选择节点要求子节点没成功才会继续看下一个分支，所以跳过的节点在选择节点里按失败返回；
 * 顺序节点里按成功放行；并行节点里也按成功返回，由并行节点根据 skipped 标记把它排除在策略统计外。</p>
 */
@Slf4j
public class OntologyGuardNode extends Decorator {

    private String nodeId;

    private boolean enabled = true;

    private String skipIf;

    private String whileCondition;

    /** 父定义节点的类型，决定「跳过」返回什么状态；根节点为空 */
    private BehaviorNodeKindEnum parentKind;

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.enabled = !"false".equalsIgnoreCase(props.get("enabled"));
        this.skipIf = props.get("skipIf");
        this.whileCondition = props.get("whileCondition");
        var parent = props.get("parentKind");
        this.parentKind = StringUtils.isBlank(parent) ? null : BehaviorNodeKindEnum.valueOf(parent);
    }

    @Override
    public B3Status onTick(Tick tick) {
        var runtime = OntologyTreeNodes.runtime(tick);
        // 循环与重入会让同一节点执行多轮，上一轮的状态/跳过标记/说明不能带到本轮结果里
        runtime.resetNodeTrace(nodeId);
        if (!enabled) {
            return skip(runtime, "节点未启用，按跳过处理");
        }
        if (StringUtils.isNotBlank(skipIf)) {
            var hit = runtime.evaluateExpression(nodeId, skipIf);
            if (hit == null) {
                runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
                return B3Status.FAILURE;
            }
            if (hit) {
                return skip(runtime, "跳过条件成立");
            }
        }
        // 持续执行条件在单轮里作为进入条件：不成立就不执行该节点；循环节点自己还会每轮重新求值
        if (StringUtils.isNotBlank(whileCondition)) {
            var hit = runtime.evaluateExpression(nodeId, whileCondition);
            if (hit == null) {
                runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
                return B3Status.FAILURE;
            }
            if (!hit) {
                return skip(runtime, "持续执行条件不成立");
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
        // 并行节点「所有分支都被跳过」时会让节点自己标记 skipped，同样要按父节点类型决定返回状态
        if (runtime.isNodeSkipped(nodeId) && parentKind == BehaviorNodeKindEnum.SELECTOR) {
            return B3Status.FAILURE;
        }
        return status;
    }

    /**
     * 跳过统一走这里：痕迹记成功 + skipped（结果树据此展示「跳过」），
     * 返回给父节点的状态按父节点类型决定，避免跳过被选择节点当成一次成功而短路。
     */
    private B3Status skip(BehaviorTreeRuntime runtime, String message) {
        runtime.setNodeSkipped(nodeId, message);
        return parentKind == BehaviorNodeKindEnum.SELECTOR ? B3Status.FAILURE : B3Status.SUCCESS;
    }
}
