package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author yangj
 */

@AllArgsConstructor
@Getter
public enum OntologyPropertyTypeEnum {

    STATIC(1, "静态属性"),
    DYNAMIC(2, "动态属性"),
    ;

    private final Integer value;
    private final String name;
}
