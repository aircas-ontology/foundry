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

import java.util.ArrayList;
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
 * transform 解析脚本注解）。</p>
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
        for (CompositeNode node : nodeMap.values()) {
            if (!isLogicNode(node)) {
                functionNodes.add(node);
            }
        }
        if (functionNodes.isEmpty()) {
            errors.add("函数编排至少需要一个函数节点");
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

        Map<String, List<String>> adjacency = new HashMap<>();
        nodeMap.keySet().forEach(id -> adjacency.put(id, new ArrayList<>()));
        Set<String> connected = new HashSet<>();
        for (CompositeEdge edge : edges) {
            if (edge == null || !nodeMap.containsKey(edge.getSourceNodeId()) || !nodeMap.containsKey(edge.getTargetNodeId())
                    || java.util.Objects.equals(edge.getSourceNodeId(), edge.getTargetNodeId())) {
                errors.add("函数编排存在无效连线");
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
                errors.add("函数编排连线引用了不存在的输入输出参数");
                return finish(errors, warnings);
            }
            if (!typeEquals(sourceParameter.getType(), targetParameter.getType())) {
                errors.add("参数类型不匹配：" + displayName(sourceParameter) + " 不能连接 " + displayName(targetParameter));
                return finish(errors, warnings);
            }
            adjacency.get(edge.getSourceNodeId()).add(edge.getTargetNodeId());
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
                errors.add("函数编排" + label + "映射不能指向逻辑节点");
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
        return ports.stream().filter(item -> item != null && id.equals(item.getId())).findFirst().orElse(null);
    }

    /** 端口类型比较，空类型按 object 处理（与原型默认值一致） */
    private static boolean typeEquals(String source, String target) {
        String s = isBlank(source) ? "object" : source.trim().toLowerCase();
        String t = isBlank(target) ? "object" : target.trim().toLowerCase();
        return s.equals(t);
    }

    private static String displayName(CompositeParameter parameter) {
        return isBlank(parameter.getName()) ? parameter.getId() : parameter.getName();
    }

    private static CompositeParameter port(String id, String name, String type) {
        return CompositeParameter.builder().id(id).name(name).type(type).description("").build();
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
