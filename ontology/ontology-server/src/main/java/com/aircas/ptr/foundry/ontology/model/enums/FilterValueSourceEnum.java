package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 基础函数行为过滤条件的取值来源，对应前端「取值方式」下拉。
 * <p>FIXED 取配置里写死的字面量；EXTERNAL 定义时不取值，由测试/执行时调用方按 {@code paramName} 传入。</p>
 */
@AllArgsConstructor
@Getter
public enum FilterValueSourceEnum {

    FIXED(1, "固定值"),
    EXTERNAL(2, "外部输入");

    private final int value;
    private final String name;

}