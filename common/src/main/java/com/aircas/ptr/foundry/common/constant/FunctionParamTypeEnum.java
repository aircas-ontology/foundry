package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 */
@AllArgsConstructor
@Getter
public enum FunctionParamTypeEnum {

    OBJECT(1, "对象类型"),
    STRING(2, "字符串"),
    INTEGER(3, "整型"),
    LONG(4, "长整型"),
    FLOAT(5, "单浮点型"),
    DOUBLE(6, "双浮点型"),
    BOOL(7, "布尔类型"),
    ;

    private final int value;

    private final String name;


}
