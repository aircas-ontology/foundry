package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OntologyComponentEnum {

    PROPERTY("属性"),
    LINK("关系"),
    ACTION("动作"),
    FUNCTION("函数");

    private String name;
}
