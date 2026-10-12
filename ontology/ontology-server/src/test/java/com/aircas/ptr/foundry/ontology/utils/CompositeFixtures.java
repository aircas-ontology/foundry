package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeFunctionRefBO;
import com.aircas.ptr.foundry.ontology.model.param.CompositeBranchConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeEdge;
import com.aircas.ptr.foundry.ontology.model.param.CompositeLoopConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeNode;
import com.aircas.ptr.foundry.ontology.model.param.CompositeParameter;
import com.aircas.ptr.foundry.ontology.model.param.CompositePortMapping;
import com.aircas.ptr.foundry.ontology.model.param.CompositeTransformConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 函数编排测试夹具，图结构与原型 functionOperators.ts 中的编排示例保持一致。
 */
final class CompositeFixtures {

    /** 原型 mock 空间 id */
    static final String SPACE_ID = "space-1";
    /** 天气过滤函数节点 id */
    static final String CODE_NODE = "node-code";
    /** 天气过滤函数 api */
    static final String CODE_API = "function-1";
    /** 状态融合函数 api */
    static final String API_API = "function-2";

    static final String FLOW_IN = "__flow_in__";
    static final String FLOW_OUT = "__flow_out__";

    private CompositeFixtures() {
    }

    // ------------------------------------------------------------------ 基础构造

    static CompositeNode functionNode(String id, String functionId, String label) {
        return CompositeNode.builder()
                .id(id)
                .kind("function")
                .functionId(functionId)
                .functionVersion("v1.0.0")
                .label(label)
                .build();
    }

    static CompositeNode logicNode(String id, String kind, String label) {
        return CompositeNode.builder()
                .id(id)
                .kind(kind)
                .label(label)
                .build();
    }

    static CompositeNode transformNode(String id, String script) {
        return CompositeNode.builder()
                .id(id)
                .kind("transform")
                .label(id)
                .transformConfig(CompositeTransformConfig.builder().language("javascript").script(script).build())
                .build();
    }

    /** 控制流连线：source.__flow_out__ -> target.__flow_in__ */
    static CompositeEdge flow(String id, String sourceNodeId, String targetNodeId) {
        return flow(id, sourceNodeId, FLOW_OUT, targetNodeId);
    }

    /** 控制流连线：source.<控制端口> -> target.__flow_in__ */
    static CompositeEdge flow(String id, String sourceNodeId, String sourceParameterId, String targetNodeId) {
        return CompositeEdge.builder()
                .id(id)
                .sourceNodeId(sourceNodeId)
                .sourceParameterId(sourceParameterId)
                .targetNodeId(targetNodeId)
                .targetParameterId(FLOW_IN)
                .build();
    }

    /** 数据连线：source.<出参> -> target.<入参> */
    static CompositeEdge data(String id, String sourceNodeId, String sourceParameterId,
                              String targetNodeId, String targetParameterId) {
        return CompositeEdge.builder()
                .id(id)
                .sourceNodeId(sourceNodeId)
                .sourceParameterId(sourceParameterId)
                .targetNodeId(targetNodeId)
                .targetParameterId(targetParameterId)
                .build();
    }

    static CompositeParameter param(String id, String name, String type) {
        return CompositeParameter.builder().id(id).name(name).type(type).build();
    }

    static CompositePortMapping mapping(String id, String externalParameterId, String nodeId, String nodeParameterId) {
        return CompositePortMapping.builder()
                .id(id)
                .externalParameterId(externalParameterId)
                .nodeId(nodeId)
                .nodeParameterId(nodeParameterId)
                .build();
    }

    // ------------------------------------------------------------------ 引用函数

    /** 天气过滤函数：入参 sensorData/timestamp，出参 result/confidence */
    static CompositeFunctionRefBO codeRef() {
        return CompositeFunctionRefBO.builder()
                .functionApi(CODE_API)
                .displayName("天气过滤")
                .published(true)
                .version("v1.0.0")
                .inputs(List.of(
                        param(CODE_API + "-in-data", "sensorData", "object"),
                        param(CODE_API + "-in-time", "timestamp", "string")))
                .outputs(List.of(
                        param(CODE_API + "-out-result", "result", "object"),
                        param(CODE_API + "-out-confidence", "confidence", "number")))
                .build();
    }

    /** 状态融合函数：入参 sensorData/timestamp/previousState，出参 filteredState/confidence */
    static CompositeFunctionRefBO apiRef() {
        return CompositeFunctionRefBO.builder()
                .functionApi(API_API)
                .displayName("状态融合")
                .published(true)
                .version("v1.0.0")
                .inputs(List.of(
                        param(API_API + "-in-0-sensorData", "sensorData", "object"),
                        param(API_API + "-in-1-timestamp", "timestamp", "string"),
                        param(API_API + "-in-2-previousState", "previousState", "object")))
                .outputs(List.of(
                        param(API_API + "-out-0-filteredState", "filteredState", "object"),
                        param(API_API + "-out-1-confidence", "confidence", "number")))
                .build();
    }

    /** 按 functionId 解析引用的函数（与 FunctionServiceImpl 中的 resolver 行为一致） */
    static Function<String, CompositeFunctionRefBO> refResolver() {
        Map<String, CompositeFunctionRefBO> byApi = new LinkedHashMap<>();
        byApi.put(CODE_API, codeRef());
        byApi.put(API_API, apiRef());
        return byApi::get;
    }

    /** 按节点 id 建立引用映射（CompositeElCompiler 的入参形式） */
    static Map<String, CompositeFunctionRefBO> refsByNodeId(String codeNodeId, String apiNodeId) {
        Map<String, CompositeFunctionRefBO> map = new LinkedHashMap<>();
        map.put(codeNodeId, codeRef());
        map.put(apiNodeId, apiRef());
        return map;
    }

    // ------------------------------------------------------------------ 最小可用图

    /** node-code -> node-api 两节点直线图，带完整外部输入输出映射 */
    static CompositeConfig minimalConfig() {
        CompositeNode code = functionNode(CODE_NODE, CODE_API, "天气过滤");
        CompositeNode api = functionNode("node-api", API_API, "状态融合");
        return CompositeConfig.builder()
                .kind("composite")
                .nodes(List.of(code, api))
                .edges(List.of(
                        flow("edge-flow", CODE_NODE, "node-api"),
                        data("edge-data", CODE_NODE, CODE_API + "-out-result", "node-api", API_API + "-in-2-previousState")))
                .inputParameters(List.of(param("function-4-in-data", "sensorData", "object")))
                .outputParameters(List.of(param("function-4-out-result", "filteredState", "object")))
                .inputMappings(List.of(
                        mapping("map-in-1", "function-4-in-data", CODE_NODE, CODE_API + "-in-data")))
                .outputMappings(List.of(
                        mapping("map-out-1", "function-4-out-result", "node-api", API_API + "-out-0-filteredState")))
                .build();
    }

    /** 无外部输入输出映射的两节点直线图 */
    static CompositeConfig minimalConfigWithoutMappings() {
        CompositeConfig config = minimalConfig();
        config.setInputParameters(List.of());
        config.setOutputParameters(List.of());
        config.setInputMappings(List.of());
        config.setOutputMappings(List.of());
        return config;
    }

    // ------------------------------------------------------------------ 原型完整图

    static final String PREP = "node-transform-prep";
    static final String LOOP = "node-loop";
    static final String BRANCH = "node-branch";
    static final String ADAPT = "node-transform-adapt";
    static final String FALLBACK = "node-transform-fallback";
    static final String API = "node-api";

    static final String PREP_SCRIPT =
            "// @in result:object\n"
                    + "// @in confidence:number\n"
                    + "// @out targets:array\n"
                    + "// @out sensorData:object\n"
                    + "// @out timestamp:string\n";

    static final String ADAPT_SCRIPT =
            "// @in result:object\n"
                    + "// @in confidence:number\n"
                    + "// @out sensorData:object\n"
                    + "// @out timestamp:string\n";

    static final String FALLBACK_SCRIPT =
            "// @in result:object\n"
                    + "// @out marked:object\n";

    /**
     * 原型中的完整编排图：
     * 天气过滤 -> 预处理转换 -> 循环 -> 分支 -> (适配转换 -> 状态融合) | (兜底转换)
     */
    static CompositeConfig prototypeConfig() {
        CompositeNode code = functionNode(CODE_NODE, CODE_API, "天气过滤");
        CompositeNode prep = transformNode(PREP, PREP_SCRIPT);
        CompositeNode loop = logicNode(LOOP, "loop", "遍历目标");
        loop.setLoopConfig(CompositeLoopConfig.builder()
                .mode("foreach").collectionPath("targets").itemName("item").times(1).build());
        CompositeNode branch = logicNode(BRANCH, "branch", "置信度判断");
        branch.setBranchConfig(CompositeBranchConfig.builder().expression("value.confidence >= 0.6").build());
        CompositeNode adapt = transformNode(ADAPT, ADAPT_SCRIPT);
        CompositeNode api = functionNode(API, API_API, "状态融合");
        CompositeNode fallback = transformNode(FALLBACK, FALLBACK_SCRIPT);

        List<CompositeEdge> edges = new ArrayList<>();
        // 控制流
        edges.add(flow("ce-1", CODE_NODE, PREP));
        edges.add(flow("ce-2", PREP, LOOP));
        edges.add(flow("ce-3", LOOP, BRANCH));
        edges.add(flow("ce-4", BRANCH, "true", ADAPT));
        edges.add(flow("ce-5", BRANCH, "false", FALLBACK));
        edges.add(flow("ce-6", ADAPT, API));
        // 数据流
        edges.add(data("de-1", CODE_NODE, CODE_API + "-out-result", PREP, "result"));
        edges.add(data("de-2", CODE_NODE, CODE_API + "-out-confidence", PREP, "confidence"));
        edges.add(data("de-3", PREP, "targets", LOOP, "collection"));
        edges.add(data("de-4", LOOP, "item", BRANCH, "value"));
        edges.add(data("de-5", BRANCH, "true", ADAPT, "result"));
        edges.add(data("de-6", CODE_NODE, CODE_API + "-out-confidence", ADAPT, "confidence"));
        edges.add(data("de-7", ADAPT, "sensorData", API, API_API + "-in-0-sensorData"));
        edges.add(data("de-8", ADAPT, "timestamp", API, API_API + "-in-1-timestamp"));
        edges.add(data("de-9", BRANCH, "false", FALLBACK, "result"));

        return CompositeConfig.builder()
                .kind("composite")
                .nodes(List.of(code, prep, loop, branch, adapt, api, fallback))
                .edges(edges)
                .inputParameters(List.of(
                        param("function-4-in-data", "sensorData", "object"),
                        param("function-4-in-time", "timestamp", "string")))
                .outputParameters(List.of(
                        param("function-4-out-result", "filteredState", "object"),
                        param("function-4-out-confidence", "confidence", "number")))
                .inputMappings(List.of(
                        mapping("map-in-1", "function-4-in-data", CODE_NODE, CODE_API + "-in-data"),
                        mapping("map-in-2", "function-4-in-time", CODE_NODE, CODE_API + "-in-time")))
                .outputMappings(List.of(
                        mapping("map-out-1", "function-4-out-result", API, API_API + "-out-0-filteredState"),
                        mapping("map-out-2", "function-4-out-confidence", API, API_API + "-out-1-confidence")))
                .build();
    }

    /** 把夹具配置深拷贝一份，便于用例逐个字段破坏 */
    static CompositeConfig copy(CompositeConfig config) {
        CompositeConfig copy = new CompositeConfig();
        copy.setKind(config.getKind());
        List<CompositeNode> nodes = new ArrayList<>();
        for (CompositeNode node : config.getNodes()) {
            nodes.add(copyNode(node));
        }
        copy.setNodes(nodes);
        List<CompositeEdge> edges = new ArrayList<>();
        for (CompositeEdge edge : config.getEdges()) {
            edges.add(CompositeEdge.builder()
                    .id(edge.getId())
                    .sourceNodeId(edge.getSourceNodeId())
                    .sourceParameterId(edge.getSourceParameterId())
                    .targetNodeId(edge.getTargetNodeId())
                    .targetParameterId(edge.getTargetParameterId())
                    .build());
        }
        copy.setEdges(edges);
        copy.setInputParameters(new ArrayList<>(config.getInputParameters()));
        copy.setOutputParameters(new ArrayList<>(config.getOutputParameters()));
        List<CompositePortMapping> inputMappings = new ArrayList<>();
        for (CompositePortMapping m : config.getInputMappings()) {
            inputMappings.add(mapping(m.getId(), m.getExternalParameterId(), m.getNodeId(), m.getNodeParameterId()));
        }
        copy.setInputMappings(inputMappings);
        List<CompositePortMapping> outputMappings = new ArrayList<>();
        for (CompositePortMapping m : config.getOutputMappings()) {
            outputMappings.add(mapping(m.getId(), m.getExternalParameterId(), m.getNodeId(), m.getNodeParameterId()));
        }
        copy.setOutputMappings(outputMappings);
        return copy;
    }

    private static CompositeNode copyNode(CompositeNode node) {
        CompositeNode copy = CompositeNode.builder()
                .id(node.getId())
                .kind(node.getKind())
                .functionId(node.getFunctionId())
                .functionVersion(node.getFunctionVersion())
                .label(node.getLabel())
                .inputDefaults(node.getInputDefaults() == null ? null : new LinkedHashMap<>(node.getInputDefaults()))
                .build();
        if (node.getLoopConfig() != null) {
            copy.setLoopConfig(CompositeLoopConfig.builder()
                    .mode(node.getLoopConfig().getMode())
                    .collectionPath(node.getLoopConfig().getCollectionPath())
                    .times(node.getLoopConfig().getTimes())
                    .itemName(node.getLoopConfig().getItemName())
                    .build());
        }
        if (node.getBranchConfig() != null) {
            copy.setBranchConfig(CompositeBranchConfig.builder()
                    .expression(node.getBranchConfig().getExpression())
                    .build());
        }
        if (node.getTransformConfig() != null) {
            copy.setTransformConfig(CompositeTransformConfig.builder()
                    .language(node.getTransformConfig().getLanguage())
                    .script(node.getTransformConfig().getScript())
                    .build());
        }
        if (node.getInputParameters() != null) {
            copy.setInputParameters(new ArrayList<>(node.getInputParameters()));
        }
        if (node.getOutputParameters() != null) {
            copy.setOutputParameters(new ArrayList<>(node.getOutputParameters()));
        }
        return copy;
    }
}