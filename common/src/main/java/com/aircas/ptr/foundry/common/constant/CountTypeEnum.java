package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 */
@AllArgsConstructor
@Getter
public enum CountTypeEnum {

    ONE(1, "一条"),
    MANY(2, "多条");

    private final int value;

    private final String name;

    public static CountTypeEnum convert(OntologyPropertyCategoryEnum categoryEnum) {
        return categoryEnum.equals(OntologyPropertyCategoryEnum.STATIC) ? MANY : ONE;
    }

}
