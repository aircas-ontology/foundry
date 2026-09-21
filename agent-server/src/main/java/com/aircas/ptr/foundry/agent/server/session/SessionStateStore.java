package com.aircas.ptr.foundry.agent.server.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级本体构建状态存储。
 *
 * <p>解决的问题：本体构建是多轮流程（功能二对象定义 → 功能三属性选择 → 功能四关系选择 → 最终落库），
 * 各阶段以带 {@code stage} 字段的 JSON 块承载状态。原先这些状态只存在于 {@code ChatMemory} 的滑动窗口
 * （maxMessages=20）中，长对话会把早先的 {@code properties_selected} / {@code relations_selected} 挤出窗口，
 * 导致落库阶段回读不到用户的选择。</p>
 *
 * <p>本存储按 sessionId 持久化各关键 stage 的原始 JSON（进程内存，跨轮不丢），在每次请求时由
 * {@code AgentChatController} 渲染进 system 消息，使模型始终能看到完整累计状态，不再依赖记忆窗口。
 * 落库成功（stage=persisted）后自动清除该会话状态，避免污染下一次构建。</p>
 *
 * <p>注意：这是进程内存储，多实例部署时状态不共享；如需水平扩展应替换为 Redis 等外部存储，
 * 接口（record/renderState/clear）保持不变即可平滑迁移。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionStateStore {

    /** 进程内最多保留的会话数，超出后整体清空，防止无界增长导致内存泄漏。 */
    private static final int MAX_SESSIONS = 1000;

    /** 落库成功标记：捕获到该 stage 时清除整个会话的构建状态。 */
    private static final String STAGE_PERSISTED = "persisted";

    /**
     * 需要跨轮持久化并按此固定顺序渲染的 stage。
     * 除构建阶段状态外，还包含用户在对话中选定的空间（space_selected）与数据源（datasource_selected），
     * 以免长对话下滑动窗口淘汰后丢失选择；persisted / persist_failed 为终态信号，不在此列。
     */
    private static final List<String> PERSISTED_STAGES = List.of(
            "space_selected",
            "datasource_selected",
            "object_defined",
            "properties_proposed",
            "properties_selected",
            "relations_proposed",
            "relations_selected");

    /** sessionId -> (stage -> 原始 JSON 字符串)。 */
    private final Map<String, Map<String, String>> states = new ConcurrentHashMap<>();

    /** 复用 Spring Boot 自动装配的统一 ObjectMapper Bean（而非各处 new），保证序列化配置一致。 */
    private final ObjectMapper objectMapper;

    /**
     * 记录某个 stage 的最新 JSON。
     *
     * @param sessionId 会话 id
     * @param stage     阶段标记（模型输出 JSON 里的 stage 字段值）
     * @param rawJson   该 stage 对应的原始 JSON 字符串
     */
    public void record(String sessionId, String stage, String rawJson) {
        if (sessionId == null || stage == null || rawJson == null) {
            return;
        }
        // 落库成功：本次构建结束，清空该会话状态，下一次构建从干净状态开始
        if (STAGE_PERSISTED.equals(stage)) {
            states.remove(sessionId);
            log.debug("[SessionState] 落库成功，已清除会话构建状态 sessionId={}", sessionId);
            return;
        }
        // 只持久化关键 stage，忽略 relations_proposed 之外的临时/终态信号
        if (!PERSISTED_STAGES.contains(stage)) {
            return;
        }
        // 容量保护：达到上限且是新会话时整体清空（简单策略，避免引入 LRU/TTL 依赖）
        if (states.size() >= MAX_SESSIONS && !states.containsKey(sessionId)) {
            log.warn("[SessionState] 会话数达到上限 {}，执行整体清空以释放内存", MAX_SESSIONS);
            states.clear();
        }
        states.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(stage, rawJson);
    }

    /**
     * 渲染指定会话的累计构建状态为一段紧凑 JSON，供注入 system 消息。
     *
     * @param sessionId 会话 id
     * @return 形如 {@code {"object_defined":{...},"properties_selected":{...}}} 的 JSON 字符串；
     *         无任何已记录状态时返回 {@code null}
     */
    public String renderState(String sessionId) {
        Map<String, String> stageMap = states.get(sessionId);
        if (stageMap == null || stageMap.isEmpty()) {
            return null;
        }
        ObjectNode root = objectMapper.createObjectNode();
        for (String stage : PERSISTED_STAGES) {
            String raw = stageMap.get(stage);
            if (raw == null) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(raw);
                root.set(stage, node);
            } catch (Exception e) {
                // 解析失败则原样作为字符串挂载，保证状态不丢
                log.warn("[SessionState] stage JSON 解析失败，按原文注入 sessionId={}, stage={}", sessionId, stage);
                root.put(stage, raw);
            }
        }
        return root.isEmpty() ? null : root.toString();
    }

    /**
     * 清除指定会话的全部构建状态（如用户显式取消/重新开始）。
     */
    public void clear(String sessionId) {
        if (sessionId != null) {
            states.remove(sessionId);
        }
    }
}
