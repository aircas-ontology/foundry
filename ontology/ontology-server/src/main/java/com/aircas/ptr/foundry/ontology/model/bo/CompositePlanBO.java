package com.aircas.ptr.foundry.ontology.model.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编排画布配置编译后的执行计划：LiteFlow 主链 EL、子链 EL 以及每个节点的运行期定义。
 */
@Data
@Schema(name = "CompositePlanBO", description = "编排执行计划")
public class CompositePlanBO {

    @Schema(description = "主链名称（函数 api）")
    private String chainName;

    @Schema(description = "主链 EL")
    private String el;

    @Schema(description = "子链 EL：链名 -> EL，循环体等")
    private Map<String, String> subChainEls = new LinkedHashMap<>();

    @Schema(description = "节点定义：LiteFlow 节点 id -> 节点定义")
    private Map<String, CompositeNodePlanBO> nodes = new LinkedHashMap<>();

    @Schema(description = "编排入参名列表")
    private List<String> inputParameterNames = new ArrayList<>();

    @Schema(description = "编排出参名列表")
    private List<String> outputParameterNames = new ArrayList<>();

    public Map<String, String> getSubChainEls() {
        return subChainEls == null ? subChainEls = new LinkedHashMap<>() : subChainEls;
    }

    public Map<String, CompositeNodePlanBO> getNodes() {
        return nodes == null ? nodes = new LinkedHashMap<>() : nodes;
    }

    public List<String> getInputParameterNames() {
        return inputParameterNames == null ? inputParameterNames = new ArrayList<>() : inputParameterNames;
    }

    public List<String> getOutputParameterNames() {
        return outputParameterNames == null ? outputParameterNames = new ArrayList<>() : outputParameterNames;
    }
}
