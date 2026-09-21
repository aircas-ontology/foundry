package com.aircas.ptr.foundry.agent.server.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent 对话入参。
 */
@Data
@Schema(description = "Agent 对话入参")
public class AgentChatParam {

    /**
     * 用户对话消息。
     */
    @NotBlank(message = "对话消息不能为空")
    @Schema(description = "用户对话消息", example = "平台上有哪些本体？", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    /**
     * 会话 id，用于多轮对话记忆隔离；同一 sessionId 的多次请求共享上下文。
     *
     * <p>约定：由前端生成并<b>每轮请求必带同一值</b>；用户发起“新对话”时前端重新生成一个新的
     * UUID v4（不可预测，避免会话被越权读取）。后端不再生成或兜底默认会话，缺失即校验失败。</p>
     */
    @NotBlank(message = "会话 id 不能为空")
    @Schema(description = "会话 id（多轮记忆键），同一会话每轮传同一值；新对话由前端生成新的 UUID v4",
            example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    /**
     * 当前已选数据源 id（datasource_connection 表主键），用于功能二"新建本体对象"等需要数据源上下文的操作。
     * 为空表示用户尚未选择数据源。
     */
    @Schema(description = "当前已选数据源 id（datasource_connection 表主键），为空表示未选择", example = "1")
    private Integer datasourceId;

    /**
     * 当前所在空间 id，用于功能二"新建本体对象"等需要空间上下文的操作（如列分类）。
     * 为空表示用户尚未选择空间。
     */
    @Schema(description = "当前所在空间 id，为空表示未选择", example = "1")
    private Integer spaceId;
}
