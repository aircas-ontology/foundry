package com.aircas.ptr.foundry.agent.server.stream;

import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatStreamVO;
import org.springframework.ai.chat.model.ToolContext;
import reactor.core.publisher.Sinks;

/**
 * Agent 流式事件发射辅助。
 *
 * <p>对话发起方（Controller）把一个 {@link Sinks.Many} 通过
 * {@code ChatClient.prompt().toolContext(...)} 以 {@link #SINK_KEY} 注入；工具回调装饰器在工具被
 * 大模型调用时，从 {@link ToolContext} 取出该 sink 并推入 {@code tool_call / tool_result} 事件，
 * 从而把"思考过程"并入同一条 SSE 流。请求归属由 toolContext 携带，线程安全、无全局状态。</p>
 */
public final class AgentStreamEvents {

    /** toolContext 中事件 sink 的键。 */
    public static final String SINK_KEY = "agentStreamEventSink";

    /** toolContext 中当前会话 id 的键，供本地会话工具（如清空上下文）读取。 */
    public static final String SESSION_ID_KEY = "agentSessionId";

    /** 工具入参 / 结果摘要的最大长度，超出截断，避免单条 SSE 事件过大。 */
    private static final int MAX_SUMMARY_LENGTH = 2000;

    private AgentStreamEvents() {
    }

    /**
     * 推送"工具调用开始"事件。
     */
    public static void toolCall(ToolContext toolContext, String tool, String args) {
        emit(toolContext, AgentChatStreamVO.toolCall(tool, truncate(args)));
    }

    /**
     * 推送"工具调用结果"事件。
     */
    public static void toolResult(ToolContext toolContext, String tool, String summary) {
        emit(toolContext, AgentChatStreamVO.toolResult(tool, truncate(summary)));
    }

    @SuppressWarnings("unchecked")
    private static void emit(ToolContext toolContext, AgentChatStreamVO event) {
        if (toolContext == null || toolContext.getContext() == null) {
            return;
        }
        Object sink = toolContext.getContext().get(SINK_KEY);
        if (sink instanceof Sinks.Many) {
            ((Sinks.Many<AgentChatStreamVO>) sink).tryEmitNext(event);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_SUMMARY_LENGTH
                ? text
                : text.substring(0, MAX_SUMMARY_LENGTH) + "…";
    }
}
