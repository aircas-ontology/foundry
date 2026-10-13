package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.yomahub.liteflow.core.NodeIteratorComponent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 函数编排"循环"节点（遍历集合模式）。
 * <p>按 {@code loopConfig.collectionPath} 取待遍历集合交给 LiteFlow，由框架逐项驱动循环体；
 * 循环体内的节点通过 {@code getCurrLoopObj()} 读取当前项。</p>
 * <p>本节点同时把集合并入 {@code item}/{@code done} 出参记录：{@code item} 是当前项（循环体内实时读取，
 * 这里只记录节点自身执行时的一次快照），{@code done} 是原始集合，供循环体外的下游节点连线取值。</p>
 */
public class CompositeLoopComponent extends NodeIteratorComponent {

    @Override
    public Iterator<?> processIterator() {
        CompositeRuntimeRequest request = CompositeDataComponent.requestOf(this);
        CompositeNodePlanBO nodePlan = CompositeDataComponent.planOf(this);
        Map<String, Object> inputs = CompositeDataComponent.resolveInputs(this, request, nodePlan);
        request.record(nodePlan.getElId(), inputs);
        Object collection = CompositeDataComponent.resolveCollection(request, nodePlan, inputs);
        Map<String, Object> outputs = new LinkedHashMap<>();
        if (nodePlan.getOutputParams().containsKey("item")) {
            outputs.put("item", null);
        }
        if (nodePlan.getOutputParams().containsKey("done")) {
            outputs.put("done", collection);
        }
        request.putProduced(nodePlan.getElId(), outputs);
        request.pushOutputs(nodePlan, outputs);
        return CompositeDataComponent.toIterator(collection);
    }
}
