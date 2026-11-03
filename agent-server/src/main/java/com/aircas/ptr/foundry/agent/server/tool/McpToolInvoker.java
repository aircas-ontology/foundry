package com.aircas.ptr.foundry.agent.server.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MCP 工具程序化调用器。
 *
 * <p>背景：确定性卡片方案要求后端<b>不依赖大模型主动调用查询工具</b>就能拿到空间/数据源列表——
 * 由 {@code SelectionResolver} 在识别到模型的 {@code request_selection} 标记后，直接用本类按工具名
 * 调用 MCP 工具回调取真实数据，再拼成 {@code selection_request} 推给前端。</p>
 *
 * <p>工具回调来自 Spring AI MCP Client 启动时动态发现并装配的 {@link ToolCallbackProvider}，
 * 与注册给 ChatClient 的是同一批回调；此处按 {@code getToolDefinition().name()} 索引，直接 {@code call(args)}。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpToolInvoker {

    private final ToolCallbackProvider mcpToolCallbackProvider;

    /**
     * 按工具名程序化调用一个 MCP 工具。
     *
     * @param toolName 工具名（与 MCP {@code tools/list} 暴露的名字一致，如 {@code listOntologySpaces}）
     * @param argsJson JSON 形式的入参（无参工具传 {@code "{}"}）
     * @return 工具原始返回字符串（MCP 传输层通常包成 {@code [{"text":"..."}]}）
     * @throws IllegalStateException 工具不存在时抛出，由调用方决定降级策略
     */
    public String call(String toolName, String argsJson) {
        ToolCallback callback = findCallback(toolName);
        if (callback == null) {
            throw new IllegalStateException("MCP 工具不存在: " + toolName);
        }
        String result = callback.call(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
        log.debug("[McpToolInvoker] 程序化调用 tool={}, resultLen={}", toolName,
                result == null ? 0 : result.length());
        return result;
    }

    private ToolCallback findCallback(String toolName) {
        ToolCallback[] callbacks = mcpToolCallbackProvider.getToolCallbacks();
        if (callbacks == null) {
            return null;
        }
        Map<String, ToolCallback> byName = Arrays.stream(callbacks)
                .collect(Collectors.toMap(cb -> cb.getToolDefinition().name(), Function.identity(), (a, b) -> a));
        return byName.get(toolName);
    }
}
