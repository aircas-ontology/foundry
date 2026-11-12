package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 黑板项：只声明键名与类型，值由变量项的默认值或执行入参提供。
 * <p>黑板不参与行为执行，只用来决定节点执行不执行。绑了对象属性的键在每次条件求值前按库里的当前值重读，
 * 值来自本次执行选中的实例；这类键不接受执行入参，同一个键又当行为外部入参会在执行前按冲突报错。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树黑板项")
public class BehaviorBlackboardItem {

    @Schema(name = "key", description = "黑板键名，表达式里用裸名引用", example = "ammoCount")
    private String key;

    @Schema(name = "valueType", description = "值类型，与变量项同名字段的类型必须一致", example = "INTEGER")
    private FunctionParamTypeEnum valueType;

    @Schema(name = "bindPropertyUniqueIdentifier",
            description = "绑定的对象属性id：每次条件求值前按本次执行的实例从库里读该属性的当前值刷进本键；"
                    + "为空表示不绑定，值只用默认值或执行入参。绑定属性键不接受执行入参，也不能同时是行为的外部入参",
            example = "prop_ammo_count")
    private String bindPropertyUniqueIdentifier;

    @Schema(name = "description", description = "说明")
    private String description;
}
