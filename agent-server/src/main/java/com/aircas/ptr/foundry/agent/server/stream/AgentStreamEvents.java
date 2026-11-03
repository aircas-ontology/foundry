package com.aircas.ptr.foundry.agent.server.stream;

import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatStreamVO;
import com.aircas.ptr.foundry.agent.server.model.vo.SelectionOptionVO;
import org.springframework.ai.chat.model.ToolContext;
import reactor.core.publisher.Sinks;

import java.util.List;

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

    /**
     * 推送"选择卡片"事件。选项列表不经过 {@link #truncate}，保证 options 不会被 2000 字上限截断。
     *
     * @return 是否成功推入事件 sink；{@code false} 表示当前请求没有流式通道（如同步 completions 接口），
     *         调用方需据此降级（改为文本列候选或提示模型自行处理）
     */
    public static boolean selectionRequest(ToolContext toolContext, String scenario, String title,
                                           boolean multiSelect, List<SelectionOptionVO> options) {
        return emit(toolContext, AgentChatStreamVO.selectionRequest(scenario, title, multiSelect, options));
    }

    /**
     * 推送"选择卡片"事件（直接面向事件 sink 的重载）。
     *
     * <p>用于确定性卡片方案：{@code AgentChatService} 在大模型流结束后识别 {@code request_selection} 标记，
     * 程序化取数拼卡并把 {@code selection_request} 推入本次请求独占的 sink（此时已无 ToolContext）。</p>
     *
     * @return 是否成功推入 sink；{@code false} 表示 sink 为空或已关闭
     */
    public static boolean selectionRequest(Sinks.Many<AgentChatStreamVO> sink, String scenario, String title,
                                           boolean multiSelect, List<SelectionOptionVO> options) {
        if (sink == null) {
            return false;
        }
        return sink.tryEmitNext(AgentChatStreamVO.selectionRequest(scenario, title, multiSelect, options)).isSuccess();
    }

    /**
     * 推送"思考过程"事件（面向事件 sink）。
     *
     * <p>用于把大模型的思维链（reasoning content）实时并入 SSE 流，供前端"思考过程"面板展示。
     * 由 {@code AgentChatService} 在流式 {@code chatResponse} 的 {@code doOnNext} 中抽取模型 reasoning
     * 增量后推入本次请求独占的 sink；对不返回思维链的模型（如非 thinking 模式的 qwen-plus）为空操作。</p>
     *
     * @return 是否成功推入 sink；{@code false} 表示 sink 为空或已关闭
     */
    public static boolean thinking(Sinks.Many<AgentChatStreamVO> sink, String reasoning) {
        if (sink == null || reasoning == null || reasoning.isEmpty()) {
            return false;
        }
        return sink.tryEmitNext(AgentChatStreamVO.thinking(reasoning)).isSuccess();
    }

    @SuppressWarnings("unchecked")
    private static boolean emit(ToolContext toolContext, AgentChatStreamVO event) {
        if (toolContext == null || toolContext.getContext() == null) {
            return false;
        }
        Object sink = toolContext.getContext().get(SINK_KEY);
        if (sink instanceof Sinks.Many) {
            return ((Sinks.Many<AgentChatStreamVO>) sink).tryEmitNext(event).isSuccess();
        }
        return false;
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
