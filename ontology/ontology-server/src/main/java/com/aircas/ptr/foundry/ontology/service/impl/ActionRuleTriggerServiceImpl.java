package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologyInstanceConverter;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.ActionHandleRuleAddConditionEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.RuleMonitorTypeEnum;
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
import com.aircas.ptr.foundry.ontology.utils.ExecutorMachineInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
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
 * 电平触发：规则启用时先对修改/属性条件绑定的实例评估一遍存量数据；此后 CDC 行级事件
 * （主表与扩展存储分组表的新增/修改/删除）-> 按事件类型+对象+属性+输入值匹配新增/删除条件、
 * 按变更表+列映射的对象属性匹配修改条件 -> 捞开启中的规则 ->
 * 定向匹配（组内任一条件命中本次事件才评估）->
 * 新增/删除条件取事件行属性值比较，修改等属性条件实时取实例属性当前值 ->
 * 括号+AND/OR 组合求值（可整体取反）-> 满足则按 actionParam
 * 显式指定的目标执行行为（entity_json 与周期调度同形式）-> 结果落 action_handle_log。
 * <p>
 * 新增/删除条件是事件驱动语义：只按事件行取值判断，规则启用不做存量评估。
 * 多个不相关条件之间 AND 是跨事件累计语义：一次 CDC 消息只对应一个分组的一种事件，
 * 「A组新增 且 B组新增」需要同一实例先后两条事件在累计窗口内都发生才触发，
 * 触发后清空累计，窗口内没有新的匹配事件则自动过期。
 * <p>
 * 不加事务：testAction 跨数据湖/图库写库，日志独立提交，不能被随后的异常回滚掉。
 * 单条规则评估/执行失败只记日志，不影响其余规则与实例同步。
 */
@Slf4j
@Service
public class ActionRuleTriggerServiceImpl implements ActionRuleTriggerService {

    /** 主存储分组名：主表行即实例本身 */
    private static final String MAIN_STORAGE_GROUP = "main";

    /** 同一规则+组+触发实例的执行冷却，防自写循环：行为写库 -> CDC 再触发 -> 条件仍成立 -> 无限执行 */
    private static final long JUDGE_COOLDOWN_MS = 10_000L;
    private final Map<String, Long> judgeCooldownMap = new ConcurrentHashMap<>();

    /** 监测条件累计窗口：同一实例的多分组事件在这段时间内凑齐才算命中 AND 组合，超时未凑齐自动作废 */
    private static final long MONITOR_ACCUMULATE_TTL_MS = 5 * 60_000L;

    /** rule+组+对象+实例 -> 已命中的监测条件下标；只存发生过、还没被消费掉的匹配 */
    private static final class MonitorAccumulation {
        private final Set<Integer> matchedIndexes = new HashSet<>();
        private volatile long lastTouched;
    }

    private final Map<String, MonitorAccumulation> monitorAccumulationMap = new ConcurrentHashMap<>();

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

    /** 一次 CDC 事件的求值上下文：新增/删除条件按事件行取值判断，修改/属性条件按它做定向过滤 */
    private record EventContext(RuleMonitorTypeEnum eventType, String ontologyUid, String pkStr,
                                String storageGroup, boolean mainTable, Set<String> changedPropertyKeys,
                                Map<String, Object> rowValues) {
    }

    @Override
    public void onDataChange(String ontologyUniqueIdentifier, Object entityPrimaryKey, String table,
                             String storageGroup, RuleMonitorTypeEnum eventType, Set<String> changedColumns,
                             Map<String, Object> rowValues) {
        if (StringUtils.isBlank(ontologyUniqueIdentifier) || entityPrimaryKey == null
                || StringUtils.isBlank(table) || eventType == null) {
            return;
        }
        var columns = changedColumns == null ? Set.<String>of() : changedColumns;
        // 更新事件前后无实际差异不算变更，避免空更新触发监测条件
        if (eventType == RuleMonitorTypeEnum.UPDATE && columns.isEmpty()) {
            return;
        }
        boolean mainTable = StringUtils.equals(MAIN_STORAGE_GROUP, storageGroup);
        // 属性条件仍按「变更表+变更列映射到对象属性」定向：扩展分组表的事件只供监测条件使用
        Set<String> changedPropertyKeys = mainTable
                ? changedPropertyKeys(ontologyUniqueIdentifier, table, columns)
                : Set.of();
        var ctx = new EventContext(eventType, ontologyUniqueIdentifier, String.valueOf(entityPrimaryKey),
                StringUtils.defaultIfBlank(storageGroup, MAIN_STORAGE_GROUP), mainTable, changedPropertyKeys, rowValues);
        // 触发时刻取收到数据变更的时间，后续规则捞取/条件求值的耗时不算进执行时间
        var triggerTime = new Date();
        // 条件跨对象，监控对象在条件操作数里，无法按单对象索引捞，取全部开启中的规则内存定向匹配
        var rules = actionHandleRuleService.list(new LambdaQueryWrapper<ActionHandleRule>()
                .eq(ActionHandleRule::getStatus, ScheduleStatus.START));
        if (CollectionUtils.isEmpty(rules)) {
            return;
        }
        for (var rule : rules) {
            try {
                var groups = readGroups(rule);
                if (CollectionUtils.isEmpty(groups)) {
                    continue;
                }
                Map<String, Map<String, Object>> instanceCache = new HashMap<>();
                Map<String, OntologyProperty> propCache = new HashMap<>();
                for (int i = 0; i < groups.size(); i++) {
                    var group = groups.get(i);
                    if (group == null || CollectionUtils.isEmpty(group.getJudgeParams())) {
                        continue;
                    }
                    var items = group.getJudgeParams();
                    var groupMonitorType = group.getMonitorType();
                    var matchedNow = monitorMatches(items, ctx, groupMonitorType, propCache);
                    if (matchedNow.isEmpty() && !propertyWatches(items, ctx, groupMonitorType)) {
                        continue;
                    }
                    boolean eventGroup = hasEventCondition(group);
                    String baseKey = rule.getId() + ":" + i + ":" + ontologyUniqueIdentifier + ":" + ctx.pkStr();
                    // 冷却键：新增/删除事件组带事件类型，同一实例的新增和删除互不压制；修改等属性条件组沿用原键
                    String cooldownKey = baseKey + (eventGroup ? ":" + eventType : "");
                    if (inCooldown(cooldownKey)) {
                        continue;
                    }
                    // 新增/删除条件把本次命中的下标并入该实例的累计集合，AND 组合要多个分组的事件先后凑齐
                    Set<Integer> accumulated = eventGroup
                            ? accumulateMonitorMatches(baseKey, matchedNow)
                            : null;
                    if (evalAndExecuteGroup(rule.getOntologySpaceId(), rule.getId(), group, instanceCache,
                            triggerTime, accumulated, eventType, cooldownKey)) {
                        if (eventGroup) {
                            monitorAccumulationMap.remove(baseKey);
                        }
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
        if (CollectionUtils.isEmpty(changedColumns)) {
            return Set.of();
        }
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
        // 只评估开启中的规则：启动接口的存量评估异步执行，落地前规则可能已被停用/删除
        if (rule == null || !ScheduleStatus.START.equals(rule.getStatus())) {
            return;
        }
        var triggerTime = new Date();
        try {
            var groups = readGroups(rule);
            if (CollectionUtils.isEmpty(groups)) {
                return;
            }
            Map<String, Map<String, Object>> instanceCache = new HashMap<>();
            for (int i = 0; i < groups.size(); i++) {
                var group = groups.get(i);
                // 新增/删除条件是事件触发，没有事件上下文时不评估，避免取反条件在启用瞬间误执行
                if (group == null || hasEventCondition(group)) {
                    continue;
                }
                evalAndExecuteGroup(rule.getOntologySpaceId(), rule.getId(), group, instanceCache,
                        triggerTime, null, null, "eval:" + ruleId + ":" + i + ":stock");
            }
        } catch (Exception e) {
            log.error("规则调度启用时存量评估失败, ruleId={}", ruleId, e);
        }
    }

    private List<RuleJudgeGroupParam> readGroups(ActionHandleRule rule) {
        if (StringUtils.isBlank(rule.getJudgeJson())) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(rule.getJudgeJson(), new TypeReference<List<RuleJudgeGroupParam>>() {
            });
        } catch (Exception e) {
            // 配了规则但结构解析不出来：报错点名规则，避免调度静默不触发
            throw new BusinessException("规则调度(id=" + rule.getId() + ", name=" + rule.getName()
                    + ") 的条件配置解析失败，请在调度管理中重新编辑保存该规则", HttpStatus.BAD_REQUEST);
        }
    }

    /** 规则组是否含新增/删除事件条件（事件驱动、不做启用时存量评估） */
    private boolean hasEventCondition(RuleJudgeGroupParam group) {
        if (group == null) {
            return false;
        }
        if (isEventDriven(group.getMonitorType())) {
            return true;
        }
        return CollectionUtils.isNotEmpty(group.getJudgeParams())
                && group.getJudgeParams().stream().anyMatch(item -> isEventDriven(item.getMonitorType()));
    }

    /** 条件项生效的事件类型：条件项自身优先，否则继承规则组的 monitorType；都为空即属性比较条件（修改语义） */
    private RuleMonitorTypeEnum effectiveMonitorType(RuleJudgeItemParam item, RuleMonitorTypeEnum groupMonitorType) {
        return item.getMonitorType() != null ? item.getMonitorType() : groupMonitorType;
    }

    /** 新增/删除是事件驱动类型：按事件行取值判断；修改/空走属性比较条件 */
    private boolean isEventDriven(RuleMonitorTypeEnum type) {
        return type == RuleMonitorTypeEnum.INSERT || type == RuleMonitorTypeEnum.DELETE;
    }

    /** 求值一组条件（含整体取反），成立则先原子认领冷却窗口再执行该组行为；返回是否执行 */
    private boolean evalAndExecuteGroup(Integer spaceId, Long ruleId, RuleJudgeGroupParam group,
                                         Map<String, Map<String, Object>> instanceCache, Date triggerTime,
                                         Set<Integer> accumulatedMonitorIndexes, RuleMonitorTypeEnum eventType,
                                         String cooldownKey) {
        boolean result = evalJudgeItems(group.getJudgeParams(), instanceCache, accumulatedMonitorIndexes,
                group.getMonitorType(), eventType);
        if (Boolean.TRUE.equals(group.getCondition())) {
            result = !result;
        }
        if (!result) {
            return false;
        }
        // 判定与占位一步完成：并发 CDC 事件下同一规则不会被执行两遍
        if (StringUtils.isNotBlank(cooldownKey) && !tryMarkCooldown(cooldownKey)) {
            return false;
        }
        executeJudgeAction(spaceId, ruleId, group.getActionParam(), triggerTime);
        return true;
    }

    /** 本次事件命中的新增/删除条件下标（一次 CDC 事件只对应一种类型和一个分组，可能同时命中多条同义条件） */
    private List<Integer> monitorMatches(List<RuleJudgeItemParam> items, EventContext ctx,
                                         RuleMonitorTypeEnum groupMonitorType,
                                         Map<String, OntologyProperty> propCache) {
        List<Integer> matched = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            var monitorType = effectiveMonitorType(item, groupMonitorType);
            if (isEventDriven(monitorType) && monitorWatches(item, monitorType, ctx, propCache)) {
                matched.add(i);
            }
        }
        return matched;
    }

    /**
     * 修改等属性条件是否与本次事件相关：仅主表的非删除事件参与，
     * 且操作数引用了本次变更实例并命中变更字段；扩展分组表事件与删除事件不触发属性条件规则。
     */
    private boolean propertyWatches(List<RuleJudgeItemParam> items, EventContext ctx,
                                    RuleMonitorTypeEnum groupMonitorType) {
        if (!ctx.mainTable() || ctx.eventType() == RuleMonitorTypeEnum.DELETE
                || ctx.changedPropertyKeys().isEmpty()) {
            return false;
        }
        for (var item : items) {
            if (!isEventDriven(effectiveMonitorType(item, groupMonitorType))
                    && (operateWatches(item.getLeftOperate(), ctx)
                    || operateWatches(item.getRightOperate(), ctx))) {
                return true;
            }
        }
        return false;
    }

    /** 把本次命中的监测条件下标并入该实例的累计集合，返回窗口内已命中的全部下标 */
    private Set<Integer> accumulateMonitorMatches(String baseKey, List<Integer> matchedNow) {
        long now = System.currentTimeMillis();
        evictExpiredMonitorAccumulation(now);
        var accumulation = monitorAccumulationMap.computeIfAbsent(baseKey, k -> new MonitorAccumulation());
        synchronized (accumulation) {
            if (now - accumulation.lastTouched >= MONITOR_ACCUMULATE_TTL_MS) {
                accumulation.matchedIndexes.clear();
            }
            accumulation.matchedIndexes.addAll(matchedNow);
            accumulation.lastTouched = now;
            return new HashSet<>(accumulation.matchedIndexes);
        }
    }

    private void evictExpiredMonitorAccumulation(long now) {
        if (monitorAccumulationMap.size() > 10_000) {
            monitorAccumulationMap.entrySet()
                    .removeIf(e -> now - e.getValue().lastTouched >= MONITOR_ACCUMULATE_TTL_MS);
        }
    }

    /**
     * 新增/删除条件是否命中本次事件：事件类型一致 + 对象一致 + 分组一致（分组由所选属性推导，给了实例主键再限定实例），
     * 再取事件行该属性列的值与输入值比较。不带属性的旧写法只按对象+存储分组匹配（兼容历史数据）。
     */
    private boolean monitorWatches(RuleJudgeItemParam item, RuleMonitorTypeEnum monitorType, EventContext ctx,
                                   Map<String, OntologyProperty> propCache) {
        if (ctx == null || monitorType != ctx.eventType()) {
            return false;
        }
        var operate = item.getLeftOperate();
        if (operate == null || operate.getType() != RuleOperateType.DYNAMIC
                || !StringUtils.equals(operate.getOntologyId(), ctx.ontologyUid())) {
            return false;
        }
        if (StringUtils.isNotBlank(operate.getEntityId())
                && !ctx.pkStr().equals(String.valueOf(operate.getEntityId()))) {
            return false;
        }
        boolean hasProperty = StringUtils.isNotBlank(operate.getPropertyId())
                || StringUtils.isNotBlank(operate.getPropertyName());
        if (!hasProperty) {
            // 兼容旧写法：只按 对象+存储分组 匹配事件，不做取值比较
            return StringUtils.equals(operate.getStorageGroup(), ctx.storageGroup());
        }
        var property = resolveProperty(operate, propCache);
        if (property == null) {
            return false;
        }
        String group = StringUtils.defaultIfBlank(property.getStorageGroup(), MAIN_STORAGE_GROUP);
        if (!StringUtils.equals(group, ctx.storageGroup())) {
            return false;
        }
        String column = property.getDatasourceColumnName();
        if (StringUtils.isBlank(column)) {
            return false;
        }
        Object actual = OntologyInstanceConverter.getColumnValue(ctx.rowValues(), column);
        var right = item.getRightOperate();
        Object expected = right != null && right.getType() == RuleOperateType.FIXED ? right.getFixValue() : null;
        return evaluateCondition(item.getCondition(), actual, expected == null ? null : String.valueOf(expected));
    }

    /** 按对象+属性唯一标识/apiName 定位启用的属性，取存储分组与数据源列；同一次事件内缓存 */
    private OntologyProperty resolveProperty(RuleOperateParam operate, Map<String, OntologyProperty> propCache) {
        String key = operate.getOntologyId() + "|" + StringUtils.defaultString(operate.getPropertyId())
                + "|" + StringUtils.defaultString(operate.getPropertyName());
        if (propCache.containsKey(key)) {
            return propCache.get(key);
        }
        var wrapper = new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, operate.getOntologyId())
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue());
        if (StringUtils.isNotBlank(operate.getPropertyId())) {
            wrapper.eq(OntologyProperty::getUniqueIdentifier, operate.getPropertyId());
        } else {
            wrapper.eq(OntologyProperty::getApiName, operate.getPropertyName());
        }
        OntologyProperty property = propertyMapper.selectList(wrapper).stream().findFirst().orElse(null);
        propCache.put(key, property);
        return property;
    }

    /** 属性操作数是否引用了本次变更对象+实例，且在变更字段上（未指定属性的按对象级监听放行） */
    private boolean operateWatches(RuleOperateParam operate, EventContext ctx) {
        if (operate == null || operate.getType() != RuleOperateType.DYNAMIC
                || !StringUtils.equals(operate.getOntologyId(), ctx.ontologyUid())
                || operate.getEntityId() == null || !ctx.pkStr().equals(String.valueOf(operate.getEntityId()))) {
            return false;
        }
        if (StringUtils.isNotBlank(operate.getPropertyId())) {
            return ctx.changedPropertyKeys().contains(operate.getPropertyId());
        }
        if (StringUtils.isNotBlank(operate.getPropertyName())) {
            return ctx.changedPropertyKeys().contains(operate.getPropertyName());
        }
        return true;
    }

    /**
     * 组合条件求值：先把每条条件算成布尔值，连同括号/连接符排成 token 序列，
     * 再递归下降解析（AND 优先于 OR，括号可改变优先级）。括号不配对等畸形表达式抛异常，由上层记日志。
     */
    private boolean evalJudgeItems(List<RuleJudgeItemParam> items, Map<String, Map<String, Object>> instanceCache,
                                   Set<Integer> accumulatedMonitorIndexes, RuleMonitorTypeEnum groupMonitorType,
                                   RuleMonitorTypeEnum eventType) {
        if (CollectionUtils.isEmpty(items)) {
            return false;
        }
        List<Object> tokens = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            int leftBrackets = item.getIsLeft() == null ? 0 : item.getIsLeft();
            for (int k = 0; k < leftBrackets; k++) {
                tokens.add('(');
            }
            tokens.add(evalJudgeItem(i, item, instanceCache, accumulatedMonitorIndexes, groupMonitorType, eventType));
            int rightBrackets = item.getIsRight() == null ? 0 : item.getIsRight();
            for (int k = 0; k < rightBrackets; k++) {
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

    private boolean evalJudgeItem(int index, RuleJudgeItemParam item, Map<String, Map<String, Object>> instanceCache,
                                  Set<Integer> accumulatedMonitorIndexes, RuleMonitorTypeEnum groupMonitorType,
                                  RuleMonitorTypeEnum eventType) {
        var monitorType = effectiveMonitorType(item, groupMonitorType);
        if (isEventDriven(monitorType)) {
            // 新增/删除条件看该实例窗口内是否已发生过对应事件（事件行取值比较已在 monitorWatches 完成）
            return accumulatedMonitorIndexes != null && accumulatedMonitorIndexes.contains(index);
        }
        // 显式「修改」条件只在修改事件上判断；eventType 为空表示启用时存量评估，照常按当前值判断
        if (monitorType == RuleMonitorTypeEnum.UPDATE
                && (eventType == RuleMonitorTypeEnum.INSERT || eventType == RuleMonitorTypeEnum.DELETE)) {
            return false;
        }
        Object left = resolveJudgeOperate(item.getLeftOperate(), instanceCache);
        Object right = resolveJudgeOperate(item.getRightOperate(), instanceCache);
        return evaluateCondition(item.getCondition(), left, right == null ? null : String.valueOf(right));
    }

    /** fixed 用固定值；dynamic 实时取实例属性当前值，propertyId 优先、propertyName 兜底，同实例同一行一次评估内只取一遍 */
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
        String instanceKey = operate.getOntologyId() + ":" + operate.getEntityId()
                + ":" + StringUtils.defaultString(operate.getDataPrimaryKey());
        var values = instanceCache.computeIfAbsent(instanceKey, k -> loadInstanceValues(operate));
        Object value = null;
        if (StringUtils.isNotBlank(operate.getPropertyId())) {
            value = values.get(operate.getPropertyId());
        }
        if (value == null && StringUtils.isNotBlank(operate.getPropertyName())) {
            value = values.get(operate.getPropertyName());
        }
        return value;
    }

    /**
     * 实例属性值：按属性唯一标识与 apiName 双键索引（主存储取首个值）。
     * 操作数给了子表行主键且属性在扩展分组时，只取该行的值；行不存在或不属于本实例会抛错，由上层记日志。
     */
    private Map<String, Object> loadInstanceValues(RuleOperateParam operate) {
        // 主键来自规则操作数/CDC 事件时是字符串，PG 整型列无法与 varchar 比较，数字串需还原为数值
        Object pk = operate.getEntityId();
        if (pk instanceof String s && s.matches("-?\\d+")) {
            pk = Long.valueOf(s);
        }
        Map<String, Object> rowKeysByGroup = null;
        if (StringUtils.isNotBlank(operate.getDataPrimaryKey())) {
            var group = propertyStorageGroup(operate);
            // 主表行就是实例本身，行主键只对扩展分组有意义
            if (StringUtils.isNotBlank(group) && !"main".equals(group)) {
                rowKeysByGroup = Map.of(group, operate.getDataPrimaryKey());
            }
        }
        Map<String, Object> map = new HashMap<>();
        List<EntityPropertyDetailVO> details = entityService.getEntityDetail(operate.getOntologyId(), pk, rowKeysByGroup);
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

    /** 操作数引用属性的存储分组：propertyId 优先、propertyName 兜底；属性查不到时返回 null 沿用原口径 */
    private String propertyStorageGroup(RuleOperateParam operate) {
        var wrapper = new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, operate.getOntologyId())
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue());
        if (StringUtils.isNotBlank(operate.getPropertyId())) {
            wrapper.eq(OntologyProperty::getUniqueIdentifier, operate.getPropertyId());
        } else {
            wrapper.eq(OntologyProperty::getApiName, operate.getPropertyName());
        }
        return propertyMapper.selectList(wrapper).stream().findFirst()
                .map(OntologyProperty::getStorageGroup).orElse(null);
    }

    private boolean inCooldown(String key) {
        Long last = judgeCooldownMap.get(key);
        return last != null && System.currentTimeMillis() - last < JUDGE_COOLDOWN_MS;
    }

    /** 原子「判 + 占」冷却窗口：冷却期内返回 false，否则写入当前时间并返回 true */
    private boolean tryMarkCooldown(String key) {
        long now = System.currentTimeMillis();
        if (judgeCooldownMap.size() > 10_000) {
            judgeCooldownMap.entrySet().removeIf(e -> now - e.getValue() >= JUDGE_COOLDOWN_MS);
        }
        Long claimed = judgeCooldownMap.compute(key, (k, last) ->
                last != null && now - last < JUDGE_COOLDOWN_MS ? last : now);
        return claimed == now;
    }

    private boolean evaluateCondition(ActionHandleRuleAddConditionEnum condition, Object actual, String expected) {
        if (condition == null || actual == null || expected == null) {
            return false;
        }
        var actualStr = String.valueOf(actual);
        switch (condition) {
            case CONTAINS:
                return actualStr.contains(expected);
            case NOT_CONTAINS:
                return !actualStr.contains(expected);
            case LT:
            case LE:
            case GT:
            case GE:
                // 大小比较只支持数字，非数字操作数直接不成立
                var a = toBigDecimal(actualStr);
                var b = toBigDecimal(expected);
                if (a == null || b == null) {
                    return false;
                }
                int c = a.compareTo(b);
                if (condition == ActionHandleRuleAddConditionEnum.LT) {
                    return c < 0;
                }
                if (condition == ActionHandleRuleAddConditionEnum.LE) {
                    return c <= 0;
                }
                if (condition == ActionHandleRuleAddConditionEnum.GT) {
                    return c > 0;
                }
                return c >= 0;
            case EQ:
                return compare(actualStr, expected) == 0;
            case NE:
                return compare(actualStr, expected) != 0;
            default:
                // CH（变化）不支持：规则调度只做当前值比较
                return false;
        }
    }

    /** 等于/不等于：两边都是数字按数值比（100 与 100.0 相等），否则按字符串精确比较 */
    private int compare(String actualStr, String expected) {
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

    /** 执行定稿规则的行为：行为按 api + 调度所属空间定位；目标实例由 actionParam 显式指定；entity_json 与周期调度同形式 */
    private void executeJudgeAction(Integer spaceId, Long ruleId, RuleActionParam actionParam, Date triggerTime) {
        if (actionParam == null || StringUtils.isBlank(actionParam.getActionApi())) {
            log.warn("规则调度缺少 actionParam.action_api, ruleId={}", ruleId);
            return;
        }
        // 行为 api 只在空间内唯一：按调度记录的空间收敛；老数据空间为空时命中多条只告警跳过，不抛断事件链
        var candidates = actionMapper.selectList(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionParam.getActionApi())
                .eq(spaceId != null, OntologyAction::getOntologySpaceId, spaceId));
        if (candidates.size() > 1) {
            log.warn("规则调度绑定的行为在多个空间同时存在且调度未记录空间, ruleId={}, actionApi={}",
                    ruleId, actionParam.getActionApi());
            return;
        }
        var action = candidates.isEmpty() ? null : candidates.get(0);
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
        executeTemplate(ruleId, action.getApi(), template, triggerTime);
    }

    /** 规则命中后执行行为并把结果落 action_handle_log；日志独立提交，不随后续异常回滚 */
    private void executeTemplate(Long ruleId, String actionApi, ActionTestParam template, Date triggerTime) {
        var handleLog = ActionHandleLog.builder()
                .actionHandleId(ruleId)
                .actionHandleType(ActionSchedulingTypeEnum.RULE)
                .requestParam(writeJson(template))
                .triggerTime(triggerTime)
                .executorIp(ExecutorMachineInfo.ip())
                .executorHost(ExecutorMachineInfo.host())
                .taskStatus(TaskStatusEnum.PENDING)
                .build();
        actionHandleLogMapper.insert(handleLog);
        try {
            handleLog.setExecuteStartTime(new Date());
            var result = ontologyActionService.testActionForScheduling(template);
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