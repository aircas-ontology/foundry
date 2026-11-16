package com.aircas.ptr.foundry.ontology.service;

import java.util.Set;

/**
 * 规则行为调度触发（定稿 judgeRules 结构）：规则启用时先评估一遍条件绑定实例的存量数据；
 * 此后数据湖实例插入/更新（CDC）时先按变更表+字段过滤出配置了规则的属性，
 * 再定向评估条件引用了该实例且命中变更字段的规则，满足则按 actionParam
 * 显式指定的目标执行行为，执行结果落 action_handle_log。
 * <p>电平触发：每次满足都执行，谁满足跑谁；不处理删除事件。
 */
public interface ActionRuleTriggerService {

    /**
     * 实例变更触发入口（CDC 插入/更新调用）。
     *
     * @param ontologyUniqueIdentifier 发生变更的对象唯一标识
     * @param entityPrimaryKey         发生变更的实例主键
     * @param table                    发生变更的数据源表名
     * @param changedColumns           本次变更的列名集合（插入为全部列，更新为前后差异列）
     */
    void onInstanceChange(String ontologyUniqueIdentifier, Object entityPrimaryKey,
                          String table, Set<String> changedColumns);

    /**
     * 规则启用时对条件绑定的实例立即评估一遍（存量数据），满足即执行。
     *
     * @param ruleId 规则调度 id
     */
    void evaluateRuleNow(Long ruleId);
}