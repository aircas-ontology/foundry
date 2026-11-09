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
 * 行为树执行入参要求：调用方据此补齐 inputs 与 entityPrimaryKey，再调 execute / test / executeBatch。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行入参要求")
public class TreeExecuteInputsVO {

    @Schema(name = "entityPrimaryKeyRequired", description = "执行请求是否必须传 entityPrimaryKey：树里有节点需要实例数据", example = "true")
    private boolean entityPrimaryKeyRequired;

    @Schema(name = "inputParams", description = "树里行为的外部输入参数，按黑板键名给出；必填项需要调用方传值")
    private List<TreeExecuteInputVO> inputParams = new ArrayList<>();

    @Schema(name = "instanceRequirements", description = "需要实例数据的节点清单")
    private List<TreeInstanceRequirementVO> instanceRequirements = new ArrayList<>();

    public List<TreeExecuteInputVO> getInputParams() {
        return inputParams == null ? new ArrayList<>() : inputParams;
    }

    public List<TreeInstanceRequirementVO> getInstanceRequirements() {
        return instanceRequirements == null ? new ArrayList<>() : instanceRequirements;
    }
}
