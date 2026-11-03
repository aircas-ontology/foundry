package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionParamSourceEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangj
 */
@Schema(description = "行为数据参数新增模型")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActionParamMappingCreateParam {

    @Schema(description = "参数对应的函数参数ID；绑定算子的行为必填，属性值/实例关系行为不传", example = "fdsfa-gfdsagf-dfs")
    private Long functionParamId;

    @Schema(description = "行为对应的函数参数的表达式", example = "data.user[0].name")
    private String functionParamExpression;

    @Schema(description = "参数名称；不绑算子的行为用它标识目标属性或外部输入名", example = "speed")
    private String parameterName;

    @Schema(description = "输入来源：PROPERTY 对象属性 / OBJECT 对象 / LIST List / FIXED 固定值 / EXTERNAL 外部输入；不传按 PROPERTY 处理", example = "FIXED")
    private ActionParamSourceEnum sourceType;

    @Schema(description = "输入来源配置：固定值存字面量（合法 JSON 按 JSON 解析，否则按原文本），List 存多个属性唯一标识的 JSON 数组；其余来源不传", example = "120.5")
    private String sourceConfig;

    @Schema(description = "对象入参的子字段路径（仅入参绑定使用）：算子的对象参数没有独立的函数参数 id，" +
            "用该路径指明绑的是哪个子字段，相对参数名按点分层级，带上参数名全路径也可识别；" +
            "标量参数或整个对象一次取值时不传", example = "position.longitude")
    private String paramPath;

    @Schema(description = "参数对应的对象属性ID；来源为对象属性/对象/List 时是取值属性，不绑算子的行为是目标属性", required = true, example = "fdsfa-gfdsagf-dfs")
    private String propertyUniqueIdentifier;

    @Schema(description = "参数对应的对象", required = true, example = "fdsfa-gfdsagf-dfs")
    private String ontologyUniqueIdentifier;
}
