package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树里一个行为节点的执行要求：调用方按节点在 nodeExecutes[节点标识] 里配数据。
 * <p>实例、外部入参、写回位置都按节点列，节点之间互不共用；两个节点绑同一个行为时各有一套。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树节点执行要求")
public class TreeExecuteNodeVO {

    @Schema(name = "nodeId", description = "行为节点标识，执行请求按它在 nodeExecutes 里给数据", example = "node_2")
    private String nodeId;

    @Schema(name = "nodeDisplayName", description = "节点显示名称")
    private String nodeDisplayName;

    @Schema(name = "actionApi", description = "节点绑定的行为 api", example = "act.satellite.track")
    private String actionApi;

    @Schema(name = "actionDisplayName", description = "节点绑定的行为显示名称", example = "卫星跟踪")
    private String actionDisplayName;

    @Schema(name = "entityPrimaryKeyRequired", description = "该节点是否需要节点级实例：属性值行为为 true，按 instanceRequirements 给实例；"
            + "关系节点按 entityPrimaryKeys.from / to 成对给两端；绑算子的行为为 false，读写实例在 inputParams、outputParams 里按参数/按行给",
            example = "false")
    private boolean entityPrimaryKeyRequired;

    @Schema(name = "instanceRequirements", description = "该节点要用到的节点级实例，按对象逐个列出；绑算子的行为为空，实例在入参/写回行上选")
    private List<TreeInstanceRequirementVO> instanceRequirements = new ArrayList<>();

    @Schema(name = "inputParams", description = "该节点绑定的行为的算子入参，逐条带取值来源：来源是外部输入的按节点在 "
            + "nodeExecutes[节点标识].params 里给值，对象属性/对象/List 来源按所选实例读库里的值，固定值取行为里的字面量")
    private List<TreeExecuteInputVO> inputParams = new ArrayList<>();

    @Schema(name = "outputParams", description = "该节点要写回的数据位置：在 nodeExecutes[节点标识].outputParams[key] "
            + "或 nodeExecutes[节点标识].dataPrimaryKey 里给实例和分组行")
    private List<TreeOutputTargetVO> outputParams = new ArrayList<>();

    public List<TreeInstanceRequirementVO> getInstanceRequirements() {
        return instanceRequirements == null ? new ArrayList<>() : instanceRequirements;
    }

    public List<TreeExecuteInputVO> getInputParams() {
        return inputParams == null ? new ArrayList<>() : inputParams;
    }

    public List<TreeOutputTargetVO> getOutputParams() {
        return outputParams == null ? new ArrayList<>() : outputParams;
    }
}
