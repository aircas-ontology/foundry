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
        // 前端按参数名传值，但 externalInputs 的键是参数 ID，需要按名称反向查找
        Map<String, Object> externalByName = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : nodePlan.getExternalInputs().entrySet()) {
            String name = entry.getValue();
            Object value = request.getExternalInputs().get(name);
            if (value != null) {
                externalByName.put(name, value);
            }
        }
        // 键必须用端口 ID，与 edgeTargets 的 sourceParamId 对齐
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : nodePlan.getExternalInputs().entrySet()) {
            String portId = entry.getKey();
            String externalName = entry.getValue();
            outputs.put(portId, externalByName.get(externalName));
        }
        request.record(nodePlan.getElId(), outputs);
        request.putProduced(nodePlan.getElId(), outputs);
        request.pushOutputs(nodePlan, outputs);
    }
}
