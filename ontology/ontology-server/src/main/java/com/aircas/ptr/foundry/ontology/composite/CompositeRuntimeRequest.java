package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeEdgeTargetBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeLoopRoundBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;
import com.aircas.ptr.foundry.ontology.model.param.CompositeLoopConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** 连线上游出参端口：目标节点 elId -> (目标入参名 -> (上游节点 elId -> 该上游在这条线上的出参端口 id)) */
    private final Map<String, Map<String, Map<String, List<String>>>> feederSourceParams = new LinkedHashMap<>();

    /** 逐轮重放项：取数节点 elId -> 每轮开始时要铺回的（目标节点 elId -> (入参名 -> 取值动作)) */
    private final Map<String, Map<String, Map<String, RoundValue>>> loopRounds = new LinkedHashMap<>();

    /** 区域内节点的常量默认值：取数节点 elId -> (目标节点 elId -> (入参名 -> 默认值))，重放时先清掉再按优先级现取 */
    private final Map<String, Map<String, Map<String, Object>>> loopMemberDefaults = new LinkedHashMap<>();

    /** 区域成员：取数节点 elId -> 该循环区域内的节点 elId，用于判断内层嵌在谁里面 */
    private final Map<String, List<String>> roundMembers = new LinkedHashMap<>();

    /** 循环节点本轮驱动的数据：节点 elId -> 集合或次数，外层替嵌套的内层推算轮数时用 */
    private final Map<String, Object> loopDrives = new LinkedHashMap<>();

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
                feederSourceParams
                        .computeIfAbsent(target.getTargetElId(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(target.getTargetParamName(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(nodePlan.getElId(), k -> new ArrayList<>())
                        .add(target.getSourceParamId());
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
        CompositeNodePlanBO nodePlan = planByElId.get(elId);
        if (nodePlan == null && elId != null) {
            // 循环体取数节点由编译器生成，画布上没有它，因此不在 plan.nodes 里
            for (CompositeLoopRoundBO round : plan.getLoopRounds()) {
                CompositeNodePlanBO preload = round.getPreloadNode();
                if (preload != null && elId.equals(preload.getElId())) {
                    return preload;
                }
            }
        }
        return nodePlan;
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

    /**
     * 记录循环节点本轮驱动的数据（遍历集合或固定次数），嵌套循环时外层据此推算内层还要跑几轮。
     */
    public void recordLoopDrive(String loopElId, Object drive) {
        loopDrives.put(loopElId, drive);
    }

    /**
     * 登记一个循环的逐轮重放：记住"上游在循环体外"的区域内节点，每轮开始前重新按优先级解析它们的入参。
     * <p>循环节点只执行一次，LiteFlow 在每一轮开始前不回调任何组件，所以区域外上游推送过来的值只在第一轮
     * 留在待消费入参里；编译器为每个非空循环体生成一个取数节点放在循环体最前面，本方法登记它每轮要做的动作。
     * 取数节点自己也在区域内，它的连线上游就是真正的区域外上游，因此"该不该重放"直接由连线来源判断，
     * 不需要再比较节点属于哪一层循环。</p>
     *
     * @param preloadElId 取数节点的 elId
     * @param members     该循环区域内的节点 elId（不含取数节点）
     */
    public void registerLoopRound(String preloadElId, List<String> members) {
        Map<String, Map<String, RoundValue>> targets = new LinkedHashMap<>();
        Map<String, Map<String, Object>> defaults = new LinkedHashMap<>();
        for (String member : members) {
            CompositeNodePlanBO nodePlan = planByElId.get(member);
            if (nodePlan == null) {
                continue;
            }
            Map<String, String> feederMap = feeders.getOrDefault(member, new LinkedHashMap<>());
            Map<String, Map<String, List<String>>> sourceMap = feederSourceParams
                    .getOrDefault(member, new LinkedHashMap<>());
            Map<String, RoundValue> values = new LinkedHashMap<>();
            Map<String, Object> memberDefaults = new LinkedHashMap<>();
            for (Map.Entry<String, String> port : nodePlan.getInputParams().entrySet()) {
                String portId = port.getKey();
                String name = port.getValue();
                String feeder = feederMap.get(name);
                // 上游在区域内时，它会在那一轮自己产出后重新推送，铺回旧值反而盖掉本轮结果
                if (feeder != null && !members.contains(feeder)) {
                    values.put(name, new RoundValue(feeder, sourceMap
                            .getOrDefault(name, new LinkedHashMap<>()).getOrDefault(feeder, new ArrayList<>())));
                }
                if (nodePlan.getInputDefaults().containsKey(portId)) {
                    memberDefaults.put(name, nodePlan.getInputDefaults().get(portId));
                }
            }
            if (!values.isEmpty()) {
                targets.put(member, values);
            }
            if (!memberDefaults.isEmpty()) {
                defaults.put(member, memberDefaults);
            }
        }
        loopRounds.put(preloadElId, targets);
        loopMemberDefaults.put(preloadElId, defaults);
        roundMembers.put(preloadElId, new ArrayList<>(members));
    }

    /**
     * 取数节点执行一次循环体重放：先清掉上一轮留下的推送值，再把区域外上游的最新产出铺回各节点入参。
     * <p>第一轮铺的是循环节点执行前上游刚推送的值，之后每轮都重新读取上游产出，因此上游若在循环之外
     * 被再次执行并产出新值，下一轮立刻生效。区域内节点的常量默认值同样在这里重铺，
     * 因为画布上的默认值是字符串而算子按声明类型收值，与首轮的执行路径保持一致。</p>
     *
     * @param preloadElId 取数节点的 elId
     * @return true 表示登记过重放内容
     */
    public boolean preloadRound(String preloadElId) {
        return preloadRound(preloadElId, true);
    }

    /**
     * 取数节点执行一次循环体重放。
     *
     * @param preloadElId 取数节点的 elId
     * @param record      是否记入执行轨迹；外层替内层补的那几次只是铺数据，画布上没有这些调用
     * @return true 表示登记过重放内容
     */
    public boolean preloadRound(String preloadElId, boolean record) {
        Map<String, Map<String, RoundValue>> targets = loopRounds.get(preloadElId);
        if (targets == null) {
            return false;
        }
        if (record) {
            record(preloadElId, new LinkedHashMap<>());
        }
        Map<String, Map<String, Object>> defaults = loopMemberDefaults.getOrDefault(preloadElId, new LinkedHashMap<>());
        Set<String> touched = new LinkedHashSet<>(targets.keySet());
        touched.addAll(defaults.keySet());
        for (String memberElId : touched) {
            Map<String, Object> pushed = pending(memberElId);
            // 只清掉本循环负责重放的入参：区域内上游在同一轮里推送的新值必须保留，
            // 否则下游节点会把上一轮的产出当成这一轮的输入
            Map<String, RoundValue> values = targets.get(memberElId);
            if (values != null) {
                pushed.keySet().removeAll(values.keySet());
                values.forEach((name, roundValue) -> pushed.put(name, roundValue.readFrom(this)));
            }
            Map<String, Object> memberDefaults = defaults.getOrDefault(memberElId, new LinkedHashMap<>());
            pushed.keySet().removeAll(memberDefaults.keySet());
            pushed.putAll(memberDefaults);
        }
        compensateNested(preloadElId);
        return true;
    }

    /**
     * 循环体内还嵌着循环时，替内层把后面几轮的取数补上。
     * <p>内层组件同样只执行一次（它只给出迭代器或次数），LiteFlow 不会在它的每一轮开始前回调任何组件，
     * 所以内层那一轮的"来自内层循环体外的上游取值"只能由外层在这一轮里铺。外层取数排在内层节点之前，
     * 内层本轮自己还会做一次取数，因此这里只补给它后面那几轮，避免同一轮重复铺值。</p>
     */
    private void compensateNested(String preloadElId) {
        List<String> nested = nestedPreloads(preloadElId);
        if (nested.isEmpty()) {
            return;
        }
        // 本次取数对应内层即将跑的那一轮，之后还要补 rounds - 1 轮
        int rounds = Math.max(roundCountOf(preloadElId), 1);
        for (int round = 1; round < rounds; round++) {
            nested.forEach(nestedPreload -> preloadRound(nestedPreload, false));
        }
    }

    /** 直接嵌在本循环区域内的内层取数节点 */
    private List<String> nestedPreloads(String preloadElId) {
        List<String> members = roundMembers.getOrDefault(preloadElId, new ArrayList<>());
        List<String> nested = new ArrayList<>();
        roundMembers.forEach((other, otherMembers) -> {
            if (other.equals(preloadElId) || otherMembers.isEmpty()) {
                return;
            }
            // 每个区域的首个成员就是它自己的循环节点
            if (members.contains(otherMembers.get(0))) {
                nested.add(other);
            }
        });
        return nested;
    }

    /** 该取数节点是否嵌在另一个循环体内（它的本轮取数由外层代为触发） */
    public boolean isNestedPreload(String preloadElId) {
        List<String> own = roundMembers.get(preloadElId);
        if (own == null || own.isEmpty()) {
            return false;
        }
        String loopElId = own.get(0);
        return roundMembers.keySet().stream()
                .filter(other -> !other.equals(preloadElId))
                .anyMatch(other -> roundMembers.get(other).contains(loopElId));
    }

    /**
     * 本循环要跑的总轮数。
     * <p>循环节点组件只执行一次来给出次数或迭代器，运行期没有任何地方能读到"还剩几轮"，
     * 因此按它登记时的配置推算：固定次数模式读 {@code times}，遍历集合模式读它本轮实际驱动的数据大小。
     * 外层取数先于内层节点执行，所以内层记录的可能还是上一轮的集合，此时回退到按配置现读。</p>
     */
    private int roundCountOf(String preloadElId) {
        List<String> members = roundMembers.getOrDefault(preloadElId, new ArrayList<>());
        if (members.isEmpty()) {
            return 1;
        }
        CompositeNodePlanBO loopPlan = planByElId.get(members.get(0));
        if (loopPlan == null) {
            return 1;
        }
        Object drive = loopDrives.get(loopPlan.getElId());
        if (drive instanceof Collection<?> values) {
            return values.size();
        }
        if (drive instanceof Number count) {
            return count.intValue();
        }
        CompositeLoopConfig loopConfig = loopPlan.getLoopConfig();
        String mode = loopConfig == null ? null : loopConfig.getMode();
        if (mode != null && "times".equalsIgnoreCase(mode.trim())) {
            Integer times = loopConfig.getTimes();
            return times == null || times < 0 ? 0 : times;
        }
        Object collection = CompositeDataComponent.resolveCollection(this, loopPlan, consumedOf(loopPlan.getElId()));
        return collection instanceof Collection<?> values ? values.size() : 1;
    }

    /**
     * 一次重放动作：把某个区域外上游的取值重新铺到目标节点的待消费入参里。
     * <p>连线上的端口 id 两端各自独立（画布为每个端口生成的不透明 id），不能拿源端口 id 去查目标的映射，
     * 因此这里记住的是"上游流向该目标入参的那个出参端口 id"，与 {@code pushOutputs} 用的是同一个键。</p>
     *
     * @param feederElId     上游节点 elId
     * @param sourceParamIds 在该上游连往本目标的连线上出现的出参端口 id，按连线顺序
     */
    private record RoundValue(String feederElId, List<String> sourceParamIds) {

        Object readFrom(CompositeRuntimeRequest request) {
            Map<String, Object> outputs = request.producedOf(feederElId);
            for (String sourceParamId : sourceParamIds) {
                if (outputs.containsKey(sourceParamId)) {
                    return outputs.get(sourceParamId);
                }
            }
            // 上游没有对应产出（例如只给出循环次数的循环节点）时，退回读它本轮解析出的入参：
            // 与原型"循环变量取自上游"的语义一致
            CompositeNodePlanBO feederPlan = request.nodePlan(feederElId);
            if (feederPlan == null) {
                return null;
            }
            Map<String, Object> consumed = request.consumedOf(feederElId);
            for (String sourceParamId : sourceParamIds) {
                String sourceName = feederPlan.getInputParams().get(sourceParamId);
                if (sourceName != null && consumed.containsKey(sourceName)) {
                    return consumed.get(sourceName);
                }
            }
            // 上游是纯控制流节点（不引用函数、也没有虚拟端口）时没有可退回的入参，
            // 取它最近一次解析到的唯一值
            return consumed.size() == 1 ? consumed.values().iterator().next() : null;
        }
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
