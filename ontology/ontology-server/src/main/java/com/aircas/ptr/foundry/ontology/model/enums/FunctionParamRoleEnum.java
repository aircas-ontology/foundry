package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 函数参数角色枚举，用于区分 BASIC_QUERY 类型函数中参数的语义角色。
 */
@AllArgsConstructor
@Getter
public enum FunctionParamRoleEnum {

    AGGREGATION(1, "聚合目标"),
    FILTER(2, "过滤条件"),

    ;

    private final int value;

    private final String name;

}
