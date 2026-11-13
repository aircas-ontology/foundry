package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 黑板键的取值来源，落 JSON 存枚举名。
 * <p>固定值只认黑板项自己的 value；外部输入认执行请求 inputs，没传时回落到同名变量的默认值；
 * 对象属性在每次条件求值前按本次执行选中的实例回读，没有默认值。
 * 三种来源互斥，保存期按来源校验对应字段。</p>
 */
@AllArgsConstructor
@Getter
public enum BehaviorBlackboardSourceEnum {

    FIXED(1, "固定值"),
    EXTERNAL(2, "外部输入"),
    PROPERTY(3, "对象属性");

    private final int value;

    private final String name;

}
