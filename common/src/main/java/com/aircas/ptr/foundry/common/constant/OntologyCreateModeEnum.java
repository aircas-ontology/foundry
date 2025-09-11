package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author yangj
 */

@AllArgsConstructor
@Getter
public enum OntologyCreateModeEnum {

    DATASOURCE(1, "数据源"),
    INHERIT(2, "继承方式"),
    NONE(3, "无模式"),
    ;

    private final Integer value;
    private final String name;
}
