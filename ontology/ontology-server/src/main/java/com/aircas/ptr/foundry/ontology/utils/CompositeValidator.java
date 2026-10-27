package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeFunctionRefBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeValidationBO;
import com.aircas.ptr.foundry.ontology.model.enums.CompositeNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.param.CompositeBranchConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeEdge;
import com.aircas.ptr.foundry.ontology.model.param.CompositeLoopConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeNode;
import com.aircas.ptr.foundry.ontology.model.param.CompositeParameter;
import com.aircas.ptr.foundry.ontology.model.param.CompositePortMapping;
import com.aircas.ptr.foundry.ontology.model.param.CompositeTransformConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 函数编排（type=COMPOSITE）的服务端校验器。
 * <p>规则与原型 functionOperator.ts 的 validateComposite 一一对应，错误文案保持一致，
 * 差异仅在于：函数引用改为按 function.api 从数据库解析、"已发布且测试通过" 对应
 * function.status = ENABLE，逻辑节点端口由服务端推导（loop/branch 固定虚拟端口，
 * transform 解析脚本注解），以及同一循环区域内节点间的数据回环（含自反馈，跨轮回喂，
 * 原型一律按环拒绝）不参与环路检测。</p>
 * <p>本类不依赖 Spring 容器，便于单元测试与在 EL 编译前复用。</p>
 */
public final class CompositeValidator {

    public static final String FLOW_IN = "__flow_in__";
    public static final String FLOW_OUT = "__flow_out__";

    private CompositeValidator() {
    }

    /**
     * 校验编排定义。
     *
     * @param config   编排定义
     * @param resolver 根据函数 api 解析被引用函数（不存在返回 null）
     */
    public static CompositeValidationBO validate(CompositeConfig config, Function<String, CompositeFunctionRefBO> resolver) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (config == null) {
            return CompositeValidationBO.fail(List.of("函数编排配置不能为空"));
        }
        List<CompositeNode> nodes = config.getNodes();
        List<CompositeEdge> edges = config.getEdges();

        if (nodes.size() < 2) {
            errors.add("函数编排至少需要两个节点");
            return finish(errors, warnings);
        }

        // 保持画布顺序，多条错误/告警文案的输出顺序才稳定
        Map<String, CompositeNode> nodeMap = new LinkedHashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (CompositeNode node : nodes) {
            if (node == null || isBlank(node.getId())) {
                duplicates.add("(空节点)");
                continue;
            }
            if (nodeMap.put(node.getId(), node) != null) {
                duplicates.add(node.getId());
            }
        }
        List<CompositeNode> functionNodes = new ArrayList<>();
        long startNodeCount = 0;
        long endNodeCount = 0;
        for (CompositeNode node : nodeMap.values()) {
            if (!isLogicNode(node)) {
                functionNodes.add(node);
            }
            CompositeNodeKindEnum kind = resolveKind(node);
            if (kind == CompositeNodeKindEnum.START) {
                startNodeCount++;
            } else if (kind == CompositeNodeKindEnum.END) {
                endNodeCount++;
            }
        }
        if (functionNodes.isEmpty()) {
            errors.add("函数编排至少需要一个函数节点");
            return finish(errors, warnings);
        }
        if (startNodeCount != 1) {
            errors.add("函数编排必须有且仅有一个开始节点");
            return finish(errors, warnings);
        }
        if (endNodeCount != 1) {
            errors.add("函数编排必须有且仅有一个结束节点");
            return finish(errors, warnings);
        }
        if (!duplicates.isEmpty()) {
            errors.add("函数编排存在重复节点");
            return finish(errors, warnings);
        }

        // 解析被引用函数
        Map<String, CompositeFunctionRefBO> refs = new HashMap<>();
        boolean missing = false;
        boolean unpublished = false;
        for (CompositeNode node : functionNodes) {
            if (isBlank(node.getFunctionId())) {
                missing = true;
                continue;
            }
            CompositeFunctionRefBO ref = resolver == null ? null : resolver.apply(node.getFunctionId());
            if (ref == null) {
                missing = true;
                continue;
            }
            refs.put(node.getId(), ref);
            if (!ref.isPublished()) {
                unpublished = true;
            }
        }
        if (missing) {
            errors.add("函数编排引用了不存在或版本已失效的函数");
            return finish(errors, warnings);
        }
        if (unpublished) {
            errors.add("函数编排只能引用已发布且测试通过的函数");
            return finish(errors, warnings);
        }

        if (edges.isEmpty()) {
            errors.add("请至少配置一条输出到下游输入的连线");
            return finish(errors, warnings);
        }

        // 循环体内的节点可把出参连回自己或同区域节点的入参（跨轮回喂），这类数据边不代表执行顺序
        Map<String, Set<String>> loopRegions = loopRegions(nodeMap, edges);

        Map<String, List<String>> adjacency = new HashMap<>();
        nodeMap.keySet().forEach(id -> adjacency.put(id, new ArrayList<>()));
        Set<String> connected = new HashSet<>();
        for (CompositeEdge edge : edges) {
            if (edge == null || !nodeMap.containsKey(edge.getSourceNodeId()) || !nodeMap.containsKey(edge.getTargetNodeId())) {
                errors.add("函数编排存在无效连线");
                return finish(errors, warnings);
            }
            boolean selfEdge = java.util.Objects.equals(edge.getSourceNodeId(), edge.getTargetNodeId());
            if (selfEdge && isControlFlowEdge(edge)) {
                errors.add("函数编排存在无效连线");
                return finish(errors, warnings);
            }
            if (selfEdge && !isSelfFeedbackNode(nodeMap.get(edge.getSourceNodeId()), loopRegions)) {
                errors.add("函数编排存在无效连线：自反馈连线只能用于循环体内的节点[" + edge.getSourceNodeId() + "]");
                return finish(errors, warnings);
            }
            CompositeNode sourceNode = nodeMap.get(edge.getSourceNodeId());
            CompositeNode targetNode = nodeMap.get(edge.getTargetNodeId());
            if (isControlFlowEdge(edge)) {
                adjacency.get(edge.getSourceNodeId()).add(edge.getTargetNodeId());
                connected.add(edge.getSourceNodeId());
                connected.add(edge.getTargetNodeId());
                continue;
            }
            CompositeParameter sourceParameter = findPort(nodePorts(sourceNode, refs, false), edge.getSourceParameterId());
            CompositeParameter targetParameter = findPort(nodePorts(targetNode, refs, true), edge.getTargetParameterId());
            if (sourceParameter == null || targetParameter == null) {
                String detail = "函数编排连线引用了不存在的输入输出参数";
                if (sourceParameter == null) {
                    detail += "：源节点[" + edge.getSourceNodeId() + "]不存在输出端口[" + edge.getSourceParameterId() + "]";
                }
                if (targetParameter == null) {
                    detail += "；目标节点[" + edge.getTargetNodeId() + "]不存在输入端口[" + edge.getTargetParameterId() + "]";
                }
                errors.add(detail);
                return finish(errors, warnings);
            }
            if (!typeEquals(sourceParameter.getType(), targetParameter.getType())) {
                errors.add("参数类型不匹配：" + displayName(sourceParameter) + " 不能连接 " + displayName(targetParameter));
                return finish(errors, warnings);
            }
            // 同一循环区域内的数据回环表达的是跨轮取值而非执行顺序，计入邻接表会被误判成环
            if (!inSameLoopRegion(edge.getSourceNodeId(), edge.getTargetNodeId(), loopRegions)) {
                adjacency.get(edge.getSourceNodeId()).add(edge.getTargetNodeId());
            }
            connected.add(edge.getSourceNodeId());
            connected.add(edge.getTargetNodeId());
        }

        if (nodeMap.keySet().stream().anyMatch(id -> !connected.contains(id))) {
            errors.add("函数编排存在未连接的节点");
            return finish(errors, warnings);
        }

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        if (nodeMap.keySet().stream().anyMatch(id -> hasCycle(id, adjacency, visiting, visited))) {
            errors.add("函数编排不能形成环路");
            return finish(errors, warnings);
        }

        validateLogicConfig(nodeMap.values(), errors, warnings);
        validatePortMappings(config, nodeMap, refs, "input", errors, warnings);
        validatePortMappings(config, nodeMap, refs, "output", errors, warnings);
        return finish(errors, warnings);
    }

    /**
     * 解析节点类型，与原型 resolveCompositeNodeKind 对齐：
     * kind 缺失或无法识别时按 functionId 回退识别逻辑节点。
     */
    public static CompositeNodeKindEnum resolveKind(CompositeNode node) {
        if (node == null) {
            return CompositeNodeKindEnum.FUNCTION;
        }
        return CompositeNodeKindEnum.fromKindAndFunctionId(node.getKind(), node.getFunctionId());
    }

    /** 是否为控制流逻辑节点（loop/branch/transform） */
    public static boolean isLogicNode(CompositeNode node) {
        return resolveKind(node) != CompositeNodeKindEnum.FUNCTION;
    }

    /**
     * 按节点类型字符串判断是否为逻辑节点，kind 为空按原子函数处理
     *
     * @param kind 节点类型
     * @return true 表示逻辑节点
     */
    public static boolean isLogicNode(String kind) {
        return CompositeNodeKindEnum.fromKind(kind) != CompositeNodeKindEnum.FUNCTION;
    }

    /**
     * 编排入口节点：拓扑上没有入边的节点，按 {@code nodes} 顺序取第一个；
     * 全部有入边（成环）时回退 {@code nodes[0]}；空编排返回 null。
     * <p>用于详情回显（补首节点入参）与编译期兜底（未映射端口按端口名登记为外部入参）。</p>
     */
    public static CompositeNode resolveEntryNode(CompositeConfig config) {
        if (config == null) {
            return null;
        }
        List<CompositeNode> nodes = config.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        Set<String> nodesWithIncoming = new HashSet<>();
        for (CompositeEdge edge : config.getEdges()) {
            if (edge != null && edge.getTargetNodeId() != null) {
                nodesWithIncoming.add(edge.getTargetNodeId());
            }
        }
        for (CompositeNode node : nodes) {
            if (node != null && node.getId() != null && !nodesWithIncoming.contains(node.getId())) {
                return node;
            }
        }
        return nodes.get(0);
    }

    /**
     * 逻辑节点虚拟端口 id。
     * <p>原型 logicCompositeNodePorts 中 port(id, name, type) 的 id 与 name 恒等
     * （collection/item/done/value/true/false 以及 transform 注解端口），
     * 因此虚拟端口 id 就是端口名本身，nodeId 参数仅为与函数节点端口保持对称。</p>
     *
     * @param nodeId 画布节点 id
     * @param name   端口名
     * @return 端口 id
     */
    public static String logicPortId(String nodeId, String name) {
        return name;
    }

    /**
     * 控制流连线：分支的 true/false 输出、以及节点的流程输出指向下游流程输入
     */
    public static boolean isControlFlowEdge(CompositeEdge edge) {
        return isControlFlowEdge(edge.getSourceParameterId(), edge.getTargetParameterId());
    }

    /**
     * 按端口 id 判断是否控制流连线
     *
     * @param sourceParameterId 源端口 id
     * @param targetParameterId 目标端口 id
     * @return true 表示仅表达执行顺序的控制流连线
     */
    public static boolean isControlFlowEdge(String sourceParameterId, String targetParameterId) {
        if (!FLOW_IN.equals(targetParameterId)) {
            return false;
        }
        return FLOW_OUT.equals(sourceParameterId) || "true".equals(sourceParameterId) || "false".equals(sourceParameterId);
    }

    /**
     * 节点端口：函数节点取被引用函数的输入/输出参数，逻辑节点取虚拟端口
     *
     * @param inputDirection true 取输入端口，false 取输出端口
     */
    public static List<CompositeParameter> nodePorts(CompositeNode node, Map<String, CompositeFunctionRefBO> refs, boolean inputDirection) {
        CompositeNodeKindEnum kind = resolveKind(node);
        if (kind == CompositeNodeKindEnum.FUNCTION) {
            CompositeFunctionRefBO ref = refs.get(node.getId());
            if (ref == null) {
                return new ArrayList<>();
            }
            return inputDirection ? ref.getInputs() : ref.getOutputs();
        }
        // 显式声明优先，缺省按原型推导
        List<CompositeParameter> declared = inputDirection ? node.getInputParameters() : node.getOutputParameters();
        if (declared != null && !declared.isEmpty()) {
            return declared;
        }
        return defaultLogicPorts(kind, node, inputDirection);
    }

    /** 逻辑节点虚拟端口，与原型 logicCompositeNodePorts 对齐 */
    public static List<CompositeParameter> defaultLogicPorts(CompositeNodeKindEnum kind, CompositeNode node, boolean inputDirection) {
        switch (kind) {
            case START:
                // 开始节点：无输入端口，输出端口由编排 inputParameters 决定（此处返回空，实际映射时从 config 取）
                return inputDirection ? List.of() : List.of();
            case END:
                // 结束节点：输入端口由编排 outputParameters 决定，无输出端口
                return inputDirection ? List.of() : List.of();
            case LOOP:
                return inputDirection
                        ? List.of(port("collection", "collection", "array"))
                        : List.of(port("item", "item", "object"), port("done", "done", "object"));
            case BRANCH:
                return inputDirection
                        ? List.of(port("value", "value", "object"))
                        : List.of(port("true", "true", "object"), port("false", "false", "object"));
            case TRANSFORM:
            default:
                CompositeTransformConfig transformConfig = node == null ? null : node.getTransformConfig();
                String script = transformConfig == null ? null : transformConfig.getScript();
                // 与原型 logicCompositeNodePorts 一致：未填脚本时按默认转换脚本推导端口
                if (script == null || script.trim().isEmpty()) {
                    script = CompositeTransformPortParser.DEFAULT_SCRIPT;
                }
                CompositeTransformPortParser.Ports ports = CompositeTransformPortParser.parse(script);
                return inputDirection ? ports.getInputs() : ports.getOutputs();
        }
    }

    private static boolean hasCycle(String nodeId, Map<String, List<String>> adjacency, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(nodeId)) {
            return true;
        }
        if (visited.contains(nodeId)) {
            return false;
        }
        visiting.add(nodeId);
        for (String target : adjacency.getOrDefault(nodeId, List.of())) {
            if (hasCycle(target, adjacency, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    /**
     * 是否为合法的自反馈节点：数据自环边的源节点位于循环体内且不是循环节点。
     * <p>循环体每轮重新执行，节点把上一轮的出参回喂自己的入参即可逐轮累积（如 a+b 循环累加）。
     * 循环节点的 collection/item/done 虚拟端口没有逐轮重取值的语义，不允许自反馈。</p>
     */
    private static boolean isSelfFeedbackNode(CompositeNode node, Map<String, Set<String>> loopRegions) {
        if (node == null || resolveKind(node) == CompositeNodeKindEnum.LOOP) {
            return false;
        }
        return loopRegions.values().stream().anyMatch(region -> region.contains(node.getId()));
    }

    /** 连线两端是否同处一个循环区域内 */
    private static boolean inSameLoopRegion(String sourceId, String targetId, Map<String, Set<String>> loopRegions) {
        return loopRegions.values().stream()
                .anyMatch(region -> region.contains(sourceId) && region.contains(targetId));
    }

    /**
     * 每个循环节点的区域成员：从该循环节点的流程出口沿控制流连线向后可达（不含循环节点自身，
     * 嵌套循环的成员同时属于外层区域）。与编译器生成循环体 EL 的收纳范围一致。
     */
    private static Map<String, Set<String>> loopRegions(Map<String, CompositeNode> nodeMap, List<CompositeEdge> edges) {
        Map<String, List<String>> controlForward = new HashMap<>();
        for (CompositeEdge edge : edges) {
            if (edge == null || !isControlFlowEdge(edge)) {
                continue;
            }
            controlForward.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>()).add(edge.getTargetNodeId());
        }
        Map<String, Set<String>> regions = new LinkedHashMap<>();
        for (Map.Entry<String, CompositeNode> entry : nodeMap.entrySet()) {
            if (resolveKind(entry.getValue()) != CompositeNodeKindEnum.LOOP) {
                continue;
            }
            Set<String> region = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>(controlForward.getOrDefault(entry.getKey(), List.of()));
            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (region.add(current)) {
                    queue.addAll(controlForward.getOrDefault(current, List.of()));
                }
            }
            regions.put(entry.getKey(), region);
        }
        return regions;
    }

    private static void validateLogicConfig(Iterable<CompositeNode> nodes, List<String> errors, List<String> warnings) {
        for (CompositeNode node : nodes) {
            CompositeNodeKindEnum kind = resolveKind(node);
            if (kind == CompositeNodeKindEnum.LOOP) {
                CompositeLoopConfig loopConfig = node.getLoopConfig();
                if (loopConfig == null || isBlank(loopConfig.getMode())) {
                    errors.add("循环节点[" + node.getId() + "]缺少循环配置");
                    continue;
                }
                if ("foreach".equalsIgnoreCase(loopConfig.getMode()) && isBlank(loopConfig.getCollectionPath())) {
                    errors.add("循环节点[" + node.getId() + "]未配置待遍历的集合");
                }
                if ("times".equalsIgnoreCase(loopConfig.getMode())
                        && (loopConfig.getTimes() == null || loopConfig.getTimes() <= 0)) {
                    errors.add("循环节点[" + node.getId() + "]的循环次数必须大于 0");
                }
            } else if (kind == CompositeNodeKindEnum.BRANCH) {
                CompositeBranchConfig branchConfig = node.getBranchConfig();
                if (branchConfig == null || isBlank(branchConfig.getExpression())) {
                    errors.add("分支节点[" + node.getId() + "]未配置判断条件");
                }
            } else if (kind == CompositeNodeKindEnum.TRANSFORM) {
                CompositeTransformConfig transformConfig = node.getTransformConfig();
                if (transformConfig == null || isBlank(transformConfig.getScript())) {
                    warnings.add("转换节点[" + node.getId() + "]脚本为空，将按默认转换脚本推导端口");
                }
            }
        }
    }

    private static void validatePortMappings(CompositeConfig config, Map<String, CompositeNode> nodeMap,
                                             Map<String, CompositeFunctionRefBO> refs, String direction,
                                             List<String> errors, List<String> warnings) {
        boolean isInput = "input".equals(direction);
        String label = isInput ? "输入" : "输出";
        List<CompositeParameter> externalParameters = isInput ? config.getInputParameters() : config.getOutputParameters();
        List<CompositePortMapping> mappings = isInput ? config.getInputMappings() : config.getOutputMappings();
        // 与原型一致：即使没有对外参数，残留的映射也要报无效，因此不能提前返回
        for (CompositeParameter parameter : externalParameters) {
            boolean mapped = mappings.stream().anyMatch(mapping -> mapping != null
                    && java.util.Objects.equals(mapping.getExternalParameterId(), parameter.getId()));
            if (!mapped) {
                errors.add("请完整配置函数编排" + label + "映射");
                return;
            }
        }
        for (CompositePortMapping mapping : mappings) {
            if (mapping == null) {
                errors.add("函数编排" + label + "映射引用了无效参数");
                continue;
            }
            CompositeParameter externalParameter = findPort(externalParameters, mapping.getExternalParameterId());
            CompositeNode node = mapping.getNodeId() == null ? null : nodeMap.get(mapping.getNodeId());
            if (externalParameter == null || node == null) {
                errors.add("函数编排" + label + "映射引用了无效参数");
                continue;
            }
            if (isLogicNode(node)) {
                // 开始/结束节点是特殊的逻辑节点，允许映射指向
                CompositeNodeKindEnum nodeKind = resolveKind(node);
                if (nodeKind != CompositeNodeKindEnum.START && nodeKind != CompositeNodeKindEnum.END) {
                    errors.add("函数编排" + label + "映射不能指向逻辑节点");
                    continue;
                }
                // 开始节点的输入映射：检查其输出端口；结束节点的输出映射：检查其输入端口
                boolean checkOutputPorts = (nodeKind == CompositeNodeKindEnum.START && isInput);
                boolean checkInputPorts = (nodeKind == CompositeNodeKindEnum.END && !isInput);
                List<CompositeParameter> nodePorts = checkOutputPorts 
                    ? (node.getOutputParameters() != null ? node.getOutputParameters() : List.of())
                    : checkInputPorts 
                        ? (node.getInputParameters() != null ? node.getInputParameters() : List.of())
                        : List.of();
                CompositeParameter nodeParameter = findPort(nodePorts, mapping.getNodeParameterId());
                if (nodeParameter == null) {
                    errors.add("函数编排" + label + "映射引用了无效参数");
                    continue;
                }
                if (!typeEquals(externalParameter.getType(), nodeParameter.getType())) {
                    errors.add("函数编排" + label + "映射参数类型不匹配");
                }
                continue;
            }
            CompositeParameter nodeParameter = findPort(nodePorts(node, refs, isInput), mapping.getNodeParameterId());
            if (nodeParameter == null) {
                errors.add("函数编排" + label + "映射引用了无效参数");
                continue;
            }
            if (!typeEquals(externalParameter.getType(), nodeParameter.getType())) {
                errors.add("函数编排" + label + "映射参数类型不匹配");
            }
        }
    }

    private static CompositeParameter findPort(List<CompositeParameter> ports, String id) {
        if (ports == null || isBlank(id)) {
            return null;
        }
        // 优先按端口 id 精确匹配，未命中时回落按端口 name 匹配（前端画布连线可能使用 name 而非 id）
        return ports.stream()
                .filter(item -> item != null && (id.equals(item.getId()) || id.equals(item.getName())))
                .findFirst()
                .orElse(null);
    }

    /** 端口类型比较，空类型按 object 处理（与原型默认值一致） */
    private static boolean typeEquals(String source, String target) {
        String s = isBlank(source) ? "object" : source.trim().toLowerCase();
        String t = isBlank(target) ? "object" : target.trim().toLowerCase();
        // object 表示"不限定具体类型"，作为通配符可以匹配任何类型（双向）
        return s.equals(t) || "object".equals(s) || "object".equals(t);
    }

    private static String displayName(CompositeParameter parameter) {
        return isBlank(parameter.getName()) ? parameter.getId() : parameter.getName();
    }

    private static CompositeParameter port(String id, String name, String type) {
        CompositeParameter param = new CompositeParameter();
        param.setId(id);
        param.setName(name);
        param.setType(type);
        param.setDescription("");
        return param;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static CompositeValidationBO finish(List<String> errors, List<String> warnings) {
        return CompositeValidationBO.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .build();
    }
}
