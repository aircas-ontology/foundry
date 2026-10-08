package com.aircas.ptr.foundry.agent.server.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 对话内选择卡片中的单个选项。
 *
 * <p>由 {@code SelectionTools#presentSelection} 通过 {@code selection_request} SSE 事件推给前端渲染，
 * 前端点击后按约定句 {@code "已选择：<label>（id=<id>）"} 回喂到下一轮 user message，
 * 由模型读取并输出对应的 {@code stage} JSON 块。</p>
 *
 * <p>{@link #id} 使用字符串而非整型：兼容 UUID（本体 uniqueIdentifier）、数字主键（datasourceId/spaceId）
 * 与列名（properties 场景）等多种主键形态，避免为不同场景设计不同字段。</p>
 *
 * <p>{@link #payload} 用于向导式结构化卡片（属性/关系表格等）：前端直接消费工具返回的原始字段渲染表格列，
 * 不必从 {@link #description} 字符串反解析；回喂仍只传 id，payload 不参与上行链路。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "选择卡片选项")
public class SelectionOptionVO {

    @Schema(description = "选项 id（前端点击后原样回喂模型；可为数字主键字符串、UUID 或列名）",
            example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "选项主标题（卡片显示的核心文本）",
            example = "PG 主库", requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;

    @Schema(description = "选项副标题/描述（卡片次要信息，可为空；结构化卡片渲染应优先取 payload）",
            example = "POSTGRESQL，127.0.0.1:35432/ontology")
    private String description;

    @Schema(description = "工具返回原始字段透传（前端渲染表格式卡片的数据源，如属性/关系的完整字段；可为空）")
    private Map<String, Object> payload;
}
