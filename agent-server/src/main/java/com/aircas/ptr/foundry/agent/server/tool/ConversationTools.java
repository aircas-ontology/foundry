package com.aircas.ptr.foundry.agent.server.tool;

import com.aircas.ptr.foundry.agent.server.session.ConversationResetService;
import com.aircas.ptr.foundry.agent.server.stream.AgentStreamEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 会话控制类本地工具（agent-server 本地注册，非 MCP）。
 *
 * <p>与 ontology-server 的 MCP 工具不同：清空上下文需要操作 agent-server 进程内的 {@code ChatMemory}
 * 与 {@code SessionStateStore}，远端 MCP 工具无法触及，故在本地用 Spring AI {@link Tool} 声明，
 * 经 {@code ChatClientConfig} 的 {@code defaultTools(...)} 注册到 ChatClient。</p>
 *
 * <p>当前 sessionId 不入模型可见的工具参数，而是由 {@code AgentChatController} 通过
 * {@code toolContext} 以 {@link AgentStreamEvents#SESSION_ID_KEY} 注入，本类从 {@link ToolContext} 读取，
 * 避免模型伪造他人 sessionId。</p>
 */
@Component
@RequiredArgsConstructor
public class ConversationTools {

    private final ConversationResetService conversationResetService;

    @Tool(description = """
            清空当前会话的上下文：包括多轮对话记忆，以及本体构建过程中已记录的状态
            （已选空间 space_selected、已选数据源 datasource_selected、对象定义、属性/关系选择等）。
            仅当用户明确表达"清空上下文/清除记忆/清空对话/重新开始/新对话/reset"等重置意图时调用；
            调用成功后会话回到全新状态，请向用户简短确认已清空。切勿在其他场景擅自调用。
            """)
    public String clearConversationContext(ToolContext toolContext) {
        Object sessionId = (toolContext == null || toolContext.getContext() == null)
                ? null
                : toolContext.getContext().get(AgentStreamEvents.SESSION_ID_KEY);
        conversationResetService.reset(sessionId == null ? null : sessionId.toString());
        return "当前会话的上下文记忆与本体构建状态已清空，可以开始新的对话。";
    }
}
