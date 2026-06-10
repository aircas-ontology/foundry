package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 * 实体表结构变更事件类型
 */
@Getter
@AllArgsConstructor
public enum DatasourceEventTypeEnum {

    CREATE_TABLE(1, "创建实体属性表（主表或关联表）"),
    ADD_COLUMN(2, "添加实体表属性"),
    UPDATE_COLUMN(3, "修改实体表属性"),
    DELETE_COLUMN(4, "删除实体表属性"),
    DROP_TABLE(5, "删除实体属性表（主表或关联表）"),

    ;

    private final Integer code;
    private final String description;
}
