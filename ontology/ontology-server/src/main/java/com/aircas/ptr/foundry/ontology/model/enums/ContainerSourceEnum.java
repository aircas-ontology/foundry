package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 容器来源：USER 为用户通过创建向导生成的容器算子，SYSTEM 为平台预置的系统容器（不可编辑/启停/删除）。
 */
@AllArgsConstructor
@Getter
public enum ContainerSourceEnum {

    USER("用户创建"),
    SYSTEM("系统预置");

    private final String label;
}
