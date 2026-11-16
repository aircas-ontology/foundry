package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.enums.ActionHandleRuleAddConditionEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.RuleOperateType;
import com.aircas.ptr.foundry.ontology.model.enums.ScheduleStatus;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.RuleActionParam;
import com.aircas.ptr.foundry.ontology.model.param.RuleJudgeGroupParam;
import com.aircas.ptr.foundry.ontology.model.param.RuleJudgeItemParam;
import com.aircas.ptr.foundry.ontology.model.param.RuleOperateParam;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleLog;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionHandleLogMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.ActionHandleRuleService;
import com.aircas.ptr.foundry.ontology.service.ActionRuleTriggerService;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则行为调度触发实现（定稿 judgeRules 结构）。
 * <p>
 * 电平触发：规则启用时先对条件绑定的实例评估一遍存量数据；此后 CDC 实例插入/更新 ->
 * 变更表+列映射到对象属性（字段无属性配置则不触发）-> 捞开启中的规则 ->
 * 定向匹配（条件操作数引用了本次变更对象+实例且命中变更字段的组才评估）->
 * 实时取操作数实例属性值 -> 括号+AND/OR 组合求值（可整体取反）-> 满足则按 actionParam
 * 显式指定的目标执行行为（entity_json 与周期调度同形式）-> 结果落 action_handle_log。
 * 不处理删除事件。
 * <p>
 * 不加事务：testAction 跨数据湖/图库写库，日志独立提交，不能被随后的异常回滚掉。
 * 单条规则评估/执行失败只记日志，不影响其余规则与实例同步。
 */
@Slf4j
@Service
public class ActionRuleTriggerServiceImpl implements ActionRuleTriggerService {

    /** 同一规则+组+触发实例的执行冷却，防自写循环：行为写库 -> CDC 再触发 -> 条件仍成立 -> 无限执行 */
    private static final long JUDGE_COOLDOWN_MS = 10_000L;
    private final Map<String, Long> judgeCooldownMap = new ConcurrentHashMap<>();

    @Resource
    private ActionHandleRuleService actionHandleRuleService;

    @Lazy
    @Resource
    private EntityService entityService;

    @Lazy
    @Resource
    private OntologyActionService ontologyActionService;

    @Resource
    private ActionHandleLogMapper actionHandleLogMapper;

    @Resource
    private OntologyActionMapper actionMapper;

    @Resource
    private OntologyPropertyMapper propertyMapper;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public void onInstanceChange(String ontologyUniqueIdentifier, Object entityPrimaryKey,
                                 String table, Set<String> changedColumns) {
        if (StringUtils.isBlank(ontologyUniqueIdentifier) || entityPrimaryKey == null
                || StringUtils.isBlank(table) || CollectionUtils.isEmpty(changedColumns)) {
            return;
        }
        // 先判断本次变更的表+字段上有没有配置属性：变更列映射到对象属性，
        // 一个属性都没命中说明这些字段没有规则可挂，直接不进规则调度
        Set<String> changedPropertyKeys = changedPropertyKeys(ontologyUniqueIdentifier, table, changedColumns);
        if (changedPropertyKeys.isEmpty()) {
            return;
        }
        // 条件跨对象，监控对象在条件操作数里，无法按单对象索引捞，取全部开启中的规则内存定向匹配
        var rules = actionHandleRuleService.list(new LambdaQueryWrapper<ActionHandleRule>()
                .eq(ActionHandleRule::getStatus, ScheduleStatus.START));
        if (CollectionUtils.isEmpty(rules)) {
            return;
        }
        String pkStr = String.valueOf(entityPrimaryKey);
        for (var rule : rules) {
            try {
                var groups = readGroups(rule);
                if (CollectionUtils.isEmpty(groups)) {
                    continue;
                }
                Map<String, Map<String, Object>> instanceCache = new HashMap<>();
                for (int i = 0; i < groups.size(); i++) {
                    var group = groups.get(i);
                    if (!judgeGroupWatches(group, ontologyUniqueIdentifier, pkStr, changedPropertyKeys)) {
                        continue;
                    }
                    String cooldownKey = rule.getId() + ":" + i + ":" + ontologyUniqueIdentifier + ":" + pkStr;
                    if (inCooldown(cooldownKey)) {
                        continue;
                    }
                    if (evalAndExecuteGroup(rule.getId(), group, instanceCache)) {
                        markCooldown(cooldownKey);
                    }
                }
            } catch (Exception e) {
                log.error("规则调度评估/执行失败, ruleId={}, ontology={}, pk={}",
                        rule.getId(), ontologyUniqueIdentifier, entityPrimaryKey, e);
            }
        }
    }

    /**
     * 变更列 -> 该对象在此表上启用的属性，收集属性唯一标识与 apiName 双键，
     * 供操作数按 propertyId / propertyName 命中判断。
     */
    private Set<String> changedPropertyKeys(String ontologyUid, String table, Set<String> changedColumns) {
        List<OntologyProperty> props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUid)
                .eq(OntologyProperty::getDatasourceId, table)
                .in(OntologyProperty::getDatasourceColumnName, changedColumns)
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()));
        Set<String> keys = new HashSet<>();
        for (var p : props) {
            if (StringUtils.isNotBlank(p.getUniqueIdentifier())) {
                keys.add(p.getUniqueIdentifier());
            }
            if (StringUtils.isNotBlank(p.getApiName())) {
                keys.add(p.getApiName());
            }
        }
        return keys;
    }

    @Override
    public void evaluateRuleNow(Long ruleId) {
        var rule = actionHandleRuleService.getById(ruleId);
        if (rule == null) {
            return;
        }
        try {
            var groups = readGroups(rule);
            if (CollectionUtils.isEmpty(groups)) {
                return;
            }
            Map<String, Map<String, Object>> instanceCache = new HashMap<>();
            for (var group : groups) {
                evalAndExecuteGroup(rule.getId(), group, instanceCache);
            }
        } catch (Exception e) {
            log.error("规则调度启用时存量评估失败, ruleId={}", ruleId, e);
        }
    }

    private List<RuleJudgeGroupParam> readGroups(ActionHandleRule rule) throws Exception {
        if (StringUtils.isBlank(rule.getJudgeJson())) {
            return List.of();
        }
        return jsonMapper.readValue(rule.getJudgeJson(), new TypeReference<List<RuleJudgeGroupParam>>() {
        });
    }

    /** 求值一组条件（含整体取反），成立则执行该组行为；返回是否执行 */
    private boolean evalAndExecuteGroup(Long ruleId, RuleJudgeGroupParam group,
                                        Map<String, Map<String, Object>> instanceCache) {
        boolean result = evalJudgeItems(group.getJudgeParams(), instanceCache);
        if (Boolean.TRUE.equals(group.getCondition())) {
            result = !result;
        }
        if (!result) {
            return false;
        }
        executeJudgeAction(ruleId, group.getActionParam());
        return true;
    }

    /** 组的任一 dynamic 操作数引用了本次变更的对象+实例、且命中变更字段才评估（定向触发） */
    private boolean judgeGroupWatches(RuleJudgeGroupParam group, String ontologyUid, String pkStr,
                                      Set<String> changedPropertyKeys) {
        if (group == null || CollectionUtils.isEmpty(group.getJudgeParams())) {
            return false;
        }
        for (var item : group.getJudgeParams()) {
            if (operateWatches(item.getLeftOperate(), ontologyUid, pkStr, changedPropertyKeys)
                    || operateWatches(item.getRightOperate(), ontologyUid, pkStr, changedPropertyKeys)) {
                return true;
            }
        }
        return false;
    }

    private boolean operateWatches(RuleOperateParam operate, String ontologyUid, String pkStr,
                                   Set<String> changedPropertyKeys) {
        if (operate == null || operate.getType() != RuleOperateType.DYNAMIC
                || !StringUtils.equals(operate.getOntologyId(), ontologyUid)
                || operate.getEntityId() == null || !pkStr.equals(String.valueOf(operate.getEntityId()))) {
            return false;
        }
        // 字段门槛：操作数引用的属性必须在本次变更列映射出的属性里；未指定属性的按对象级监听放行
        if (StringUtils.isNotBlank(operate.getPropertyId())) {
            return changedPropertyKeys.contains(operate.getPropertyId());
        }
        if (StringUtils.isNotBlank(operate.getPropertyName())) {
            return changedPropertyKeys.contains(operate.getPropertyName());
        }
        return true;
    }

    /**
     * 组合条件求值：先把每条条件算成布尔值，连同括号/连接符排成 token 序列，
     * 再递归下降解析（AND 优先于 OR，括号可改变优先级）。括号不配对等畸形表达式抛异常，由上层记日志。
     */
    private boolean evalJudgeItems(List<RuleJudgeItemParam> items, Map<String, Map<String, Object>> instanceCache) {
        if (CollectionUtils.isEmpty(items)) {
            return false;
        }
        List<Object> tokens = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            if (Boolean.TRUE.equals(item.getIsLeft())) {
                tokens.add('(');
            }
            tokens.add(evalJudgeItem(item, instanceCache));
            if (Boolean.TRUE.equals(item.getIsRight())) {
                tokens.add(')');
            }
            if (i < items.size() - 1) {
                tokens.add(ActionRuleConnectType.OR.equals(item.getLinkFlag()) ? "OR" : "AND");
            }
        }
        int[] pos = {0};
        boolean result = parseJudgeOr(tokens, pos);
        if (pos[0] != tokens.size()) {
            throw new IllegalStateException("judgeParams 表达式不完整（括号或连接符畸形）");
        }
        return result;
    }

    private boolean parseJudgeOr(List<Object> tokens, int[] pos) {
        boolean value = parseJudgeAnd(tokens, pos);
        while (pos[0] < tokens.size() && "OR".equals(tokens.get(pos[0]))) {
            pos[0]++;
            boolean right = parseJudgeAnd(tokens, pos);
            value = value || right;
        }
        return value;
    }

    private boolean parseJudgeAnd(List<Object> tokens, int[] pos) {
        boolean value = parseJudgeFactor(tokens, pos);
        while (pos[0] < tokens.size() && "AND".equals(tokens.get(pos[0]))) {
            pos[0]++;
            boolean right = parseJudgeFactor(tokens, pos);
            value = value && right;
        }
        return value;
    }

    private boolean parseJudgeFactor(List<Object> tokens, int[] pos) {
        if (pos[0] >= tokens.size()) {
            throw new IllegalStateException("judgeParams 表达式意外结束");
        }
        Object token = tokens.get(pos[0]);
        if (Boolean.TRUE.equals(token) || Boolean.FALSE.equals(token)) {
            pos[0]++;
            return (Boolean) token;
        }
        if (Character.valueOf('(').equals(token)) {
            pos[0]++;
            boolean value = parseJudgeOr(tokens, pos);
            if (pos[0] >= tokens.size() || !Character.valueOf(')').equals(tokens.get(pos[0]))) {
                throw new IllegalStateException("judgeParams 缺少右括号");
            }
            pos[0]++;
            return value;
        }
        throw new IllegalStateException("judgeParams 表达式位置 " + pos[0] + " 出现非法 token: " + token);
    }

    private boolean evalJudgeItem(RuleJudgeItemParam item, Map<String, Map<String, Object>> instanceCache) {
        Object left = resolveJudgeOperate(item.getLeftOperate(), instanceCache);
        Object right = resolveJudgeOperate(item.getRightOperate(), instanceCache);
        return evaluateCondition(item.getCondition(), left, right == null ? null : String.valueOf(right));
    }

    /** fixed 用固定值；dynamic 实时取实例属性当前值，propertyId 优先、propertyName 兜底，同实例一次评估内只取一遍 */
    private Object resolveJudgeOperate(RuleOperateParam operate, Map<String, Map<String, Object>> instanceCache) {
        if (operate == null) {
            return null;
        }
        if (operate.getType() == RuleOperateType.FIXED) {
            return operate.getFixValue();
        }
        if (operate.getType() != RuleOperateType.DYNAMIC
                || StringUtils.isBlank(operate.getOntologyId()) || StringUtils.isBlank(operate.getEntityId())) {
            return null;
        }
        String instanceKey = operate.getOntologyId() + ":" + operate.getEntityId();
        var values = instanceCache.computeIfAbsent(instanceKey,
                k -> loadInstanceValues(operate.getOntologyId(), operate.getEntityId()));
        Object value = null;
        if (StringUtils.isNotBlank(operate.getPropertyId())) {
            value = values.get(operate.getPropertyId());
        }
        if (value == null && StringUtils.isNotBlank(operate.getPropertyName())) {
            value = values.get(operate.getPropertyName());
        }
        return value;
    }

    /** 实例属性值：按属性唯一标识与 apiName 双键索引（主存储取首个值） */
    private Map<String, Object> loadInstanceValues(String ontologyUid, Object pk) {
        Map<String, Object> map = new HashMap<>();
        List<EntityPropertyDetailVO> details = entityService.getEntityDetail(ontologyUid, pk);
        if (details == null) {
            return map;
        }
        for (var d : details) {
            var values = d.getPropertyValues();
            Object value = CollectionUtils.isEmpty(values) ? null : values.get(0);
            if (d.getPropertyUniqIdentifier() != null) {
                map.put(d.getPropertyUniqIdentifier(), value);
            }
            if (d.getPropertyApiName() != null) {
                map.putIfAbsent(d.getPropertyApiName(), value);
            }
        }
        return map;
    }

    private boolean inCooldown(String key) {
        Long last = judgeCooldownMap.get(key);
        return last != null && System.currentTimeMillis() - last < JUDGE_COOLDOWN_MS;
    }

    private void markCooldown(String key) {
        long now = System.currentTimeMillis();
        if (judgeCooldownMap.size() > 10_000) {
            judgeCooldownMap.entrySet().removeIf(e -> now - e.getValue() >= JUDGE_COOLDOWN_MS);
        }
        judgeCooldownMap.put(key, now);
    }

    private boolean evaluateCondition(ActionHandleRuleAddConditionEnum condition, Object actual, String expected) {
        if (condition == null || actual == null) {
            return false;
        }
        Integer cmp = compare(actual, expected);
        if (cmp == null) {
            return false;
        }
        switch (condition) {
            case LT:
                return cmp < 0;
            case LE:
                return cmp <= 0;
            case EQ:
                return cmp == 0;
            case NE:
                return cmp != 0;
            case GT:
                return cmp > 0;
            case GE:
                return cmp >= 0;
            default:
                // CH（变化）不支持：规则调度只做当前值比较
                return false;
        }
    }

    /** 数值优先比较；非数值退化为字符串比较（EQ/NE 有意义，大小按字典序）。无法比较返回 null */
    private Integer compare(Object actual, String expected) {
        if (expected == null) {
            return null;
        }
        var actualStr = String.valueOf(actual);
        var a = toBigDecimal(actualStr);
        var b = toBigDecimal(expected);
        if (a != null && b != null) {
            return a.compareTo(b);
        }
        return actualStr.compareTo(expected);
    }

    private BigDecimal toBigDecimal(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 执行定稿规则的行为：目标实例由 actionParam 显式指定；entity_json 与周期调度同形式 */
    private void executeJudgeAction(Long ruleId, RuleActionParam actionParam) {
        if (actionParam == null || StringUtils.isBlank(actionParam.getActionApi())) {
            log.warn("规则调度缺少 actionParam.action_api, ruleId={}", ruleId);
            return;
        }
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionParam.getActionApi()));
        if (action == null) {
            log.warn("规则调度绑定的行为不存在, ruleId={}, actionApi={}", ruleId, actionParam.getActionApi());
            return;
        }
        var template = readTemplate(actionParam.getEntityJson(), action);
        template.setOntologySpaceId(action.getOntologySpaceId());
        template.setActionApi(action.getApi());
        if (StringUtils.isNotBlank(actionParam.getEntityIdentifier()) && template.getEntityPrimaryKey() == null) {
            template.setEntityPrimaryKey(actionParam.getEntityIdentifier());
        }
        executeTemplate(ruleId, action.getApi(), template);
    }

    /** testAction 执行 + 结果落 action_handle_log；日志独立提交，不随后续异常回滚 */
    private void executeTemplate(Long ruleId, String actionApi, ActionTestParam template) {
        var handleLog = ActionHandleLog.builder()
                .actionHandleId(ruleId)
                .actionHandleType(ActionSchedulingTypeEnum.RULE)
                .requestParam(writeJson(template))
                .triggerTime(new Date())
                .taskStatus(TaskStatusEnum.PENDING)
                .build();
        actionHandleLogMapper.insert(handleLog);
        try {
            var result = ontologyActionService.testAction(template);
            handleLog.setMsg(result.getMessage())
                    .setExecuteResult(writeJson(result))
                    .setCompleteTime(new Date())
                    .setTaskStatus(TaskStatusEnum.COMPLETED);
        } catch (Exception e) {
            log.error("规则调度执行行为失败, ruleId={}, actionApi={}", ruleId, actionApi, e);
            handleLog.setMsg(e.getMessage())
                    .setCompleteTime(new Date())
                    .setTaskStatus(TaskStatusEnum.FAILED);
        }
        actionHandleLogMapper.updateById(handleLog);
    }

    @SneakyThrows
    private ActionTestParam readTemplate(String entityJson, OntologyAction action) {
        if (StringUtils.isNotBlank(entityJson)) {
            return jsonMapper.readValue(entityJson, ActionTestParam.class);
        }
        // 无外部入参的行为：构造最小执行参数
        return ActionTestParam.builder()
                .ontologySpaceId(action.getOntologySpaceId())
                .actionApi(action.getApi())
                .build();
    }

    @SneakyThrows
    private String writeJson(Object value) {
        return jsonMapper.writeValueAsString(value);
    }
}