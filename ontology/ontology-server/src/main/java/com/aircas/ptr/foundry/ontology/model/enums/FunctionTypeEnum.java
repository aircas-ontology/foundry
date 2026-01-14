package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 */
@AllArgsConstructor
@Getter
public enum FunctionTypeEnum {

    CUSTOMIZE(1, "自定义"),
    EXTERNAL(2, "外部");

    private final int value;

    private final String name;


}
