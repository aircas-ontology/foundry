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
 * 行为树执行入参要求：调用方据此补齐 inputs、节点入参、实例主键与数据位置，再调 execute / test / executeBatch。
 * <p>黑板键的取值（固定值/外部输入/对象属性）由树定义声明，这里只列行为外部入参和实例要求。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行入参要求")
public class TreeExecuteInputsVO {

    @Schema(name = "entityPrimaryKeyRequired", description = "执行请求是否必须给实例主键：树里有行为需要实例数据，"
            + "或有黑板键的来源是对象属性；传共用的 entityPrimaryKey，或把涉及的每个对象都放进 entityPrimaryKeys，都算满足",
            example = "true")
    private boolean entityPrimaryKeyRequired;

    @Schema(name = "inputParams", description = "树里行为的外部输入参数：按节点传，命中的每个节点都要在 nodeExecutes 里给值")
    private List<TreeExecuteInputVO> inputParams = new ArrayList<>();

    @Schema(name = "instanceRequirements", description = "需要实例的数据来源，按对象逐个列出：行为取属性数据，或黑板键读对象属性；"
            + "实例主键由执行请求按对象给；行为绑定关系时还带关系两端的对象，按 from、to 传实例")
    private List<TreeInstanceRequirementVO> instanceRequirements = new ArrayList<>();

    @Schema(name = "outputParams", description = "各节点要写回的数据位置，按写回行逐条列出：写哪个对象、哪个存储分组、要不要给行主键；"
            + "调用方在 nodeExecutes[节点标识].outputParams[key] 或 dataPrimaryKey 里给实例和行")
    private List<TreeOutputTargetVO> outputParams = new ArrayList<>();

    public List<TreeExecuteInputVO> getInputParams() {
        return inputParams == null ? new ArrayList<>() : inputParams;
    }

    public List<TreeInstanceRequirementVO> getInstanceRequirements() {
        return instanceRequirements == null ? new ArrayList<>() : instanceRequirements;
    }

    public List<TreeOutputTargetVO> getOutputParams() {
        return outputParams == null ? new ArrayList<>() : outputParams;
    }
}
