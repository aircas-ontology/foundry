package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.enums.ActionHandleRuleAddConditionEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import com.aircas.ptr.foundry.ontology.model.enums.RuleOperateType;
import com.aircas.ptr.foundry.ontology.model.param.RuleJudgeItemParam;
import com.aircas.ptr.foundry.ontology.model.param.RuleOperateParam;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定稿规则组合条件求值（纯逻辑，fixed 操作数，不依赖 Spring/数据库）：
 * 括号分组、AND 优先于 OR、数值/字符串比较、畸形表达式。
 */
class ActionRuleTriggerJudgeEvalTest {

    private final ActionRuleTriggerServiceImpl service = new ActionRuleTriggerServiceImpl();

    private boolean eval(List<RuleJudgeItemParam> items) throws Exception {
        Method m = ActionRuleTriggerServiceImpl.class.getDeclaredMethod("evalJudgeItems", List.class, java.util.Map.class);
        m.setAccessible(true);
        try {
            return (Boolean) m.invoke(service, items, new HashMap<String, java.util.Map<String, Object>>());
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    /** 恒真/恒假条件项：1 EQ 1 / 1 EQ 0 */
    private RuleJudgeItemParam item(boolean value) {
        return RuleJudgeItemParam.builder()
                .leftOperate(fixed("1"))
                .condition(ActionHandleRuleAddConditionEnum.EQ)
                .rightOperate(fixed(value ? "1" : "0"))
                .build();
    }

    private RuleOperateParam fixed(String value) {
        return RuleOperateParam.builder().type(RuleOperateType.FIXED).fixValue(value).build();
    }

    private RuleJudgeItemParam link(RuleJudgeItemParam item, ActionRuleConnectType flag) {
        item.setLinkFlag(flag);
        return item;
    }

    @Test
    void andPrecedenceOverOr() throws Exception {
        // true OR false AND false => true OR (false AND false) => true
        assertTrue(eval(List.of(
                link(item(true), ActionRuleConnectType.OR),
                link(item(false), ActionRuleConnectType.AND),
                item(false))));
        // false AND false OR true => (false AND false) OR true => true
        assertTrue(eval(List.of(
                link(item(false), ActionRuleConnectType.AND),
                link(item(false), ActionRuleConnectType.OR),
                item(true))));
    }

    @Test
    void parenthesesOverridePrecedence() throws Exception {
        // (true OR false) AND false => false
        var first = link(item(true), ActionRuleConnectType.OR);
        first.setIsLeft(true);
        var second = item(false);
        second.setIsRight(true);
        second.setLinkFlag(ActionRuleConnectType.AND);
        assertFalse(eval(List.of(first, second, item(false))));
    }

    @Test
    void nestedGroups() throws Exception {
        // true AND (false OR (true AND true)) => true
        var a = link(item(true), ActionRuleConnectType.AND);
        var b = link(item(false), ActionRuleConnectType.OR);
        b.setIsLeft(true);
        var c = link(item(true), ActionRuleConnectType.AND);
        c.setIsLeft(true);
        var d = item(true);
        d.setIsRight(true);
        var e = item(true);
        e.setIsRight(true);
        assertTrue(eval(List.of(a, b, c, d, e)));
    }

    @Test
    void numericAndStringCompare() throws Exception {
        // 120 GT 100 数值比较 => true（字符串字典序会得出 false）
        var numeric = RuleJudgeItemParam.builder()
                .leftOperate(fixed("120"))
                .condition(ActionHandleRuleAddConditionEnum.GT)
                .rightOperate(fixed("100"))
                .build();
        assertTrue(eval(List.of(numeric)));
        // READY EQ READY => true
        var text = RuleJudgeItemParam.builder()
                .leftOperate(fixed("READY"))
                .condition(ActionHandleRuleAddConditionEnum.EQ)
                .rightOperate(fixed("READY"))
                .build();
        assertTrue(eval(List.of(text)));
        // null 值比较 => false
        var nullLeft = RuleJudgeItemParam.builder()
                .leftOperate(null)
                .condition(ActionHandleRuleAddConditionEnum.EQ)
                .rightOperate(fixed("1"))
                .build();
        assertFalse(eval(List.of(nullLeft)));
    }

    @Test
    void malformedExpressionThrows() {
        // 只有左括号没有右括号
        var open = item(true);
        open.setIsLeft(true);
        assertThrows(IllegalStateException.class, () -> eval(List.of(open)));
        // 多余右括号
        var close = item(true);
        close.setIsRight(true);
        assertThrows(IllegalStateException.class, () -> eval(List.of(close)));
    }

    @Test
    void emptyItemsFalse() throws Exception {
        assertFalse(eval(List.of()));
    }
}