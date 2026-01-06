package com.aircas.ptr.foundry.ontology.common.enums;

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
public enum OntologyOrderByEnum {

    NAME("display_name"),
    UPDATE_TIME("update_time"),
    QUERY_TIME("latest_query_time"),
    ;

    private final String value;
}
