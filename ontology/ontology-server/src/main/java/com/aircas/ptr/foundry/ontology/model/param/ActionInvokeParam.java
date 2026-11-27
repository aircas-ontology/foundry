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

    /**
     * LIST 入参按子级字段给的数据位置：入参名 -> 属性唯一标识 -> 位置。
     * <p>请求里的键是子级字段（父参数名.字段路径），落到执行侧归到该子级绑定的属性上。
     * 同一个 LIST 入参的子级可以来自不同对象、不同实例，所以不能合并成一个入参级位置：
     * 每个子级按自己的实例读，给了行主键按行取，没给按该子表默认行数读。</p>
     */
    private Map<String, Map<String, ActionInstanceTargetParam>> inputFieldParams;

    /**
     * 对象模式测试的子表读取规则（请求里的 listReadRules 按子级字段归位后的形状）：
     * 顶层入参名 -> 对象id -> 存储分组 -> 规则。
     * <p>请求体的键是子级字段路径，落到执行侧就是这些字段所在的「对象 + 子表」，读子表时按它取行。</p>
     */
    private Map<String, Map<String, Map<String, ActionTestListReadRuleParam>>> resolvedReadRules;

    /**
     * 行为节点上配了取值来源的入参：参数名 -> 已解析出的值，取值时优先于行为自己的绑定来源。
     * <p>只有行为树执行会传；参数名用 {@code ontology_action_mapping_in.parameter_name}，
     * 属性、固定值、对象这些非外部输入来源的绑定行也按它覆盖，免得配了不生效。</p>
     */
    private Map<String, Object> paramValueOverrides;

    /**
     * 本次执行真正解析出的入参：传入一个可变容器，执行侧把顶层入参名 -> 值回填进去，
     * 行为树的成功条件表达式按 {@code #inputs} 读取。不传就不收集，对外测试链路不关心。
     * <p>涉及关系的行为按对端逐条执行，多个线程会往同一个容器里写，执行侧对容器加锁、最后一条覆盖；
     * 读在全部执行结束之后。</p>
     */
    private Map<String, Object> resolvedInputs;
}
