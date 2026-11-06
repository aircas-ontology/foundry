package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 黑板条件节点的比较方式，落 JSON 存枚举名。
 * <p>
 * 数值类型只允许 GT/GE/LT/LE/EQ/NE，字符串允许 CONTAINS/NOT_CONTAINS，
 * IS_EMPTY/IS_NOT_EMPTY 不取 value；具体的类型与运算符搭配在保存期校验。
 */
@AllArgsConstructor
@Getter
public enum BehaviorCompareOpEnum {

    EQ(1, "等于"),
    NE(2, "不等于"),
    GT(3, "大于"),
    GE(4, "大于等于"),
    LT(5, "小于"),
    LE(6, "小于等于"),
    CONTAINS(7, "包含"),
    NOT_CONTAINS(8, "不包含"),
    IS_EMPTY(9, "为空"),
    IS_NOT_EMPTY(10, "不为空");

    private final int value;

    private final String name;

}
