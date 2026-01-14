package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Status {

    ENABLE(1, "启用"),
    DISABLE(0, "停用"),
    DELETE(-1, "删除");

    private final int value;
    private final String name;
}
