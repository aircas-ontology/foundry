package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OntologyComponentEnum {

    PROPERTY(1, "属性"),
    LINK(2, "关系"),
    ACTION(3, "动作"),
    FUNCTION(4, "函数"),
    MODEL(5, "模型");

    private Integer value;
    private String name;
}
