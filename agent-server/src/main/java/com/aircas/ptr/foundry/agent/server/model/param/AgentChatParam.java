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
     * 会话 id，用于多轮对话记忆隔离；同一 sessionId 的多次请求共享上下文。为空时使用默认会话。
     */
    @Schema(description = "会话 id（多轮记忆键），同一会话传同一值；为空用默认会话", example = "sess-001")
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
