package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树执行时需要调用方关注的行为外部入参：由绑定行为的外部输入参数反推。
 * <p>参数按节点取值：每个节点只用自己的 nodeExecutes[节点].params，没有全树默认值可以回退，
 * 因此列出的参数都要传。黑板键是条件用的全局变量，和这里的入参互不相干。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行入参")
public class TreeExecuteInputVO {

    @Schema(name = "key", description = "参数名：与绑定行为声明的外部入参同名，按节点传给 nodeExecutes[节点标识].params",
            example = "ammoCount")
    private String key;

    @Schema(name = "valueType", description = "行为声明的参数类型", example = "INTEGER")
    private FunctionParamTypeEnum valueType;

    @Schema(name = "required", description = "是否必填：外部入参只认节点自己那份取值，没有全树同名值可回退，一律必填",
            example = "true")
    private boolean required;

    @Schema(name = "description", description = "参数说明，取算子参数或属性的说明")
    private String description;

    @Schema(name = "nodeIds", description = "消费该参数的行为节点标识：每个节点都要在 nodeExecutes 里单独给这个参数")
    private List<String> nodeIds = new ArrayList<>();

    @Schema(name = "actionApis", description = "声明该外部参数的行为 api")
    private List<String> actionApis = new ArrayList<>();

    public List<String> getNodeIds() {
        return nodeIds == null ? new ArrayList<>() : nodeIds;
    }

    public List<String> getActionApis() {
        return actionApis == null ? new ArrayList<>() : actionApis;
    }
}
