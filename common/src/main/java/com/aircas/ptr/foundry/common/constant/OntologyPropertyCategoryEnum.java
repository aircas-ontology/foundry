package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * @author yangj
 */

@AllArgsConstructor
@Getter
public enum OntologyPropertyCategoryEnum {

    STATIC(1, "静态属性"),
    DYNAMIC(2, "动态属性"),
    ;

    private final Integer value;
    private final String name;

    public static OntologyPropertyCategoryEnum getByValue(Integer value) {
        return Arrays.stream(OntologyPropertyCategoryEnum.values()).filter(v -> v.getValue().equals(value)).findFirst().get();
    }
}
