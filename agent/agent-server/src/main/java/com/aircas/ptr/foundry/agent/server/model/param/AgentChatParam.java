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
}
