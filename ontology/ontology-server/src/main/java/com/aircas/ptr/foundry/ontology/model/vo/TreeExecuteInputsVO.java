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
 * 行为树执行入参要求：调用方据此补齐 inputs、实例主键与数据位置，再调 execute / test / executeBatch。
 * <p>行为需要的参数全部由执行请求提供，节点上不再配黑板键取值的来源。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行入参要求")
public class TreeExecuteInputsVO {

    @Schema(name = "entityPrimaryKeyRequired", description = "执行请求是否必须给实例主键：树里有行为需要实例数据；"
            + "传共用的 entityPrimaryKey，或把涉及的每个对象都放进 entityPrimaryKeys，都算满足", example = "true")
    private boolean entityPrimaryKeyRequired;

    @Schema(name = "inputParams", description = "树里行为的外部输入参数，按黑板键名给出；必填项需要调用方传值")
    private List<TreeExecuteInputVO> inputParams = new ArrayList<>();

    @Schema(name = "instanceRequirements", description = "需要实例数据的行为，按对象逐个列出；实例主键由执行请求给")
    private List<TreeInstanceRequirementVO> instanceRequirements = new ArrayList<>();

    public List<TreeExecuteInputVO> getInputParams() {
        return inputParams == null ? new ArrayList<>() : inputParams;
    }

    public List<TreeInstanceRequirementVO> getInstanceRequirements() {
        return instanceRequirements == null ? new ArrayList<>() : instanceRequirements;
    }
}
