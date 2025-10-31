package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 */
@AllArgsConstructor
@Getter
public enum FunctionTypeEnum {

    CUSTOMIZE(1, "自定义"),
    EXIST(2, "已有");

    private final int value;

    private final String name;


}
