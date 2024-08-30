package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @enumName: QuerySortEnum
 * @author: yangj
 * @date: 2024/8/30 18:16
 * @version: 1.0
 * @description: 查询排序
 */
@Getter
@AllArgsConstructor
public enum QuerySortEnum {

    ASC("ASC", "升序"),
    DESC("DESC", "降序");

    private final String value;
    private final String name;
}
