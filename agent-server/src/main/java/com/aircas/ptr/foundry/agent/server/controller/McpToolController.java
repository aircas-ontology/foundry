package com.aircas.ptr.foundry.agent.server.controller;

import com.aircas.ptr.foundry.agent.server.model.vo.McpToolVO;
import com.aircas.ptr.foundry.common.base.RestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具清单入口。
 *
 * <p>暴露 agent-server 启动时经 MCP 握手 + {@code tools/list} 从 ontology-server 动态发现的工具，
 * 与注册给大模型的是同一批 {@link ToolCallback}，供前端展示当前可用能力（名称/描述/入参 Schema）。</p>
 */
@Tag(name = "MCP 工具")
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpToolController {

    private final ToolCallbackProvider mcpToolCallbackProvider;

    private final ObjectMapper objectMapper;

    @GetMapping("/tools")
    @Operation(summary = "MCP 工具清单：返回动态发现的工具名、描述与入参 JSON Schema")
    public RestResult<List<McpToolVO>> tools() {
        ToolCallback[] callbacks = mcpToolCallbackProvider.getToolCallbacks();
        List<McpToolVO> result = new ArrayList<>();
        if (callbacks != null) {
            for (ToolCallback callback : callbacks) {
                var definition = callback.getToolDefinition();
                result.add(McpToolVO.builder()
                        .name(definition.name())
                        .description(definition.description())
                        .inputSchema(parseSchema(definition.inputSchema()))
                        .build());
            }
        }
        return RestResult.ofData(result);
    }

    /** Schema 原文是 JSON 字符串，解析为对象返回，前端免去二次 parse；解析失败时降级返回原文。 */
    private Object parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(schemaJson, Map.class);
        } catch (Exception e) {
            log.warn("[McpToolController] inputSchema 解析失败，按原文返回: {}", e.getMessage());
            return schemaJson;
        }
    }
}