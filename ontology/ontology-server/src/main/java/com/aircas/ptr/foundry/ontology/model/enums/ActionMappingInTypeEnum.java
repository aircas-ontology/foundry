package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @enumName: ActionMappingInTypeEnum
 * @author: yangj
 * @date: 2024/9/1 16:18
 * @version: 1.0
 * @description: 行为输入参数类型
 */
@AllArgsConstructor
@Getter
public enum ActionMappingInTypeEnum {

    ONTOLOGY("1", "实体本身");

    private final String code;
    private final String des;
}
