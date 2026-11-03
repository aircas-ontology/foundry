package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.param.CompositeLoopConfig;
import com.yomahub.liteflow.core.NodeForComponent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 函数编排"循环"节点（固定次数模式）。
 * <p>{@code loopConfig.mode = times} 时编译为 LiteFlow 的 FOR 组件，按 {@code times} 次数驱动循环体；
 * 次数缺失或为负按 0 次处理，与原型行为一致。</p>
 * <p>本组件只执行一次（取次数），循环体内"来自循环体外的上游取值"由每轮先执行的
 * {@link CompositeLoopPreloadComponent} 重放。</p>
 */
public class CompositeForComponent extends NodeForComponent {

    @Override
    public int processFor() {
        CompositeRuntimeRequest request = CompositeDataComponent.requestOf(this);
        CompositeNodePlanBO nodePlan = CompositeDataComponent.planOf(this);
        Map<String, Object> inputs = CompositeDataComponent.resolveInputs(this, request, nodePlan);
        request.record(nodePlan.getElId(), inputs);
        CompositeLoopConfig loopConfig = nodePlan.getLoopConfig();
        Integer times = loopConfig == null ? null : loopConfig.getTimes();
        request.recordLoopDrive(nodePlan.getElId(), times == null || times < 0 ? 0 : times);
        Map<String, Object> outputs = new LinkedHashMap<>();
        if (nodePlan.getOutputParams().containsKey("item")) {
            outputs.put("item", null);
        }
        if (nodePlan.getOutputParams().containsKey("done")) {
            // times 模式没有 collectionPath，但画布仍可把集合接到 collection 端口上供"结束后"出口输出
            outputs.put("done", CompositeDataComponent.resolveCollection(request, nodePlan, inputs));
        }
        request.putProduced(nodePlan.getElId(), outputs);
        request.pushOutputs(nodePlan, outputs);
        return times == null || times < 0 ? 0 : times;
    }
}
