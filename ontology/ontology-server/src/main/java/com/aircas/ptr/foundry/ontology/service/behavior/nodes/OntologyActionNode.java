package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorActionConfig;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.Action;
import com.github.silencesu.behavior3java.core.Tick;

/**
 * 行为树叶子节点：引用本空间内一个已发布行为，走行为执行链路真实落库。
 * <p>入参按参数名从黑板取，执行完把绑定对象属性的黑板键换成写回后的新值。
 * 配置的失败重试在单次执行内同步重试；重试会重复落库，配置时要确认行为本身可以重复执行。
 * 外层限时节点已超时时不再发起新的行为，避免超时后继续写数据。</p>
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
        var retries = config == null || config.getRetryTimes() == null || config.getRetryTimes() <= 0
                ? 0 : config.getRetryTimes();
        var wantAttempts = (long) retries + 1;
        var maxAttempts = Math.min(wantAttempts, OntologyTreeNodes.MAX_LOOP_ITERATIONS);
        var attempt = 0;
        var state = BehaviorStateEnum.FAILURE;
        while (attempt < maxAttempts) {
            if (runtime.deadlineExceeded()) {
                runtime.setNodeMessage(nodeId, "已超过限时，本次不执行行为");
                return B3Status.FAILURE;
            }
            attempt++;
            state = runtime.executeAction(nodeId, config);
            if (state != BehaviorStateEnum.FAILURE) {
                break;
            }
        }
        if (attempt > 1) {
            var capped = wantAttempts > maxAttempts ? "（达到单次执行上限 " + OntologyTreeNodes.MAX_LOOP_ITERATIONS + "）" : "";
            runtime.appendNodeMessage(nodeId, state == BehaviorStateEnum.FAILURE
                    ? "已重试 " + (attempt - 1) + " 次仍失败" + capped
                    : "第 " + attempt + " 次尝试成功");
        }
        if (config != null && config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0) {
            // 保存期已拒绝新配置，这里只兜历史定义
            runtime.appendNodeMessage(nodeId, "行为节点不支持超时，timeoutSeconds 已忽略；需要限制后续步骤请用限时节点");
        }
        return OntologyTreeNodes.toB3(state);
    }
}
