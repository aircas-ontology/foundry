package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 */
@AllArgsConstructor
@Getter
public enum FunctionModelEnum {

    PYTORCH(1, "pytorch"),
    TENSORFLOW(2, "tensorflow");

    private final int value;

    private final String name;


}
