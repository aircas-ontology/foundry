package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为入参的取值来源，对应前端「输入来源」下拉。
 * <p>
 * PROPERTY/OBJECT/LIST 从实例数据取值，来源属性记在 property_unique_identifier 或 source_config；
 * FIXED 取定义时固定的字面量，记在 source_config；
 * EXTERNAL 定义时不取值，由测试/执行时调用方传入。
 */
@AllArgsConstructor
@Getter
public enum ActionParamSourceEnum {

    PROPERTY(1, "对象属性"),
    OBJECT(2, "对象"),
    LIST(3, "List"),
    FIXED(4, "固定值"),
    EXTERNAL(5, "外部输入");

    private final int value;

    private final String name;

}
