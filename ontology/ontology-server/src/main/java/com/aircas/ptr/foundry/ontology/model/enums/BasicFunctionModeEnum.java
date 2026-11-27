package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 基础函数的操作方式。
 * <p>
 * AGGREGATE 聚合：对一个聚合目标字段施加聚合函数，各对象的行合批后只算一个统一值；
 * QUERY 查询：按选中的字段列出实例数据，多对象时各对象的结果按 UNION ALL 合并后统一分页。
 */
@AllArgsConstructor
@Getter
public enum BasicFunctionModeEnum {

    AGGREGATE(1, "聚合"),
    QUERY(2, "查询");

    private final int value;

    private final String name;

}
