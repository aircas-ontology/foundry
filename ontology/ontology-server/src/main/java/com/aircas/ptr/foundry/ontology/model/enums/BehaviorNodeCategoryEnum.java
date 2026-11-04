package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为树节点类别，决定子节点数量约束。
 * <p>
 * COMPOSITE 容器节点按执行模式调度多个子节点；DECORATOR 装饰器节点恰好一个子节点；
 * LEAF 叶子节点（判断节点与行为节点）没有子节点。
 */
@AllArgsConstructor
@Getter
public enum BehaviorNodeCategoryEnum {

    COMPOSITE(1, "容器节点"),
    DECORATOR(2, "装饰器节点"),
    LEAF(3, "叶子节点");

    private final int value;

    private final String name;

}
