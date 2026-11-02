package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 基础行为的基础操作，仅行为类型为属性值、实例关系时有值。
 */
@AllArgsConstructor
@Getter
public enum ActionOperationEnum {

    CREATE(1, "新增"),
    UPDATE(2, "修改"),
    DELETE(3, "删除");

    private final int value;

    private final String name;

}
