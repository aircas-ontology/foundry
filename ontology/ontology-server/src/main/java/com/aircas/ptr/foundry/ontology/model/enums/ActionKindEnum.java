package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为类别。
 * <p>
 * BASE 基础行为直接操作实例数据（属性值、实例关系）或只读查询（基础算子），不写业务逻辑；
 * APPLICATION 应用行为绑定脚本/外部API/容器算子，并必须关联一条关系，执行时额外写入一条实例关系；
 * TREE 行为树，definition 存节点树 JSON，不绑定算子也不指向对象类型。
 * 落库存枚举名，与 function.type 口径一致。
 */
@AllArgsConstructor
@Getter
public enum ActionKindEnum {

    BASE(1, "基础行为"),
    APPLICATION(2, "应用行为"),
    TREE(3, "行为树");

    private final int value;

    private final String name;

}
