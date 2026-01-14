package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DbStatus {

    /**
     * 未删除状态
     */
    NOT_DELETED(1,"未删除"),

    /**
     * 已删除状态
     */
    DELETED(0,"已删除");

    /**
     * 状态码
     */
    private final int value;

    /**
     * 状态描述
     */
    private final String name;

}
