package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 基础行为的类型，仅 action_kind=BASE 时有值。
 * <p>
 * PROPERTY_VALUE 属性值、LINK 实例关系两类不绑定算子，配有 新增/修改/删除 三种基础操作；
 * BASIC_OPERATOR 基础算子绑定 BASIC_QUERY 算子，无基础操作，输出只能仅返回结果。
 */
@AllArgsConstructor
@Getter
public enum ActionSubjectTypeEnum {

    PROPERTY_VALUE(1, "属性值"),
    LINK(2, "实例关系"),
    BASIC_OPERATOR(3, "基础算子");

    private final int value;

    private final String name;

}
