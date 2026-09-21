package com.aircas.ptr.foundry.agent.server.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 对话结果视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent 对话结果")
public class AgentChatVO {

    /**
     * 大模型回复内容（已综合工具调用结果）。
     */
    @Schema(description = "大模型回复内容")
    private String reply;
}
