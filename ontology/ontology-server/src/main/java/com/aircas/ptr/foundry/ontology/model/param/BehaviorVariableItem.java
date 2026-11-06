package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorVariableScopeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 变量项：黑板键名对应的值。
 * <p>作用域目前只有 RUNTIME，值只在一次树执行内存在，执行结束即丢弃，因此不落运行时状态表。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树变量项")
public class BehaviorVariableItem {

    @Schema(name = "name", description = "变量名，必须与某个黑板键名同名", example = "ammoCount")
    private String name;

    @Schema(name = "valueType", description = "值类型，与黑板项的类型必须一致", example = "INTEGER")
    private FunctionParamTypeEnum valueType;

    @Schema(name = "scope", description = "作用域，目前只有 RUNTIME", example = "RUNTIME")
    private BehaviorVariableScopeEnum scope;

    @Schema(name = "defaultValue", description = "默认值；执行时调用方传了同名键值则覆盖它，类型不符会报错")
    private Object defaultValue;
}
