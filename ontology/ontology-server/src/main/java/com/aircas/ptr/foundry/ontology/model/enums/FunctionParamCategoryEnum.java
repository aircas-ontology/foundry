package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 */
@AllArgsConstructor
@Getter
public enum FunctionParamCategoryEnum {

    INPUT(1, "输入参数"),
    OUTPUT(2, "输出参数"),

    ;

    private final int value;

    private final String name;


}
