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
 * 行为节点上的算子入参：按绑定行为声明的入参逐个列出，并说明取值从哪来。
 * <p>只有来源是外部输入的才需要调用方在 nodeExecutes[节点].params 里给值；
 * 对象属性、对象、List 来源的入参按节点选定的实例读库里的值，固定值取行为里的字面量。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树节点算子入参")
public class TreeExecuteInputVO {

    @Schema(name = "key", description = "参数名：与绑定行为声明的入参同名；来源是外部输入时按节点传给 nodeExecutes[节点标识].params",
            example = "ammoCount")
    private String key;

    @Schema(name = "valueType", description = "行为声明的参数类型", example = "INTEGER")
    private FunctionParamTypeEnum valueType;

    @Schema(name = "required", description = "调用方是否必须给值：只有来源是外部输入时为 true，其余来源执行时自己取",
            example = "true")
    private boolean required;

    @Schema(name = "sourceType", description = "取值来源：EXTERNAL 外部输入（按节点传值）/ PROPERTY 对象属性（按所选实例读库里当前值）"
            + " / FIXED 固定值（取行为里的字面量）/ OBJECT、LIST 从实例读取", example = "PROPERTY")
    private ActionParamSourceEnum sourceType;

    @Schema(name = "objectIdentifier", description = "来源是对象属性/对象/List 时，从哪个对象的实例取值；外部输入、固定值为空")
    private String objectIdentifier;

    @Schema(name = "propertyUniqueIdentifier", description = "来源是对象属性时绑定的属性唯一标识；其他来源为空")
    private String propertyUniqueIdentifier;

    @Schema(name = "propertyDisplayName", description = "来源是对象属性时绑定的属性显示名称", example = "起始经度")
    private String propertyDisplayName;

    @Schema(name = "description", description = "参数说明，取算子参数或属性的说明")
    private String description;
}
