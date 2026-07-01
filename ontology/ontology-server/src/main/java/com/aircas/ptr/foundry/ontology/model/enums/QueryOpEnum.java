package com.aircas.ptr.foundry.ontology.model.enums;

/**
 * 查询操作枚举
 */
public enum QueryOpEnum {
    EQ, NE,
    LIKE, LIKE_LEFT, LIKE_RIGHT,
    IN, NOT_IN,
    BETWEEN, NOT_BETWEEN,
    GT, GE, LT, LE,
    IS_NULL, IS_NOT_NULL,
    // 函数型（value 里写函数表达式，例如JSON_EXTRACT({0}, '$.level') = {1}，{0} 为属性apiName，{1},{2}..{n}. 为values数组值依次填入
    APPLY
}