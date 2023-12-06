package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum FormatType {

    FILE(0, "非结构化数据"),
    STRUCT(1, "结构化数据");

    private final int value;
    private final String name;
}
