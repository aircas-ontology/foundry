package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.enums.RuleMonitorTypeEnum;

import java.util.Map;
import java.util.Set;

/**
 * 规则行为调度触发（judgeRules 结构）：每条条件先选事件类型（新增/修改/删除），再选对象+属性+输入值比较。
 * 数据湖行级 CDC 事件（新增/修改/删除，主表与扩展存储分组表均可）到达时，
 * 新增/删除条件按事件行属性值判断，修改条件按实例当前属性值判断，
 * 满足则按 actionParam 显式指定的目标执行行为，执行结果落 action_handle_log。
 * <p>电平触发：每次满足都执行，谁满足跑谁；新增/删除只做事件触发，不在启用时评估存量。
 * 不相关条件之间 AND 为跨事件累计：同一实例的多个分组事件在窗口内先后发生、凑齐条件后触发并清空累计。
 */
public interface ActionRuleTriggerService {

    /**
     * 数据湖行级变更触发入口（CDC 新增/修改/删除调用）。
     *
     * @param ontologyUniqueIdentifier 变更行所属对象唯一标识
     * @param entityPrimaryKey         变更行关联的实例主键（扩展分组表取外键指向的主表实例）
     * @param table                    发生变更的数据源表名
     * @param storageGroup             变更行所属存储分组：主表为 main，扩展表为属性上的分组名
     * @param eventType                事件类型：新增/修改/删除
     * @param changedColumns           本次变更的列名集合（插入为全部列，更新为前后差异列，删除为删除前行全部列）
     * @param rowValues                事件行的列值（新增/修改取变更后整行，删除取删除前整行），供按属性取值比较
     */
    void onDataChange(String ontologyUniqueIdentifier, Object entityPrimaryKey, String table,
                      String storageGroup, RuleMonitorTypeEnum eventType, Set<String> changedColumns,
                      Map<String, Object> rowValues);

    /**
     * 规则启用时对条件绑定的实例立即评估一遍（存量数据），满足即执行；含新增/删除事件条件的规则跳过。
     *
     * @param ruleId 规则调度 id
     */
    void evaluateRuleNow(Long ruleId);
}