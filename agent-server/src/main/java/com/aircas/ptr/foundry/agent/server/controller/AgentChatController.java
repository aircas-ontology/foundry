package com.aircas.ptr.foundry.agent.server.controller;

import com.aircas.ptr.foundry.agent.server.model.param.AgentChatParam;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatStreamVO;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatVO;
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

    /** sessionId 为空时使用的默认会话键。 */
    private static final String DEFAULT_SESSION_ID = "default";

    private final ChatClient chatClient;

    @PostMapping("/completions")
    @Operation(summary = "同步对话：一次性返回大模型完整回复")
    public RestResult<AgentChatVO> completions(@RequestBody @Valid AgentChatParam param) {
        String reply = chatClient.prompt()
                .system(buildContextSystem(param))
                .user(param.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, resolveSessionId(param)))
                .call()
                .content();
        return RestResult.ofData(AgentChatVO.builder().reply(reply).build());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话：以 SSE 类型化事件逐段返回思考过程与回复")
    public Flux<ServerSentEvent<AgentChatStreamVO>> stream(@RequestBody @Valid AgentChatParam param) {
        // 每请求一个事件 sink：工具回调经 toolContext 拿到它，把 tool_call/tool_result 推入本流
        Sinks.Many<AgentChatStreamVO> sink = Sinks.many().unicast().onBackpressureBuffer();
        String sessionId = resolveSessionId(param);

        Flux<AgentChatStreamVO> contentEvents = chatClient.prompt()
                .system(buildContextSystem(param))
                .user(param.getMessage())
                .toolContext(buildToolContext(sink))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content()
                .map(AgentChatStreamVO::content)
                .doOnComplete(sink::tryEmitComplete)
                .doOnError(e -> sink.tryEmitComplete());

        return Flux.merge(sink.asFlux(), contentEvents)
                .map(this::toSse)
                .concatWith(Mono.fromSupplier(() -> toSse(AgentChatStreamVO.done())))
                .onErrorResume(e -> Flux.just(toSse(AgentChatStreamVO.error(e.getMessage()))));
    }

    /**
     * 构建工具上下文：携带事件 sink（仅流式），供工具回调把 tool_call/tool_result 推入当前 SSE 流。
     */
    private Map<String, Object> buildToolContext(Sinks.Many<AgentChatStreamVO> sink) {
        Map<String, Object> ctx = new HashMap<>();
        if (sink != null) {
            ctx.put(AgentStreamEvents.SINK_KEY, sink);
        }
        return ctx;
    }

    private ServerSentEvent<AgentChatStreamVO> toSse(AgentChatStreamVO vo) {
        return ServerSentEvent.builder(vo).build();
    }

    private String resolveSessionId(AgentChatParam param) {
        return (param.getSessionId() == null || param.getSessionId().isBlank())
                ? DEFAULT_SESSION_ID
                : param.getSessionId();
    }

    /**
     * 构建每请求的上下文系统提示，告知模型当前已选的数据源和空间。
     */
    private String buildContextSystem(AgentChatParam param) {
        StringBuilder sb = new StringBuilder("当前会话上下文：");
        if (param.getDatasourceId() != null) {
            sb.append("已选数据源 id=").append(param.getDatasourceId()).append("；");
        } else {
            sb.append("用户尚未选择数据源；");
        }
        if (param.getSpaceId() != null) {
            sb.append("当前空间 id=").append(param.getSpaceId()).append("。");
        } else {
            sb.append("用户尚未选择空间。");
        }
        return sb.toString();
    }
}
