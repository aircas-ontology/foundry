package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为树节点写回数据位置的传参方式，只在执行要求里返回给调用方，不落库。
 * <p>OUTPUT_PARAM：绑算子的行为按出参绑定行定位，用 nodeExecutes[节点].outputParams[key] 传实例与分组行；
 * DATA_PRIMARY_KEY：属性值-修改/删除这类不绑算子的行为只写一行，用 nodeExecutes[节点].dataPrimaryKey 传行主键。</p>
 */
@AllArgsConstructor
@Getter
public enum TreeOutputTargetModeEnum {

    OUTPUT_PARAM(1, "按出参绑定行写回"),
    DATA_PRIMARY_KEY(2, "按分组表行写回");

    private final int value;

    private final String name;

}
