package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ActionParamSourceEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@Schema(description = "行为参数映射VO")
public class ActionParamMappingVO {

    @Schema(description = "关联的函数参数值的spel表达式", example = "data[0].name")
    private String functionParamExpression;

    @Schema(description = "关联的函数参数id；不绑算子的行为为空")
    private Long functionParamId;

    @Schema(description = "参数名称")
    private String parameterName;

    @Schema(description = "输入来源：PROPERTY 对象属性 / OBJECT 对象 / LIST List / FIXED 固定值 / EXTERNAL 外部输入")
    private ActionParamSourceEnum sourceType;

    @Schema(description = "输入来源配置（JSON）：固定值存字面量，List 存属性唯一标识数组")
    private String sourceConfig;

    @Schema(description = "对象入参的子字段路径（相对该算子参数，点分层级）；标量参数或整包取值为空", example = "position.longitude")
    private String paramPath;

    @Schema(description = "属性关联对象id", required = true, example = "abcd1234")
    private String ontologyUniqueIdentifier;

    @Schema(description = "参数对应的对象属性ID", required = true, example = "fdsfa-gfdsagf-dfs")
    private String propertyUniqueIdentifier;

}
