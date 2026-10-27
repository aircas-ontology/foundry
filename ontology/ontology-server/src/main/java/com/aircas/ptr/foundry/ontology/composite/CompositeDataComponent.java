package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.param.CompositeLoopConfig;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数编排数据类节点的公共运行期逻辑。
 * <p>执行流程：定位节点定义 -&gt; 解析入参 -&gt; 产出出参 -&gt; 记录产出 -&gt; 沿数据连线推送下游。
 * 原子函数节点与转换节点只有"产出出参"这一步不同，因此各自继承本类实现
 * {@link #produce(CompositeRuntimeRequest, CompositeNodePlanBO, Map)}。</p>
 * <p>入参解析优先级（与原型一致）：所在循环的当前项 &gt; 上游连线推送值 &gt; 外部输入映射 &gt; 节点默认值 &gt; null。
 * 循环当前项必须在使用时读取，因为 LiteFlow 的迭代器组件只执行一次，无法逐项推送给下游。</p>
 */
@Slf4j
public abstract class CompositeDataComponent extends NodeComponent {

    @Override
    public void process() throws Exception {
        CompositeRuntimeRequest request = requestOf(this);
        CompositeNodePlanBO nodePlan = planOf(this);
        Map<String, Object> inputs = resolveInputs(this, request, nodePlan);
        request.record(nodePlan.getElId(), inputs);
        Map<String, Object> outputs = produce(request, nodePlan, inputs);
        request.putProduced(nodePlan.getElId(), outputs);
        request.pushOutputs(nodePlan, outputs);
        if (!nodePlan.getEnclosingLoops().isEmpty() && log.isDebugEnabled()) {
            // getCurrLoopObj 是泛型方法，必须显式转成 Object，否则会匹配到 String.valueOf(char[])
            log.debug("编排节点[{}]当前循环项: {}", nodePlan.getLabel(), String.valueOf((Object) getCurrLoopObj()));
        }
    }

    /**
     * 产出本节点出参值。
     *
     * @param request  本次执行请求
     * @param nodePlan 节点定义
     * @param inputs   解析出的入参（键为入参端口名）
     * @return 出参值，键为出参端口名（数据边与出参汇总都按端口名取）
     */
    protected abstract Map<String, Object> produce(CompositeRuntimeRequest request, CompositeNodePlanBO nodePlan,
                                                   Map<String, Object> inputs);

    // ================================================================== 运行期公共逻辑

    /** 取本次执行请求 */
    static CompositeRuntimeRequest requestOf(NodeComponent component) {
        CompositeRuntimeRequest request = component.getRequestData();
        if (request == null) {
            throw new BusinessException("函数编排执行上下文缺失，请确认编排函数已注册执行链");
        }
        return request;
    }

    /** 按 LiteFlow 节点 id 取节点定义 */
    static CompositeNodePlanBO planOf(NodeComponent component) {
        CompositeRuntimeRequest request = requestOf(component);
        CompositeNodePlanBO nodePlan = request.nodePlan(component.getNodeId());
        if (nodePlan == null) {
            throw new BusinessException("编排执行计划缺少节点定义：" + component.getNodeId());
        }
        return nodePlan;
    }

    /**
     * 解析节点全部入参，优先级：所在循环的当前项 &gt; 上游连线推送值 &gt; 外部输入映射 &gt; 节点默认值 &gt; null。
     */
    static Map<String, Object> resolveInputs(NodeComponent component, CompositeRuntimeRequest request,
                                             CompositeNodePlanBO nodePlan) {
        Map<String, Object> pushed = request.pending(nodePlan.getElId());
        Map<String, String> feederMap = request.feedersOf(nodePlan.getElId());
        Map<String, Object> externalInputs = request.getExternalInputs();
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (Map.Entry<String, String> port : nodePlan.getInputParams().entrySet()) {
            String portId = port.getKey();
            String name = port.getValue();
            String feeder = feederMap.get(name);
            if (feeder != null && nodePlan.getEnclosingLoops().contains(feeder)) {
                inputs.put(name, loopObjectFor(component, request, nodePlan, feeder));
                continue;
            }
            if (pushed.containsKey(name)) {
                inputs.put(name, pushed.get(name));
                continue;
            }
            String externalName = nodePlan.getExternalInputs().get(portId);
            if (externalName != null && externalInputs.containsKey(externalName)) {
                inputs.put(name, externalInputs.get(externalName));
                continue;
            }
            Object defaultValue = nodePlan.getInputDefaults().get(portId);
            if (defaultValue == null) {
                // 画布把子字段绑定的默认值按叶子字段名登记，编译器归位成端口对象后在这里补上
                defaultValue = nodePlan.getNestedInputDefaults().get(name);
            }
            inputs.put(name, defaultValue);
        }
        return inputs;
    }

    /**
     * 读取"驱动本端口的那个循环"的当前循环项。
     * <p>编译器按"外层在前"的顺序写入 {@code enclosingLoops}，LiteFlow 的循环栈同样外层先入栈。
     * {@code getPreNLoopObj(k)}／{@code getPreNLoopIndex(k)} 取的是"栈顶往下数第 k 层"，所以传入的 k 必须是
     * "距栈顶的距离"，即 {@code size - 1 - index}；k 为 0 时等价于 {@code getCurrLoopObj()}。</p>
     * <p>固定次数（FOR）模式的循环不会向循环对象栈压入任何值（{@code ForCondition} 只调用
     * {@code setLoopIndex}），因此这类循环的"当前项"按原型语义取"序号"，即循环下标 + 1。</p>
     */
    static Object loopObjectFor(NodeComponent component, CompositeRuntimeRequest request,
                                CompositeNodePlanBO nodePlan, String feeder) {
        int depth = loopDepthOf(nodePlan, feeder);
        if (isTimesLoop(request, feeder)) {
            Integer index = depth <= 0 ? component.getLoopIndex() : component.getPreNLoopIndex(depth);
            return index == null ? null : index + 1;
        }
        return depth <= 0 ? component.getCurrLoopObj() : component.getPreNLoopObj(depth);
    }

    /** 循环节点是否为固定次数模式 */
    static boolean isTimesLoop(CompositeRuntimeRequest request, String feederElId) {
        CompositeNodePlanBO loopPlan = feederElId == null ? null : request.nodePlan(feederElId);
        if (loopPlan == null) {
            return false;
        }
        CompositeLoopConfig loopConfig = loopPlan.getLoopConfig();
        String mode = loopConfig == null ? null : loopConfig.getMode();
        return mode != null && !mode.trim().isEmpty() && "times".equalsIgnoreCase(mode.trim());
    }

    /** 目标循环节点在 enclosingLoops 中的深度（0 表示最内层）；找不到供给方时按最内层处理 */
    static int loopDepthOf(CompositeNodePlanBO nodePlan, String feeder) {
        List<String> loops = nodePlan.getEnclosingLoops();
        int index = feeder == null ? -1 : loops.indexOf(feeder);
        if (index >= 0) {
            return loops.size() - 1 - index;
        }
        return 0;
    }

    /** 循环节点取待遍历集合：collectionPath 命中入参 &gt; 命中外部输入 &gt; collection 入参 &gt; 首个集合类入参 */
    static Object resolveCollection(CompositeRuntimeRequest request, CompositeNodePlanBO nodePlan,
                                    Map<String, Object> inputs) {
        CompositeLoopConfig loopConfig = nodePlan.getLoopConfig();
        String path = loopConfig == null ? null : loopConfig.getCollectionPath();
        if (path != null && !path.trim().isEmpty()) {
            String key = path.trim();
            if (inputs.containsKey(key) && inputs.get(key) != null) {
                return inputs.get(key);
            }
            if (request.getExternalInputs().containsKey(key)) {
                return request.getExternalInputs().get(key);
            }
        }
        if (inputs.get("collection") != null) {
            return inputs.get("collection");
        }
        for (Object value : inputs.values()) {
            if (isIterable(value)) {
                return value;
            }
        }
        return null;
    }

    static boolean isIterable(Object value) {
        return value instanceof Collection<?> || value instanceof Object[] || value instanceof Iterator<?>;
    }

    static Iterator<?> toIterator(Object value) {
        if (value == null) {
            return Collections.emptyIterator();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection).iterator();
        }
        if (value instanceof Object[] array) {
            return Arrays.asList(array).iterator();
        }
        if (value instanceof Map<?, ?> map) {
            // 原型没有把对象强制转成数组，但画布上"遍历对象"是可配置的场景，按 entry 遍历
            return new ArrayList<>(map.entrySet()).iterator();
        }
        if (value instanceof Iterator<?> iterator) {
            return iterator;
        }
        return Collections.emptyIterator();
    }

    /** 入参快照描述，用于日志与错误文案 */
    static String describe(Map<String, Object> inputs) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
