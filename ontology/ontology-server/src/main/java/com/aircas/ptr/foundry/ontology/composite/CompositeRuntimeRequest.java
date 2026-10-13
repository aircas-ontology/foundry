package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeEdgeTargetBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数编排的单次执行请求，作为 LiteFlow 的 requestData 在各组件之间传递。
 *
 * <p>承载编译后的节点定义、外部入参，以及运行期在节点之间流转的数据：连线推送过来的
 * 待消费入参、每个节点的产出、执行轨迹。所有 Map 均以 LiteFlow 节点 id（elId）为键。</p>
 */
public class CompositeRuntimeRequest {

    private final CompositePlanBO plan;

    /** 编排的外部入参：参数名 -> 值 */
    private final Map<String, Object> externalInputs;

    /** 节点定义：elId -> 节点定义 */
    private final Map<String, CompositeNodePlanBO> planByElId = new LinkedHashMap<>();

    /** 数据连线来源：目标节点 elId -> (目标入参名 -> 上游节点 elId) */
    private final Map<String, Map<String, String>> feeders = new LinkedHashMap<>();

    /** 连线推送过来、等待消费的入参：节点 elId -> (入参名 -> 值) */
    private final Map<String, Map<String, Object>> pendingInputs = new LinkedHashMap<>();

    /** 节点每次执行解析出的入参快照：节点 elId -> 历史列表 */
    private final Map<String, List<Map<String, Object>>> inputHistory = new LinkedHashMap<>();

    /** 节点产出：节点 elId -> (出参 id -> 值) */
    private final Map<String, Map<String, Object>> produced = new LinkedHashMap<>();

    /** 执行轨迹：按实际执行顺序记录的 elId */
    private final List<String> invocations = new ArrayList<>();

    public CompositeRuntimeRequest(CompositePlanBO plan, Map<String, Object> externalInputs) {
        this.plan = plan;
        this.externalInputs = externalInputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(externalInputs);
        for (CompositeNodePlanBO nodePlan : plan.getNodes().values()) {
            planByElId.put(nodePlan.getElId(), nodePlan);
            for (CompositeEdgeTargetBO target : nodePlan.getEdgeTargets()) {
                feeders.computeIfAbsent(target.getTargetElId(), k -> new LinkedHashMap<>())
                        .put(target.getTargetParamName(), nodePlan.getElId());
            }
        }
    }

    public CompositePlanBO getPlan() {
        return plan;
    }

    public Map<String, Object> getExternalInputs() {
        return externalInputs;
    }

    public Map<String, CompositeNodePlanBO> getPlanByElId() {
        return planByElId;
    }

    public CompositeNodePlanBO nodePlan(String elId) {
        return planByElId.get(elId);
    }

    /** 记录一次节点执行（循环体内会多次执行，历史记录不能覆盖连线推送的待消费入参） */
    public void record(String elId, Map<String, Object> inputs) {
        invocations.add(elId);
        inputHistory.computeIfAbsent(elId, k -> new ArrayList<>())
                .add(inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs));
    }

    /** 某节点最近一次执行解析出的入参 */
    public Map<String, Object> consumedOf(String elId) {
        List<Map<String, Object>> history = inputHistory.get(elId);
        return history == null || history.isEmpty() ? new LinkedHashMap<>() : history.get(history.size() - 1);
    }

    /** 某节点历次执行解析出的入参 */
    public List<Map<String, Object>> historyOf(String elId) {
        return inputHistory.getOrDefault(elId, new ArrayList<>());
    }

    /** 某节点的产出 */
    public Map<String, Object> producedOf(String elId) {
        return produced.getOrDefault(elId, new LinkedHashMap<>());
    }

    /** 按出参名读取节点产出值 */
    public Object producedValue(String elId, String paramName) {
        CompositeNodePlanBO nodePlan = planByElId.get(elId);
        Map<String, Object> outputs = produced.get(elId);
        if (nodePlan == null || outputs == null) {
            return null;
        }
        for (Map.Entry<String, String> port : nodePlan.getOutputParams().entrySet()) {
            if (paramName.equals(port.getValue())) {
                return outputs.get(port.getKey());
            }
        }
        return null;
    }

    /** 沿数据连线把出参值推送给下游节点的入参 */
    public void pushOutputs(CompositeNodePlanBO nodePlan, Map<String, Object> outputs) {
        for (CompositeEdgeTargetBO target : nodePlan.getEdgeTargets()) {
            if (!outputs.containsKey(target.getSourceParamId())) {
                continue;
            }
            pending(target.getTargetElId()).put(target.getTargetParamName(), outputs.get(target.getSourceParamId()));
        }
    }

    /** 某节点待消费的连线入参 */
    public Map<String, Object> pending(String elId) {
        return pendingInputs.computeIfAbsent(elId, k -> new LinkedHashMap<>());
    }

    /** 某节点的连线来源 */
    public Map<String, String> feedersOf(String elId) {
        return feeders.getOrDefault(elId, new LinkedHashMap<>());
    }

    /** 记录节点产出 */
    public void putProduced(String elId, Map<String, Object> outputs) {
        produced.put(elId, outputs);
    }

    /**
     * 按编排定义的外部输出参数汇总最终结果。
     *
     * @return 外部出参名 -> 值，未执行的节点不贡献结果
     */
    public Map<String, Object> assembleOutput() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (CompositeNodePlanBO nodePlan : plan.getNodes().values()) {
            Map<String, Object> outputs = produced.get(nodePlan.getElId());
            if (outputs == null) {
                continue;
            }
            for (Map.Entry<String, String> mapping : nodePlan.getExternalOutputs().entrySet()) {
                if (plan.getOutputParameterNames().contains(mapping.getValue())) {
                    result.put(mapping.getValue(), outputs.get(mapping.getKey()));
                }
            }
        }
        return result;
    }

    /** 执行轨迹，用于问题定位 */
    public List<String> getInvocations() {
        return invocations;
    }
}
