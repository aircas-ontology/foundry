package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 函数编排（type=COMPOSITE）的编排定义，序列化为 JSON 存入 function.code 字段。
 * <p>与原型 CompositeDefinition 对应，额外携带编排对外的输入/输出参数定义，
 * 落库时据此生成 function_param 记录，使编排函数与自定义/基础查询函数在
 * 列表、详情、执行入口上保持一致。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排配置")
public class CompositeConfig {

    @Schema(name = "kind", description = "定义类型，固定为 composite", example = "composite")
    private String kind;

    @Schema(name = "nodes", description = "编排节点列表")
    private List<CompositeNode> nodes = new ArrayList<>();

    @Schema(name = "edges", description = "编排连线列表")
    private List<CompositeEdge> edges = new ArrayList<>();

    @Schema(name = "inputMappings", description = "对外输入参数到节点端口的映射")
    private List<CompositePortMapping> inputMappings = new ArrayList<>();

    @Schema(name = "outputMappings", description = "节点输出端口到对外输出参数的映射")
    private List<CompositePortMapping> outputMappings = new ArrayList<>();

    @Schema(name = "inputParameters", description = "编排对外输入参数定义")
    private List<CompositeParameter> inputParameters = new ArrayList<>();

    @Schema(name = "outputParameters", description = "编排对外输出参数定义")
    private List<CompositeParameter> outputParameters = new ArrayList<>();

    public List<CompositeNode> getNodes() {
        return nodes == null ? new ArrayList<>() : nodes;
    }

    public List<CompositeEdge> getEdges() {
        return edges == null ? new ArrayList<>() : edges;
    }

    public List<CompositePortMapping> getInputMappings() {
        return inputMappings == null ? new ArrayList<>() : inputMappings;
    }

    public List<CompositePortMapping> getOutputMappings() {
        return outputMappings == null ? new ArrayList<>() : outputMappings;
    }

    public List<CompositeParameter> getInputParameters() {
        return inputParameters == null ? new ArrayList<>() : inputParameters;
    }

    public List<CompositeParameter> getOutputParameters() {
        return outputParameters == null ? new ArrayList<>() : outputParameters;
    }
}