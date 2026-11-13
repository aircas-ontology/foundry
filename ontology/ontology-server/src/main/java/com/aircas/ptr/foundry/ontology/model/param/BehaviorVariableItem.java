package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorVariableScopeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 变量项：外部输入来源黑板键的兜底默认值。
 * <p>作用域目前只有 RUNTIME，值只在一次树执行内存在，执行结束即丢弃，因此不落运行时状态表。
 * 固定值来源的取值在黑板项自己的 value 上，对象属性来源每次条件求值按实例回读，这两种来源都不配变量。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树变量项")
public class BehaviorVariableItem {

    @Schema(name = "name", description = "变量名，必须与某个外部输入来源的黑板键同名", example = "ammoCount")
    private String name;

    @Schema(name = "valueType", description = "值类型，与黑板项的类型必须一致", example = "INTEGER")
    private FunctionParamTypeEnum valueType;

    @Schema(name = "scope", description = "作用域，目前只有 RUNTIME", example = "RUNTIME")
    private BehaviorVariableScopeEnum scope;

    @Schema(name = "defaultValue", description = "默认值：执行入参没给这个键时用它兜底，可以不填", example = "10")
    private Object defaultValue;
}
