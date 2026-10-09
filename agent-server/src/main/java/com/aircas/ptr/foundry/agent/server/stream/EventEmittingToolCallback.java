package com.aircas.ptr.foundry.agent.server.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 工具回调装饰器：在工具被大模型调用前后，向流式事件 sink 推送 {@code tool_call / tool_result} 事件。
 *
 * <p>采用装饰器而非在 {@code @Tool} 方法上加 {@link ToolContext} 参数，是为了不改动工具 Bean 签名、
 * 保持工具实现类的方法签名纯净；事件发射逻辑集中在本类。</p>
 *
 * <p><b>与确定性卡片方案的关系</b>：选择卡片（selection_request）已统一由 {@link SelectionResolver} 在
 * 后端流末确定性生成，不再依赖模型是否调用查询工具，因此本装饰器<b>不再自动推卡</b>，仅负责
 * tool_call / tool_result 事件；list 类工具的 tool_result 仍降级为一句话计数，避免与卡片数据重复。</p>
 *
 * <p><b>tool_result 精简</b>：list 类工具返回的原始 JSON 往往与卡片 {@code options} 内容重复，
 * 因此在成功返回时把 summary 降级为一句话计数（“共 N 条记录”）。MCP 传输层把工具返回值包成
 * {@code [{"text":"<内层 JSON>"}]}，本类会先脱壳再计数。失败 / 非 JSON 数组结果仍原样上报，方便定位。</p>
 */
public class EventEmittingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ObjectMapper objectMapper;

    public EventEmittingToolCallback(ToolCallback delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
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
            AgentStreamEvents.toolResult(toolContext, toolName, summarizeIfListResult(output));
            return output;
        } catch (RuntimeException e) {
            AgentStreamEvents.toolResult(toolContext, toolName, "调用失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 如果工具返回的是 JSON 数组（list 类工具的典型形态），把 summary 精简为 “共 N 条记录”，
     * 避免与紧随其后的 selection_request 事件重复携带同一份数据；其他情形（对象 / 字符串 / 解析失败）
     * 原样返回，由 {@link AgentStreamEvents#toolResult} 内部的 {@code truncate} 兜底截断。
     *
     * <p>MCP 传输层会把工具返回值包成 {@code [{"text":"<内层字符串化 JSON>"}]}，因此先尝试脱壳一层再判数组。</p>
     */
    private String summarizeIfListResult(String raw) {
        try {
            JsonNode array = extractArray(raw);
            return array != null ? "共 " + array.size() + " 条记录" : raw;
        } catch (Exception ignored) {
            // 非合法 JSON：原样返回
            return raw;
        }
    }

    /**
     * 把工具返回值解析为数据数组节点：兼容 MCP 包裹形态 {@code [{"text":"<内层数组>"}]} 与本地工具直接返回的数组。
     *
     * @return 数组节点；非数组或空串返回 {@code null}；非法 JSON 抛异常由调用方处置
     */
    private JsonNode extractArray(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(raw.trim());
        if (!root.isArray()) {
            return null;
        }
        // MCP 内容块：[{"text":"..."}]；解开内层字符串再判数组
        if (root.size() == 1 && root.get(0).isObject() && root.get(0).has("text")) {
            JsonNode inner = objectMapper.readTree(root.get(0).get("text").asText());
            return inner.isArray() ? inner : null;
        }
        // 本地工具直接返回数据数组
        return root;
    }
}
