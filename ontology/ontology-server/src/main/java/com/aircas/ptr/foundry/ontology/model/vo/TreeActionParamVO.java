package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionParamSourceEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 行为树可绑定行为的参数定义，前端据此展示行为要的入参和出参。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树可绑定行为的参数")
public class TreeActionParamVO {

    @Schema(name = "name", description = "参数名，行为的外部输入参数按它从执行请求取值（nodeExecutes.params 优先，回退 inputs）；"
            + "绑定的键就是它。列表元素/对象子字段的叶子名在多个入参上重复时这里是「父参数名.字段路径」，绑定后照样按它取覆盖值",
            example = "satelliteId")
    private String name;

    @Schema(name = "paramName", description = "所属顶层算子入参名：name 带上级时就是名字的前缀，仅可绑定入参列表（bindableInputs）里有值；"
            + "数据位置仍可只按它给（同一参数只给一个位置时）", example = "tracks")
    private String paramName;

    @Schema(name = "type", description = "参数类型", example = "STRING")
    private FunctionParamTypeEnum type;

    @Schema(name = "description", description = "参数说明")
    private String description;

    @Schema(name = "fieldName", description = "出参的英文字段名（出参字段名，缺时退回算子出参名），仅可绑定出参列表（bindableOutputs）里有值："
            + "界面展示用它，点名仍用 name", example = "longitude")
    private String fieldName;

    @Schema(name = "sourceType", description = "该入参在行为上声明的取值来源，仅可绑定入参列表（bindableInputs）里有值："
            + "节点上不配来源时按它取值", example = "EXTERNAL")
    private ActionParamSourceEnum sourceType;
}
