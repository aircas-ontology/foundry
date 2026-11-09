package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSubjectTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树行为节点可选项：本空间内已发布的行为及其算子参数。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树可绑定的行为")
public class TreeAvailableActionVO {

    @Schema(name = "actionApi", description = "行为api，保存到行为节点配置里", example = "act.satellite.track")
    private String actionApi;

    @Schema(name = "displayName", description = "行为显示名称")
    private String displayName;

    @Schema(name = "actionKind", description = "行为类别：BASE 基础行为 / APPLICATION 应用行为")
    private ActionKindEnum actionKind;

    @Schema(name = "subjectType", description = "基础行为类型，非基础行为为空")
    private ActionSubjectTypeEnum subjectType;

    @Schema(name = "functionApi", description = "绑定的算子api，属性值和实例关系行为为空")
    private String functionApi;

    @Schema(name = "requiresInstance", description = "执行该行为是否必须提供实体主键：属性值/实例关系行为，或算子入参取对象属性、出参写回属性")
    private boolean requiresInstance;

    @Schema(name = "objectIdentifier", description = "行为作用的对象类型，执行时按它选实例；应用行为为空")
    private String objectIdentifier;

    @Schema(name = "instanceObjects", description = "需要调用方提供实例主键的对象类型：入参取属性/对象/List 或出参写属性的对象。"
            + "多于一个时行为节点必须按对象配实例键；关系对端不在其中，它由对端主键参数指定或整段遍历")
    private List<String> instanceObjects = new ArrayList<>();

    @Schema(name = "externalParams", description = "行为的外部输入参数（取值来源为外部输入），绑定到行为树时保存期会自动补同名黑板键")
    private List<TreeActionParamVO> externalParams = new ArrayList<>();

    @Schema(name = "inputParams", description = "算子入参，按参数名从黑板取值")
    private List<TreeActionParamVO> inputParams = new ArrayList<>();

    @Schema(name = "outputParams", description = "算子出参")
    private List<TreeActionParamVO> outputParams = new ArrayList<>();

    public List<TreeActionParamVO> getInputParams() {
        return inputParams == null ? new ArrayList<>() : inputParams;
    }

    public List<TreeActionParamVO> getOutputParams() {
        return outputParams == null ? new ArrayList<>() : outputParams;
    }
}
