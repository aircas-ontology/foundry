package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.yomahub.liteflow.core.NodeComponent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 函数编排"开始"节点。
 * <p>把编排的外部输入作为本节点的输出传递给下游。</p>
 */
public class CompositeStartComponent extends NodeComponent {

    @Override
    public void process() {
        CompositeRuntimeRequest request = CompositeDataComponent.requestOf(this);
        CompositeNodePlanBO nodePlan = CompositeDataComponent.planOf(this);
        // START 节点的输出 = 编排的外部输入
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : nodePlan.getExternalInputs().entrySet()) {
            String externalName = entry.getValue();
            Object value = request.getExternalInputs().get(externalName);
            outputs.put(externalName, value);
        }
        request.record(nodePlan.getElId(), outputs);
        request.putProduced(nodePlan.getElId(), outputs);
        request.pushOutputs(nodePlan, outputs);
    }
}
