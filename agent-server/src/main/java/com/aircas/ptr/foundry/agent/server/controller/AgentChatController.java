package com.aircas.ptr.foundry.agent.server.controller;

import com.aircas.ptr.foundry.agent.server.config.ChatClientConfig;
import com.aircas.ptr.foundry.agent.server.model.param.AgentChatParam;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatStreamVO;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatVO;
import com.aircas.ptr.foundry.agent.server.session.ConversationResetService;
import com.aircas.ptr.foundry.agent.server.session.SessionStageCollector;
import com.aircas.ptr.foundry.agent.server.session.SessionStateStore;
import com.aircas.ptr.foundry.agent.server.stream.AgentStreamEvents;
import com.aircas.ptr.foundry.common.base.RestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 对话入口。
 *
 * <p>对外提供两类对话接口：同步（一次性返回完整回复）与流式（SSE 类型化事件逐段返回）。
 * 流式事件以 JSON 承载并按 {@code type} 区分：{@code thinking / tool_call / tool_result} 供前端渲染
 * 可折叠的"思考过程"，{@code content} 为主回答增量，{@code done / error} 为结束与异常信号。
 * 大模型在对话过程中会按需调用 MCP 动态发现的工具（ontology-server 的 MCP Server 提供），工具步骤经
 * {@code EventEmittingToolCallback} 装饰器并入同一条 SSE 流。</p>
 *
 * <p>多轮上下文由 {@code MessageChatMemoryAdvisor} 按 {@code sessionId} 隔离记忆。</p>
 */
@Tag(name = "Agent 对话")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Validated
public class AgentChatController {

    private final ChatClient chatClient;

    /** 会话级本体构建状态存储：跨轮持久化各 stage，注入 system 消息，规避 ChatMemory 滑动窗口淘汰。 */
    private final SessionStateStore sessionStateStore;

    /** 从模型回复中抽取带 stage 的 JSON 块并写入 {@link #sessionStateStore}。 */
    private final SessionStageCollector sessionStageCollector;

    /** 会话上下文重置：清空 ChatMemory 记忆窗口 + SessionStateStore 构建状态。 */
    private final ConversationResetService conversationResetService;

    @PostMapping("/completions")
    @Operation(summary = "同步对话：一次性返回大模型完整回复")
    public RestResult<AgentChatVO> completions(@RequestBody @Valid AgentChatParam param) {
        String sessionId = param.getSessionId();
        String reply = chatClient.prompt()
                .system(buildSystemPrompt(param, sessionId))
                .user(param.getMessage())
                .toolContext(buildToolContext(null, sessionId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        // 回复落定后采集本轮 stage 状态，供后续轮次注入
        sessionStageCollector.collectAndStore(sessionId, reply);
        return RestResult.ofData(AgentChatVO.builder().reply(reply).build());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话：以 SSE 类型化事件逐段返回思考过程与回复")
    public Flux<ServerSentEvent<AgentChatStreamVO>> stream(@RequestBody @Valid AgentChatParam param) {
        // 每请求一个事件 sink：工具回调经 toolContext 拿到它，把 tool_call/tool_result 推入本流
        Sinks.Many<AgentChatStreamVO> sink = Sinks.many().unicast().onBackpressureBuffer();
        String sessionId = param.getSessionId();
        // 累积本轮 content 增量，流结束后整体解析 stage 状态（单订阅内 reactor 算子串行，无并发写）
        StringBuilder replyBuffer = new StringBuilder();

        Flux<AgentChatStreamVO> contentEvents = chatClient.prompt()
                .system(buildSystemPrompt(param, sessionId))
                .user(param.getMessage())
                .toolContext(buildToolContext(sink, sessionId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content()
                .doOnNext(replyBuffer::append)
                .map(AgentChatStreamVO::content)
                .doOnComplete(() -> {
                    sink.tryEmitComplete();
                    sessionStageCollector.collectAndStore(sessionId, replyBuffer.toString());
                })
                .doOnError(e -> sink.tryEmitComplete());

        return Flux.merge(sink.asFlux(), contentEvents)
                .map(this::toSse)
                .concatWith(Mono.fromSupplier(() -> toSse(AgentChatStreamVO.done())))
                .onErrorResume(e -> Flux.just(toSse(AgentChatStreamVO.error(e.getMessage()))));
    }

    @PostMapping("/clear_context")
    @Operation(summary = "清空会话上下文：清除多轮记忆与本体构建状态（用于“新对话/清空”）")
    public RestResult<Boolean> clearContext(@RequestParam String sessionId) {
        conversationResetService.reset(sessionId);
        return RestResult.ofData(Boolean.TRUE);
    }

    /**
     * 构建工具上下文：携带事件 sink（仅流式）与当前会话 id，供工具回调把 tool_call/tool_result
     * 推入当前 SSE 流、并让本地会话工具（clearConversationContext）读到 sessionId。
     */
    private Map<String, Object> buildToolContext(Sinks.Many<AgentChatStreamVO> sink, String sessionId) {
        Map<String, Object> ctx = new HashMap<>();
        if (sink != null) {
            ctx.put(AgentStreamEvents.SINK_KEY, sink);
        }
        // 注入当前会话 id，供本地会话工具读取；不作为模型可见参数，避免被伪造为他人 sessionId
        if (sessionId != null) {
            ctx.put(AgentStreamEvents.SESSION_ID_KEY, sessionId);
        }
        return ctx;
    }

    private ServerSentEvent<AgentChatStreamVO> toSse(AgentChatStreamVO vo) {
        return ServerSentEvent.builder(vo).build();
    }

    /**
     * 构建每请求发送给模型的完整 system 文本 = 默认系统提示词（角色/规范/本体构建全流程）+ 本次会话上下文。
     *
     * <p>必须这样拼接：Spring AI 的 {@code .system(String)} 会整体覆盖 {@code ChatClientConfig} 里
     * {@code defaultSystem(DEFAULT_SYSTEM_PROMPT)} 设置的内容；若只传上下文，默认提示词将丢失、
     * 本体构建全流程引导不会生效。</p>
     */
    private String buildSystemPrompt(AgentChatParam param, String sessionId) {
        return ChatClientConfig.DEFAULT_SYSTEM_PROMPT + "\n\n" + buildContextSystem(param, sessionId);
    }

    /**
     * 构建本次会话上下文片段（追加在默认系统提示词之后）：告知模型当前已选数据源/空间，并注入跨轮持久的本体构建状态。
     */
    private String buildContextSystem(AgentChatParam param, String sessionId) {
        StringBuilder sb = new StringBuilder("当前会话上下文（前端本次请求携带）：");
        sb.append(param.getDatasourceId() != null
                ? "已选数据源 id=" + param.getDatasourceId() + "；"
                : "前端未传数据源 id；");
        sb.append(param.getSpaceId() != null
                ? "当前空间 id=" + param.getSpaceId() + "。"
                : "前端未传空间 id。");
        // 注入已持久化的构建状态（space_selected / datasource_selected / object_defined / ...），
        // 使模型在长对话、记忆窗口淘汰后仍能回读到用户此前在对话中做出的选择
        String state = sessionStateStore.renderState(sessionId);
        if (state != null) {
            sb.append("\n【已记录的本体构建状态】（跨轮持久，优先于短期记忆；当上方前端入参未提供 spaceId/datasourceId 时，以本块中的 space_selected/datasource_selected 为准）：")
              .append(state);
        }
        return sb.toString();
    }
}
