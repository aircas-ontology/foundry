package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 聚合函数枚举
 */
@Getter
@AllArgsConstructor
public enum AggFuncEnum {

    SUM("SUM"),
    COUNT("COUNT"),
    AVG("AVG"),
    MAX("MAX"),
    MIN("MIN"),
    DISTINCT("DISTINCT"),
    ;

    private final String value;

}
