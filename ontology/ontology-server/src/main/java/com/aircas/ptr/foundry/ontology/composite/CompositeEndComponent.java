package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.yomahub.liteflow.core.NodeComponent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 函数编排"结束"节点。
 * <p>收集输入端口的值并产生输出，供编排的外部输出映射使用。</p>
 */
public class CompositeEndComponent extends NodeComponent {

    @Override
    public void process() {
        CompositeRuntimeRequest request = CompositeDataComponent.requestOf(this);
        CompositeNodePlanBO nodePlan = CompositeDataComponent.planOf(this);
        Map<String, Object> inputs = CompositeDataComponent.resolveInputs(this, request, nodePlan);
        request.record(nodePlan.getElId(), inputs);
        // END 节点的输入即为编排的输出，直接透传
        request.putProduced(nodePlan.getElId(), new LinkedHashMap<>(inputs));
        request.pushOutputs(nodePlan, inputs);
    }
}
