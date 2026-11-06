package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 黑板项：只声明键名与类型，值由变量项的默认值或执行入参提供。
 * <p>行为节点写属性成功后，引擎按 bindPropertyUniqueIdentifier 用新值替换该 key 的黑板值，
 * 所以黑板是唯一数据源，不需要每个节点再去查实例。</p>
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

    @Schema(name = "bindPropertyUniqueIdentifier", description = "绑定的对象属性id；行为节点写该属性成功后用新值替换本键，为空表示不回填", example = "prop_ammo_count")
    private String bindPropertyUniqueIdentifier;

    @Schema(name = "description", description = "说明")
    private String description;
}
