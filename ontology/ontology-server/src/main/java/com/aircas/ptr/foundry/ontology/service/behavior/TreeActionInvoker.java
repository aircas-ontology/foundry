package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorActionConfig;

/**
 * 行为节点真正执行一个已有行为的能力，由 OntologyActionService 实现：
 * 取绑定行为、按参数名从黑板取入参、调用行为执行链路落库、再把写回的属性刷新到黑板。
 */
@FunctionalInterface
public interface TreeActionInvoker {

    TreeActionOutcome invoke(Integer spaceId, BehaviorActionConfig config, TreeExecutionRuntime runtime);

    /**
     * @param state   节点状态
     * @param message 执行说明，成功时可为空
     */
    record TreeActionOutcome(BehaviorStateEnum state, String message) {
    }
}
