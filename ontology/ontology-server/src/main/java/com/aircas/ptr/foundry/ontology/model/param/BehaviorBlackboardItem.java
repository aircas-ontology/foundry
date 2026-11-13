package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorBlackboardSourceEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang.StringUtils;

/**
 * 黑板项：声明键名、类型和取值来源。
 * <p>三种来源互斥，取值地点各不相同：FIXED 用本项的 value；EXTERNAL 由执行请求 inputs 覆盖，
 * 不传时回落到同名变量的默认值；PROPERTY 选择本空间对象的一个属性，在每次条件求值前按本次执行选中的实例回读，
 * 不接受执行入参也没有默认值。黑板只决定节点执行不执行，行为节点需要的外部入参由执行请求 nodeExecutes 按节点单独给。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树黑板项")
public class BehaviorBlackboardItem {

    @Schema(name = "key", description = "黑板键名，表达式里用裸名引用", example = "ammoCount")
    private String key;

    @Schema(name = "valueType", description = "值类型，固定值取值、外部输入的默认值与执行入参、对象属性的绑定属性类型都必须与它一致",
            example = "INTEGER")
    private FunctionParamTypeEnum valueType;

    @Schema(name = "source", description = "取值来源：FIXED=固定值，EXTERNAL=外部输入，PROPERTY=对象属性；"
            + "不传时按历史口径推导（绑了属性按 PROPERTY，否则按 EXTERNAL）", example = "EXTERNAL")
    private BehaviorBlackboardSourceEnum source;

    @Schema(name = "value", description = "固定值来源的取值，类型必须与 valueType 一致；外部输入和对象属性来源不填",
            example = "10")
    private Object value;

    @Schema(name = "bindPropertyUniqueIdentifier",
            description = "对象属性来源时必填：本空间对象的属性唯一标识，每次条件求值前按本次执行选中的实例回读；"
                    + "固定值和外部输入来源不能绑定属性", example = "prop_ammo_count")
    private String bindPropertyUniqueIdentifier;

    @Schema(name = "description", description = "说明")
    private String description;

    /** 历史定义没有 source：绑了属性按对象属性，否则按外部输入，保持原行为 */
    public BehaviorBlackboardSourceEnum resolvedSource() {
        if (source != null) {
            return source;
        }
        return StringUtils.isNotBlank(bindPropertyUniqueIdentifier)
                ? BehaviorBlackboardSourceEnum.PROPERTY
                : BehaviorBlackboardSourceEnum.EXTERNAL;
    }
}
