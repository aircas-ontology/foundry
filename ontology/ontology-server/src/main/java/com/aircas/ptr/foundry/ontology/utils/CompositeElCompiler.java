package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeEdgeTargetBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeFunctionRefBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeLoopRoundBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;
import com.aircas.ptr.foundry.ontology.model.enums.CompositeNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.param.CompositeBranchConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeEdge;
import com.aircas.ptr.foundry.ontology.model.param.CompositeLoopConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeNode;
import com.aircas.ptr.foundry.ontology.model.param.CompositeParameter;
import com.aircas.ptr.foundry.ontology.model.param.CompositePortMapping;
import com.aircas.ptr.foundry.ontology.model.param.CompositeTransformConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 函数编排（type=COMPOSITE）画布配置到 LiteFlow EL 的编译器。
 * <p>画布连线分两类：控制流连线（源端口 __flow_out__ 或分支的 true/false，目标端口 __flow_in__）
 * 只表达执行顺序，是生成 EL 结构的唯一依据；数据流连线（普通出入参端口之间）只决定运行期取值。
 * 原型画布中原子函数节点同样会连出 __flow_out__ -> __flow_in__ 的顺序线，因此控制流判定必须
 * 使用连线上的原始端口 id，不能先做端口名映射。</p>
 * <p>生成的 EL 严格使用 LiteFlow 可解析的语法（表达式大小写敏感，小写的 iterator/if/for/do 与
 * wrapper 形式的 tag(x) 都不是合法表达式）：顺序 THEN(a, b)；循环节点 ITERATOR(x).DO(体内区域)，
 * 计数模式为 FOR(x).DO(体内区域)；分支节点 IF(x, 真支路, 假支路)；普通节点直接写节点 id，
 * 运行期组件凭 {@code getNodeId()} 定位自身；空支路或空循环体使用占位节点 compositeNoop。</p>
 * <p>逻辑节点的虚拟端口 id 与端口名恒等（collection/item/done/value/true/false 以及 transform
 * 注解端口），原子函数节点的端口 id 是画布侧生成的不透明字符串，编译器一律按 id 精确匹配，
 * 不做任何解析或归一化。</p>
 * <p>本类不依赖 Spring 容器与 LiteFlow 运行时，便于单元测试。</p>
 */
@Slf4j
public final class CompositeElCompiler {

    /** 空支路/空循环体占位节点 id，由运行期注册为普通组件 */
    public static final String NOOP_NODE_ID = "compositeNoop";

    /** 循环体取数节点的 kind：画布上不存在该类型，只有编译期为逐轮重放生成的节点使用 */
    public static final String LOOP_PRELOAD_KIND = "loopPreload";

    /** 控制流邻接表的键分隔符，节点 id 与端口 id 均不含该字符 */
    private static final char ADJ_SEPARATOR = '\u0001';

    /** 分支节点真支路端口 */
    private static final String PORT_TRUE = "true";

    /** 分支节点假支路端口 */
    private static final String PORT_FALSE = "false";

    /** JSON 序列化工具 */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private CompositeElCompiler() {
    }

    /**
     * 编译编排配置。
     *
     * @param chainName 主链名称，使用函数 api
     * @param config    编排配置，调用前应先通过 {@link CompositeValidator} 校验
     * @param refs      被引用的原子函数定义：画布节点 id -> 函数定义
     * @return 执行计划（主链 EL + 每个节点的运行期定义）
     */
    public static CompositePlanBO compile(String chainName, CompositeConfig config, Map<String, CompositeFunctionRefBO> refs) {
        if (config == null) {
            throw new IllegalArgumentException("函数编排配置不能为空");
        }
        Graph graph = new Graph(chainName, config, refs == null ? new HashMap<>() : refs);
        CompositePlanBO plan = new CompositePlanBO();
        plan.setChainName(chainName);
        plan.getNodes().putAll(graph.nodePlans());
        graph.fillExternalMappings(plan);
        plan.setEl(graph.buildMainRegion());
        plan.getLoopRounds().addAll(graph.loopRounds());
        return plan;
    }

    /**
     * 画布图结构：节点 id 映射、控制流邻接表、拓扑序以及 EL 递归生成。
     */
    private static final class Graph {

        private final CompositeConfig config;

        private final Map<String, CompositeFunctionRefBO> refs;

        /**
         * 链级命名空间前缀。LiteFlow 节点表是全局的，不同编排函数的画布节点 id 完全可能相同
         * （如 node-1），因此 elId 必须带上链名前缀，避免后注册的编排覆盖先注册的节点定义。
         */
        private final String namespace;

        /** 画布节点 id -> 节点 */
        private final Map<String, CompositeNode> nodeByCanvasId = new LinkedHashMap<>();

        /** 画布节点 id -> LiteFlow 节点 id */
        private final Map<String, String> elIdByCanvasId = new LinkedHashMap<>();

        /** LiteFlow 节点 id -> 节点运行期定义 */
        private final Map<String, CompositeNodePlanBO> planByElId = new LinkedHashMap<>();

        /** 控制流邻接表：节点 id + 分隔符 + 源端口 id -> 目标节点 id 列表 */
        private final Map<String, List<String>> controlTargets = new LinkedHashMap<>();

        /** 控制流入度：节点 id -> 入边数量 */
        private final Map<String, Integer> controlInDegree = new LinkedHashMap<>();

        /** 全部节点按控制流拓扑序排列 */
        private final List<String> topoOrder = new ArrayList<>();

        /** 已被循环体或分支支路收纳的节点，避免在外层重复输出 */
        private final Set<String> visited = new LinkedHashSet<>();

        /** 编译器为每个非空循环体生成的取数节点登记项 */
        private final List<CompositeLoopRoundBO> loopRounds = new ArrayList<>();

        /** 已占用的 LiteFlow 节点 id（不含链名前缀），取数节点也要从这里查重 */
        private final Set<String> usedElIds = new LinkedHashSet<>();

        Graph(String chainName, CompositeConfig config, Map<String, CompositeFunctionRefBO> refs) {
            this.config = config;
            this.refs = refs;
            this.namespace = normalizeElId(chainName);
            buildIndex();
            buildNodePlans();
            buildEdges();
            buildTopoOrder();
        }

        /** 建立画布节点与 LiteFlow 节点 id 的映射 */
        private void buildIndex() {
            usedElIds.add(NOOP_NODE_ID);
            for (CompositeNode node : config.getNodes()) {
                if (node == null || isBlank(node.getId()) || nodeByCanvasId.containsKey(node.getId())) {
                    continue;
                }
                nodeByCanvasId.put(node.getId(), node);
                elIdByCanvasId.put(node.getId(), namespace + "_" + allocateElId(node.getId(), usedElIds));
            }
        }

        /** 逐节点构建运行期定义 */
        private void buildNodePlans() {
            for (CompositeNode node : nodeByCanvasId.values()) {
                buildNodePlan(node);
            }
        }

        /** 全部节点的运行期定义，key 为 LiteFlow 节点 id */
        private Map<String, CompositeNodePlanBO> nodePlans() {
            return planByElId;
        }

        /**
         * 循环逐轮重放登记项：按外层先、内层后的顺序生成（emitNode 递归时外层先建）。
         */
        private List<CompositeLoopRoundBO> loopRounds() {
            return loopRounds;
        }

        /** 按连线建立控制流邻接表（仅顺序线）与数据流关系 */
        private void buildEdges() {
            for (CompositeEdge edge : config.getEdges()) {
                if (edge == null || isBlank(edge.getSourceNodeId()) || isBlank(edge.getTargetNodeId())) {
                    continue;
                }
                String sourceElId = elIdByCanvasId.get(edge.getSourceNodeId());
                String targetElId = elIdByCanvasId.get(edge.getTargetNodeId());
                if (sourceElId == null || targetElId == null) {
                    continue;
                }
                String sourceParamId = trimToEmpty(edge.getSourceParameterId());
                String targetParamId = trimToEmpty(edge.getTargetParameterId());
                if (CompositeValidator.isControlFlowEdge(sourceParamId, targetParamId)) {
                    controlTargets.computeIfAbsent(adjKey(sourceElId, sourceParamId), k -> new ArrayList<>()).add(targetElId);
                    controlInDegree.merge(targetElId, 1, Integer::sum);
                } else {
                    fillDataEdge(sourceElId, targetElId, sourceParamId, targetParamId);
                }
            }
            for (String elId : elIdByCanvasId.values()) {
                controlInDegree.putIfAbsent(elId, 0);
            }
        }

        /** 记录一条数据流连线：源节点出参流向目标节点的哪个入参 */
        private void fillDataEdge(String sourceElId, String targetElId, String sourceParamId, String targetParamId) {
            CompositeNodePlanBO sourcePlan = planByElId.get(sourceElId);
            if (sourcePlan == null) {
                return;
            }
            CompositeNodePlanBO targetPlan = planByElId.get(targetElId);
            String targetParamName = targetPlan == null ? targetParamId
                    : targetPlan.getInputParams().getOrDefault(targetParamId, targetParamId);
            sourcePlan.getEdgeTargets().add(new CompositeEdgeTargetBO(targetElId, targetParamName, sourceParamId));
        }

        /** Kahn 算法求控制流拓扑序，存在环路时（校验器已拦截，此处兜底）按画布顺序追加 */
        private void buildTopoOrder() {
            Map<String, Integer> degree = new LinkedHashMap<>(controlInDegree);
            Map<String, List<String>> adjacency = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : controlTargets.entrySet()) {
                adjacency.computeIfAbsent(sourceNodeIdOf(entry.getKey()), k -> new ArrayList<>()).addAll(entry.getValue());
            }
            List<String> queue = new ArrayList<>();
            for (String elId : elIdByCanvasId.values()) {
                if (degree.getOrDefault(elId, 0) <= 0) {
                    queue.add(elId);
                }
            }
            int cursor = 0;
            while (cursor < queue.size()) {
                String current = queue.get(cursor++);
                topoOrder.add(current);
                for (String target : adjacency.getOrDefault(current, new ArrayList<>())) {
                    int left = degree.merge(target, -1, Integer::sum);
                    if (left == 0) {
                        queue.add(target);
                    }
                }
            }
            for (String elId : elIdByCanvasId.values()) {
                if (!topoOrder.contains(elId)) {
                    topoOrder.add(elId);
                }
            }
        }

        /** 构建单个节点的运行期定义 */
        private CompositeNodePlanBO buildNodePlan(CompositeNode node) {
            String elId = elIdByCanvasId.get(node.getId());
            CompositeNodeKindEnum kind = CompositeValidator.resolveKind(node);
            CompositeNodePlanBO plan = new CompositeNodePlanBO();
            plan.setElId(elId);
            plan.setCanvasId(node.getId());
            plan.setKind(kind.name().toLowerCase());
            plan.setLabel(isBlank(node.getLabel()) ? node.getId() : node.getLabel());

            Map<String, CompositeFunctionRefBO> nodeRefs = new HashMap<>();
            nodeRefs.put(node.getId(), refs.getOrDefault(node.getId(), emptyRef(node)));
            for (CompositeParameter parameter : CompositeValidator.nodePorts(node, nodeRefs, true)) {
                if (parameter != null && !isBlank(parameter.getId())) {
                    plan.getInputParams().put(parameter.getId(), parameter.getName());
                }
            }
            for (CompositeParameter parameter : CompositeValidator.nodePorts(node, nodeRefs, false)) {
                if (parameter != null && !isBlank(parameter.getId())) {
                    plan.getOutputParams().put(parameter.getId(), parameter.getName());
                }
            }
            if (node.getInputDefaults() != null) {
                node.getInputDefaults().forEach((portId, value) ->
                        plan.getInputDefaults().put(portId, value == null ? null : String.valueOf(value)));
            }

            if (kind == CompositeNodeKindEnum.FUNCTION) {
                CompositeFunctionRefBO ref = refs.get(node.getId());
                plan.setFunctionApi(ref == null ? node.getFunctionId() : ref.getFunctionApi());
            } else if (kind == CompositeNodeKindEnum.LOOP) {
                CompositeLoopConfig loopConfig = node.getLoopConfig();
                plan.setLoopConfig(loopConfig == null ? new CompositeLoopConfig() : loopConfig);
            } else if (kind == CompositeNodeKindEnum.BRANCH) {
                CompositeBranchConfig branchConfig = node.getBranchConfig();
                plan.setBranchExpression(branchConfig == null ? null : branchConfig.getExpression());
            } else if (kind == CompositeNodeKindEnum.TRANSFORM) {
                CompositeTransformConfig transformConfig = node.getTransformConfig();
                if (transformConfig != null) {
                    plan.setTransformMappingMode(transformConfig.getMappingMode());
                    if ("json".equals(transformConfig.getMappingMode()) && transformConfig.getMappings() != null) {
                        try {
                            plan.setTransformMappings(objectMapper.writeValueAsString(transformConfig.getMappings()));
                        } catch (Exception e) {
                            log.warn("转换节点[{}] JSON 映射序列化失败", elId, e);
                        }
                    }
                    plan.setTransformLanguage(transformConfig.getLanguage());
                    plan.setTransformScript(transformConfig.getScript());
                }
            } else if (kind == CompositeNodeKindEnum.START || kind == CompositeNodeKindEnum.END) {
                // start/end 节点无需额外配置，端口由 inputParameters/outputParameters 定义
            }
            planByElId.put(elId, plan);
            return plan;
        }

        /** 用外部输入输出映射填充节点的 externalInputs/externalOutputs，并汇总编排出入参名 */
        private void fillExternalMappings(CompositePlanBO plan) {
            Map<String, String> externalNames = new LinkedHashMap<>();
            for (CompositeParameter parameter : config.getInputParameters()) {
                if (parameter != null && !isBlank(parameter.getId())) {
                    externalNames.put(parameter.getId(), parameter.getName());
                }
            }
            for (CompositeParameter parameter : config.getOutputParameters()) {
                if (parameter != null && !isBlank(parameter.getId())) {
                    externalNames.putIfAbsent(parameter.getId(), parameter.getName());
                }
            }
            
            // 处理开始节点：将编排输入参数映射到开始节点的输出端口
            for (CompositeNode node : config.getNodes()) {
                if (node == null || CompositeValidator.resolveKind(node) != CompositeNodeKindEnum.START) {
                    continue;
                }
                CompositeNodePlanBO startPlan = planOfCanvas(node.getId());
                if (startPlan == null) {
                    continue;
                }
                // 开始节点的输出端口对应编排的输入参数
                for (CompositeParameter inputParam : config.getInputParameters()) {
                    if (inputParam != null && !isBlank(inputParam.getId())) {
                        String paramName = inputParam.getName();
                        if (isBlank(paramName)) {
                            paramName = inputParam.getId();
                        }
                        // 开始节点的"外部输入"就是编排的输入参数名
                        startPlan.getExternalInputs().put(inputParam.getId(), paramName);
                        if (!plan.getInputParameterNames().contains(paramName)) {
                            plan.getInputParameterNames().add(paramName);
                        }
                    }
                }
            }
            
            // 处理结束节点：将结束节点的输入端口映射到编排的输出参数
            for (CompositeNode node : config.getNodes()) {
                if (node == null || CompositeValidator.resolveKind(node) != CompositeNodeKindEnum.END) {
                    continue;
                }
                CompositeNodePlanBO endPlan = planOfCanvas(node.getId());
                if (endPlan == null) {
                    continue;
                }
                // 结束节点的输入端口对应编排的输出参数
                for (CompositeParameter outputParam : config.getOutputParameters()) {
                    if (outputParam != null && !isBlank(outputParam.getId())) {
                        String paramName = outputParam.getName();
                        if (isBlank(paramName)) {
                            paramName = outputParam.getId();
                        }
                        // 结束节点的"外部输出"就是编排的输出参数名
                        endPlan.getExternalOutputs().put(outputParam.getId(), paramName);
                        if (!plan.getOutputParameterNames().contains(paramName)) {
                            plan.getOutputParameterNames().add(paramName);
                        }
                    }
                }
            }
            
            // 处理普通节点的输入映射（非开始节点）
            for (CompositePortMapping mapping : config.getInputMappings()) {
                CompositeNodePlanBO nodePlan = planOfCanvas(mapping == null ? null : mapping.getNodeId());
                if (nodePlan == null || isBlank(mapping.getNodeParameterId())) {
                    continue;
                }
                CompositeNode node = nodeByCanvasId.get(mapping.getNodeId());
                if (node != null && CompositeValidator.resolveKind(node) == CompositeNodeKindEnum.START) {
                    // 开始节点的映射已在上面处理
                    continue;
                }
                String name = externalNames.getOrDefault(mapping.getExternalParameterId(), mapping.getExternalParameterId());
                nodePlan.getExternalInputs().put(mapping.getNodeParameterId(), name);
                if (!isBlank(name)) {
                    plan.getInputParameterNames().add(name);
                }
            }
            // 兜底：编排未声明 inputMappings 时，全部拓扑入口（无入边的函数节点）的端口按端口名登记为外部入参，
            // 调用方传同名参数即覆盖 inputDefaults，不传则仍回落默认值；多入口编排每个入口都登记。
            // 逻辑节点（loop/branch/transform）的虚拟端口不参与自动登记，避免误覆盖 collection/value 等控制语义端口。
            Set<String> nodesWithIncoming = new LinkedHashSet<>();
            for (CompositeNode node : config.getNodes()) {
                if (node == null || node.getId() == null) {
                    continue;
                }
                nodesWithIncoming.add(node.getId());
            }
            for (CompositeEdge edge : config.getEdges()) {
                if (edge == null || edge.getTargetNodeId() == null) {
                    continue;
                }
                nodesWithIncoming.remove(edge.getTargetNodeId());
            }
            for (CompositeNode node : config.getNodes()) {
                if (node == null || node.getId() == null || !nodesWithIncoming.contains(node.getId())
                        || CompositeValidator.isLogicNode(node)) {
                    continue;
                }
                CompositeNodePlanBO entryPlan = planOfCanvas(node.getId());
                if (entryPlan == null) {
                    continue;
                }
                for (Map.Entry<String, String> port : entryPlan.getInputParams().entrySet()) {
                    String portId = port.getKey();
                    if (entryPlan.getExternalInputs().containsKey(portId)) {
                        continue;
                    }
                    String name = port.getValue();
                    if (isBlank(name)) {
                        continue;
                    }
                    entryPlan.getExternalInputs().put(portId, name);
                    plan.getInputParameterNames().add(name);
                }
            }
            // 处理普通节点的输出映射（非结束节点）
            for (CompositePortMapping mapping : config.getOutputMappings()) {
                CompositeNodePlanBO nodePlan = planOfCanvas(mapping == null ? null : mapping.getNodeId());
                if (nodePlan == null || isBlank(mapping.getNodeParameterId())) {
                    continue;
                }
                CompositeNode node = nodeByCanvasId.get(mapping.getNodeId());
                if (node != null && CompositeValidator.resolveKind(node) == CompositeNodeKindEnum.END) {
                    // 结束节点的映射已在上面处理
                    continue;
                }
                String name = externalNames.getOrDefault(mapping.getExternalParameterId(), mapping.getExternalParameterId());
                nodePlan.getExternalOutputs().put(mapping.getNodeParameterId(), name);
                if (!isBlank(name)) {
                    plan.getOutputParameterNames().add(name);
                }
            }
        }

        /**
         * 生成主链 EL：按拓扑序遍历全部节点，已被循环体或分支支路收纳的节点跳过，
         * 因此嵌套结构只在其容器节点处展开一次。
         */
        private String buildMainRegion() {
            List<String> parts = new ArrayList<>();
            for (String elId : topoOrder) {
                if (visited.contains(elId)) {
                    continue;
                }
                parts.add(emitNode(elId));
            }
            return join(parts);
        }

        /** 输出一个节点及其嵌套结构 */
        private String emitNode(String elId) {
            visited.add(elId);
            CompositeNodePlanBO plan = planByElId.get(elId);
            CompositeNodeKindEnum kind = CompositeNodeKindEnum.fromKind(plan == null ? null : plan.getKind());
            if (kind == CompositeNodeKindEnum.LOOP) {
                List<String> roots = directTargetsOf(elId, CompositeValidator.FLOW_OUT);
                List<String> body = regionOf(elId, CompositeValidator.FLOW_OUT);
                plan.getLoopRoots().addAll(roots);
                plan.getLoopBody().addAll(body);
                for (String member : body) {
                    CompositeNodePlanBO memberPlan = planByElId.get(member);
                    if (memberPlan != null && !member.equals(elId)) {
                        memberPlan.getEnclosingLoops().add(elId);
                    }
                }
                String preloadElId = preloadNode(elId, body);
                if (timesLoop(plan)) {
                    return "FOR(" + elId + ").DO(" + preloadElId + ")";
                }
                return "ITERATOR(" + elId + ").DO(" + preloadElId + ")";
            }
            if (kind == CompositeNodeKindEnum.BRANCH) {
                // 两条支路汇聚到同一后续节点时，汇聚点及其独占的下游不属于任一支路，
                // 否则只有一条支路能执行到它们；剔除后由外层区域按拓扑序在 IF 之后输出。
                Set<String> merged = mergedTargets(elId);
                List<String> trueRegion = regionOf(elId, PORT_TRUE, merged);
                List<String> falseRegion = regionOf(elId, PORT_FALSE, merged);
                plan.getBranchTargets().put(PORT_TRUE, firstOf(directTargetsOf(elId, PORT_TRUE)));
                plan.getBranchTargets().put(PORT_FALSE, firstOf(directTargetsOf(elId, PORT_FALSE)));
                return "IF(" + elId + ", " + join(regionParts(trueRegion)) + ", " + join(regionParts(falseRegion)) + ")";
            }
            return elId;
        }

        /** 展开区域内首层需要输出的节点，其余节点由嵌套容器递归输出 */
        private List<String> regionParts(List<String> region) {
            List<String> parts = new ArrayList<>();
            for (String elId : region) {
                if (visited.contains(elId)) {
                    continue;
                }
                parts.add(emitNode(elId));
            }
            return parts;
        }

        /**
         * 找出分支两条支路的汇聚节点：分别沿控制流向后遍历两条支路，两条支路都能到达的节点
         * 即汇聚点及其下游。这些节点必须在 IF 之外由外层区域按拓扑序输出，否则只有一条支路能执行到它们。
         * 无法确定汇聚点（单条支路、无公共后继）时返回空集。
         */
        private Set<String> mergedTargets(String branchElId) {
            List<String> trueDirect = directTargetsOf(branchElId, PORT_TRUE);
            List<String> falseDirect = directTargetsOf(branchElId, PORT_FALSE);
            if (trueDirect.isEmpty() || falseDirect.isEmpty()) {
                return new LinkedHashSet<>();
            }
            Set<String> merged = reachableFrom(trueDirect, branchElId);
            merged.retainAll(reachableFrom(falseDirect, branchElId));
            merged.remove(branchElId);
            return merged;
        }

        /** 从若干起点沿控制流向后遍历到的全部节点，回到容器节点时不再展开 */
        private Set<String> reachableFrom(List<String> starts, String containerElId) {
            Set<String> reached = new LinkedHashSet<>();
            Deque<String> pending = new ArrayDeque<>(starts);
            while (!pending.isEmpty()) {
                String current = pending.poll();
                if (current.equals(containerElId) || !reached.add(current)) {
                    continue;
                }
                for (String port : controlPorts()) {
                    pending.addAll(directTargetsOf(current, port));
                }
            }
            return reached;
        }

        /** 容器节点通过某个控制端口可达的全部节点，按拓扑序返回 */
        private List<String> regionOf(String containerElId, String sourceParamId) {
            return regionOf(containerElId, sourceParamId, new LinkedHashSet<>());
        }

        /** 容器区域，排除掉不属于该容器的汇聚节点 */
        private List<String> regionOf(String containerElId, String sourceParamId, Set<String> excluded) {
            Set<String> reached = new LinkedHashSet<>();
            List<String> pending = new ArrayList<>(directTargetsOf(containerElId, sourceParamId));
            while (!pending.isEmpty()) {
                String current = pending.remove(0);
                if (current.equals(containerElId) || excluded.contains(current) || !reached.add(current)) {
                    continue;
                }
                for (String port : controlPorts()) {
                    pending.addAll(directTargetsOf(current, port));
                }
            }
            List<String> region = new ArrayList<>();
            for (String elId : topoOrder) {
                if (reached.contains(elId)) {
                    region.add(elId);
                }
            }
            return region;
        }

        /** 容器节点某个控制端口的直接下游节点 */
        private List<String> directTargetsOf(String containerElId, String sourceParamId) {
            return controlTargets.getOrDefault(adjKey(containerElId, sourceParamId), new ArrayList<>());
        }

        private CompositeNodePlanBO planOfCanvas(String canvasId) {
            if (isBlank(canvasId)) {
                return null;
            }
            String elId = elIdByCanvasId.get(canvasId);
            return elId == null ? null : planByElId.get(elId);
        }

        /** 区域片段拼成 EL，单元素不加 THEN，空区域用占位节点 */
        private String join(List<String> parts) {
            if (parts.isEmpty()) {
                return NOOP_NODE_ID;
            }
            if (parts.size() == 1) {
                return parts.get(0);
            }
            return "THEN(" + String.join(", ", parts) + ")";
        }

        /** times 计数模式使用 for 组件，其余（foreach）使用迭代器组件 */
        private boolean timesLoop(CompositeNodePlanBO plan) {
            CompositeLoopConfig loopConfig = plan == null ? null : plan.getLoopConfig();
            String mode = loopConfig == null ? null : loopConfig.getMode();
            return !isBlank(mode) && "times".equalsIgnoreCase(mode.trim());
        }

        /**
         * 为循环体生成"每轮取数"节点并返回循环体的 EL 片段。
         * <p>循环节点只执行一次，LiteFlow 在每一轮开始前不回调任何组件，所以来自循环体外的上游取值
         * 只在第一轮可见。这里把它自己生成的取数节点放在循环体最前面，每轮先执行它重放这些取值；
         * 空循环体没有需要重放的节点，保持占位节点不变。</p>
         */
        private String preloadNode(String loopElId, List<String> body) {
            if (body.isEmpty()) {
                return NOOP_NODE_ID;
            }
            CompositeNodePlanBO loopPlan = planByElId.get(loopElId);
            // 前缀只加一次：loopElId 本身已带命名空间，整串归一化会让 id 出现重复前缀
            String preloadElId = namespace + "_"
                    + allocateElId(strippedNamespace(loopElId) + "_pre", usedElIds);
            CompositeNodePlanBO preload = new CompositeNodePlanBO();
            preload.setElId(preloadElId);
            // 画布上没有对应节点：canvasId 留空，kind 用运行期专用的 loopPreload，label 只用于日志
            preload.setKind(LOOP_PRELOAD_KIND);
            preload.setLabel((loopPlan == null ? loopElId : loopPlan.getLabel()) + "-取数");

            CompositeLoopRoundBO round = new CompositeLoopRoundBO();
            round.setLoopElId(loopElId);
            round.setPreloadElId(preloadElId);
            round.setPreloadNode(preload);
            round.getMemberElIds().addAll(body);
            loopRounds.add(round);

            List<String> parts = new ArrayList<>();
            parts.add(preloadElId);
            parts.addAll(regionParts(body));
            return join(parts);
        }

        private String firstOf(List<String> targets) {
            return targets.isEmpty() ? NOOP_NODE_ID : targets.get(0);
        }

        /** 去掉编译器加在节点 id 前面的命名空间，用于在已有 id 之后派生新 id */
        private String strippedNamespace(String elId) {
            String prefix = namespace + "_";
            return elId.startsWith(prefix) ? elId.substring(prefix.length()) : elId;
        }

        private List<String> controlPorts() {
            return List.of(CompositeValidator.FLOW_OUT, PORT_TRUE, PORT_FALSE);
        }
    }

    /** 控制流邻接表键中的节点 id 部分 */
    private static String sourceNodeIdOf(String key) {
        int index = key.indexOf(ADJ_SEPARATOR);
        return index < 0 ? key : key.substring(0, index);
    }

    private static String adjKey(String elId, String sourceParamId) {
        return elId + ADJ_SEPARATOR + sourceParamId;
    }

    /**
     * 分配合法的 LiteFlow 节点 id：仅允许字母、数字、下划线与美元符号且不能以数字开头。
     * 画布节点 id 含连字符需要转换，冲突时追加序号。
     *
     * @param canvasId 画布节点 id
     * @param used     已占用的节点 id
     * @return LiteFlow 节点 id
     */
    public static String allocateElId(String canvasId, Set<String> used) {
        String base = normalizeElId(canvasId);
        String elId = base;
        int index = 2;
        while (used.contains(elId)) {
            elId = base + "_" + index++;
        }
        used.add(elId);
        return elId;
    }

    /**
     * 将任意画布 id 归一化为合法节点 id。
     *
     * @param canvasId 画布节点 id
     * @return 合法节点 id
     */
    public static String normalizeElId(String canvasId) {
        if (isBlank(canvasId)) {
            return "node";
        }
        StringBuilder builder = new StringBuilder(canvasId.length() + 4);
        for (int i = 0; i < canvasId.length(); i++) {
            char ch = canvasId.charAt(i);
            boolean legal = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '_' || ch == '$';
            builder.append(legal ? ch : '_');
        }
        String normalized = builder.toString();
        char first = normalized.charAt(0);
        if (!(Character.isLetter(first) || first == '_' || first == '$')) {
            normalized = "n_" + normalized;
        }
        return normalized;
    }

    /** 引用函数缺失时的空定义，仅用于端口推导不抛空指针，校验器已先行拦截 */
    private static CompositeFunctionRefBO emptyRef(CompositeNode node) {
        CompositeFunctionRefBO ref = new CompositeFunctionRefBO();
        ref.setFunctionApi(node == null ? null : node.getFunctionId());
        ref.setDisplayName(node == null ? null : node.getLabel());
        ref.setPublished(false);
        return ref;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
