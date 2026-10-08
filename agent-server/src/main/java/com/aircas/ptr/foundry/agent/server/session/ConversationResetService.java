package com.aircas.ptr.foundry.agent.server.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/**
 * 会话上下文重置服务：一次性清空某会话的短期记忆（{@link ChatMemory} 滑动窗口）与本体构建状态
 * （{@link SessionStateStore}），供"新对话"与"聊天内清空上下文"两种入口复用。
 *
 * <p>两处调用方：① {@code AgentChatController} 的显式重置接口（前端"新对话/清空"按钮）；
 * ② 本地工具 {@code ConversationTools#clearConversationContext}（用户在聊天里表达清空意图时由模型调用）。</p>
 *
 * <p>注意：{@code ChatMemory} 用 {@code InMemoryChatMemoryRepository}，clear 只移除当前会话键，
 * 不影响其他会话；多实例部署时各实例内存独立，重置仅对本实例生效（与 SessionStateStore 同）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationResetService {

    private final ChatMemory chatMemory;
    private final SessionStateStore sessionStateStore;

    /**
     * 清空指定会话的全部上下文（多轮记忆 + 本体构建状态）。
     *
     * @param sessionId 会话 id；为空时不操作（无法定位会话）
     */
    public void reset(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[SessionReset] sessionId 为空，跳过清空");
            return;
        }
        chatMemory.clear(sessionId);
        sessionStateStore.clear(sessionId);
        log.info("[SessionReset] 已清空会话上下文（记忆 + 构建状态）sessionId={}", sessionId);
    }
}
