package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.enums.RuleMonitorTypeEnum;

import java.util.Set;

/**
 * 规则行为调度触发（定稿 judgeRules 结构）：规则启用时先评估一遍条件绑定实例的存量数据；
 * 此后数据湖行级 CDC 事件（新增/更新/删除，主表与扩展存储分组表均可）到达时，
 * 按事件对象+分组+类型匹配监测条件，按变更表+字段匹配属性条件，定向评估条件引用了该实例的规则，
 * 满足则按 actionParam 显式指定的目标执行行为，执行结果落 action_handle_log。
 * <p>电平触发：每次满足都执行，谁满足跑谁；监测条件只做事件触发，不在启用时评估存量。
 * 监测条件之间 AND 为跨事件累计：同一实例的多个分组事件在窗口内先后发生、凑齐条件后触发并清空累计。
 */
public interface ActionRuleTriggerService {

    /**
     * 数据湖行级变更触发入口（CDC 新增/更新/删除调用）。
     *
     * @param ontologyUniqueIdentifier 变更行所属对象唯一标识
     * @param entityPrimaryKey         变更行关联的实例主键（扩展分组表取外键指向的主表实例）
     * @param table                    发生变更的数据源表名
     * @param storageGroup             变更行所属存储分组：主表为 main，扩展表为属性上的分组名
     * @param eventType                事件类型：insert/update/delete
     * @param changedColumns           本次变更的列名集合（插入为全部列，更新为前后差异列，删除为删除前行全部列）
     */
    void onDataChange(String ontologyUniqueIdentifier, Object entityPrimaryKey, String table,
                      String storageGroup, RuleMonitorTypeEnum eventType, Set<String> changedColumns);

    /**
     * 规则启用时对条件绑定的实例立即评估一遍（存量数据），满足即执行；含监测条件的规则跳过。
     *
     * @param ruleId 规则调度 id
     */
    void evaluateRuleNow(Long ruleId);
}