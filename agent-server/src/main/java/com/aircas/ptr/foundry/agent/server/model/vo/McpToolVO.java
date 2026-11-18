package com.aircas.ptr.foundry.agent.server.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 工具清单条目：与注册给大模型的工具定义一一对应，供前端展示可用能力。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP 工具清单条目")
public class McpToolVO {

    @Schema(description = "工具名（MCP tools/list 暴露的名字）", example = "getCategoryTree")
    private String name;

    @Schema(description = "工具描述（大模型可见的用途说明）")
    private String description;

    @Schema(description = "入参 JSON Schema（对象形式，前端可直接渲染参数表单）")
    private Object inputSchema;
}