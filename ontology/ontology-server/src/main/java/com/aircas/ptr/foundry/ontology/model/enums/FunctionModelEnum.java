package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 */
@AllArgsConstructor
@Getter
public enum FunctionModelEnum {

    PYTORCH(1, "pytorch"),
    TENSORFLOW(2, "tensorflow"),
    OTHER(3, "other"),
    ;

    private final int value;

    private final String name;


}
