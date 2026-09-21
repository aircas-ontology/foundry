package com.aircas.ptr.foundry.agent.server.stream;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 工具回调装饰器：在工具被大模型调用前后，向流式事件 sink 推送 {@code tool_call / tool_result} 事件。
 *
 * <p>采用装饰器而非在 {@code @Tool} 方法上加 {@link ToolContext} 参数，是为了不改动工具 Bean 签名、
 * 保持工具实现类的方法签名纯净；事件发射逻辑集中在本类。</p>
 */
public class EventEmittingToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public EventEmittingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        AgentStreamEvents.toolCall(toolContext, toolName, toolInput);
        try {
            String output = delegate.call(toolInput, toolContext);
            AgentStreamEvents.toolResult(toolContext, toolName, output);
            return output;
        } catch (RuntimeException e) {
            AgentStreamEvents.toolResult(toolContext, toolName, "调用失败: " + e.getMessage());
            throw e;
        }
    }
}
