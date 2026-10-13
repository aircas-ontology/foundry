package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.yomahub.liteflow.core.NodeBooleanComponent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 函数编排"条件分支"节点。
 * <p>按 {@code branchConfig.expression} 判断走真支路还是假支路，并把被判断的值透传到对应出参
 * （出参端口 id 为 {@code true}/{@code false}，与画布虚拟端口一致），供下游连线取值。</p>
 */
public class CompositeBranchComponent extends NodeBooleanComponent {

    @Override
    public boolean processBoolean() {
        CompositeRuntimeRequest request = CompositeDataComponent.requestOf(this);
        CompositeNodePlanBO nodePlan = CompositeDataComponent.planOf(this);
        Map<String, Object> inputs = CompositeDataComponent.resolveInputs(this, request, nodePlan);
        request.record(nodePlan.getElId(), inputs);
        boolean outcome = CompositeExpressionEvaluator.evaluate(nodePlan.getBranchExpression(), inputs);
        String port = outcome ? "true" : "false";
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put(port, passThrough(nodePlan.getBranchExpression(), inputs));
        request.putProduced(nodePlan.getElId(), outputs);
        request.pushOutputs(nodePlan, outputs);
        return outcome;
    }

    /** 取表达式根变量对应的值，取不到时退化为第一个非空入参 */
    private Object passThrough(String expression, Map<String, Object> inputs) {
        String root = CompositeExpressionEvaluator.root(expression);
        if (root != null && inputs.containsKey(root)) {
            return inputs.get(root);
        }
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            if (entry.getValue() != null) {
                return entry.getValue();
            }
        }
        return null;
    }
}
