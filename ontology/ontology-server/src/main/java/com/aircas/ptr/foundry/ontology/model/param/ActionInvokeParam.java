package com.aircas.ptr.foundry.ontology.model.param;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 内部的行为调用参数：行为树节点把树级/节点级的分组行主键按对象传进单行为执行体时用它。
 * <p>对外接口只用 {@link ActionTestParam}，按对象方向指定实例/行的两个字段不在那里；
 * 类不进 Swagger，字段含义见 {@link ActionInstanceTargetParam}。</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActionInvokeParam extends ActionTestParam {

    /** 入参方向按对象给的数据位置：对象id -> {实例主键, 分组 -> 行主键} */
    private Map<String, ActionInstanceTargetParam> inputEntities;

    /** 出参方向按对象给的数据位置：对象id -> {实例主键, 分组 -> 行主键} */
    private Map<String, ActionInstanceTargetParam> outputEntities;
}
