package com.aircas.ptr.foundry.agent.server.controller;

import com.aircas.ptr.foundry.agent.server.model.param.AgentChatParam;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatStreamVO;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatVO;
import com.aircas.ptr.foundry.agent.server.service.AgentChatService;
import com.aircas.ptr.foundry.agent.server.session.ConversationResetService;
import com.aircas.ptr.foundry.common.base.RestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Agent 对话入口。
 *
 * <p>对外提供两类对话接口：同步（一次性返回完整回复）与流式（SSE 类型化事件逐段返回）。与大模型交互的
 * 编排（system 提示词拼装、toolContext 构建、ChatClient 调用、工具事件与回复增量合流、stage 状态采集）
 * 统一下沉到 {@link AgentChatService}；本 controller 只负责收参数、调用 service，以及把领域事件流
 * {@link AgentChatStreamVO} 适配为 SSE 传输。</p>
 *
 * <p>流式事件以 JSON 承载并按 {@code type} 区分：{@code thinking / tool_call / tool_result} 供前端渲染
 * 可折叠的"思考过程"，{@code content} 为主回答增量，{@code done / error} 为结束与异常信号。
 * 大模型在对话过程中会按需调用 MCP 动态发现的工具（ontology-server 的 MCP Server 提供）。
 * 多轮上下文由 {@code MessageChatMemoryAdvisor} 按 {@code sessionId} 隔离记忆。</p>
 */
@Tag(name = "Agent 对话")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Validated
public class AgentChatController {

    private final AgentChatService agentChatService;

    /** 会话上下文重置：清空 ChatMemory 记忆窗口 + SessionStateStore 构建状态。 */
    private final ConversationResetService conversationResetService;

    @PostMapping("/completions")
    @Operation(summary = "同步对话：一次性返回大模型完整回复")
    public RestResult<AgentChatVO> completions(@RequestBody @Valid AgentChatParam param) {
        return RestResult.ofData(agentChatService.chat(param));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话：以 SSE 类型化事件逐段返回思考过程与回复")
    public Flux<ServerSentEvent<AgentChatStreamVO>> stream(@RequestBody @Valid AgentChatParam param) {
        return agentChatService.chatStream(param).map(this::toSse);
    }

    @PostMapping("/clear_context")
    @Operation(summary = "清空会话上下文：清除多轮记忆与本体构建状态（用于“新对话/清空”）")
    public RestResult<Boolean> clearContext(@RequestParam String sessionId) {
        conversationResetService.reset(sessionId);
        return RestResult.ofData(Boolean.TRUE);
    }

    /** 领域事件 → SSE 传输封装（纯表现层适配）。 */
    private ServerSentEvent<AgentChatStreamVO> toSse(AgentChatStreamVO vo) {
        return ServerSentEvent.builder(vo).build();
    }
}
