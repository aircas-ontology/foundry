package com.aircas.ptr.foundry.agent.server.service;

import com.aircas.ptr.foundry.agent.server.config.ChatClientConfig;
import com.aircas.ptr.foundry.agent.server.model.param.AgentChatParam;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatStreamVO;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatVO;
import com.aircas.ptr.foundry.agent.server.session.SessionStageCollector;
import com.aircas.ptr.foundry.agent.server.session.SessionStateStore;
import com.aircas.ptr.foundry.agent.server.stream.AgentStreamEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 对话编排服务。
 *
 * <p>承载与大模型交互的全部业务编排：system 提示词拼装（默认提示词 + 本次会话上下文 + 跨轮持久的本体构建
 * 状态）、toolContext 构建、ChatClient 调用，以及流式场景下工具事件与回复增量的合流与 stage 状态采集。
 * 自 {@code AgentChatController} 下沉而来，使 controller 回归「收参数 → 调 service → 包 SSE」。</p>
 *
 * <p>同步入口 {@link #chat} 返回完整回复 VO；流式入口 {@link #chatStream} 返回<b>领域事件流</b>
 * （{@link AgentChatStreamVO}：thinking / tool_call / tool_result / content / done / error），
 * 由 controller 适配为 SSE。SSE 传输封装不属于本服务职责，故本类不出现 {@code ServerSentEvent}。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final ChatClient chatClient;

    /** 会话级本体构建状态存储：跨轮持久化各 stage，注入 system 消息，规避 ChatMemory 滑动窗口淘汰。 */
    private final SessionStateStore sessionStateStore;

    /** 从模型回复中抽取带 stage 的 JSON 块并写入 {@link #sessionStateStore}。 */
    private final SessionStageCollector sessionStageCollector;

    /**
     * 同步对话：一次性返回大模型完整回复。
     *
     * @param param 对话入参（消息、会话 id、已选数据源/空间）
     * @return 完整回复 VO
     */
    public AgentChatVO chat(AgentChatParam param) {
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
        return AgentChatVO.builder().reply(reply).build();
    }

    /**
     * 流式对话：返回领域事件流（thinking / tool_call / tool_result / content / done / error）。
     *
     * <p>每次调用创建一个独占事件 sink，经 toolContext 传给工具回调装饰器，把工具步骤并入同一条流；
     * content 增量累积后于流结束时整体解析 stage 状态（单订阅内 reactor 算子串行，无并发写）。
     * SSE 封装由 controller 完成。</p>
     *
     * @param param 对话入参
     * @return 领域事件流，末尾追加 {@code done}，异常降级为 {@code error} 事件
     */
    public Flux<AgentChatStreamVO> chatStream(AgentChatParam param) {
        // 每请求一个事件 sink：工具回调经 toolContext 拿到它，把 tool_call/tool_result 推入本流
        Sinks.Many<AgentChatStreamVO> sink = Sinks.many().unicast().onBackpressureBuffer();
        String sessionId = param.getSessionId();
        // 累积本轮 content 增量，流结束后整体解析 stage 状态
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
                .concatWith(Mono.fromSupplier(AgentChatStreamVO::done))
                .onErrorResume(e -> Flux.just(AgentChatStreamVO.error(e.getMessage())));
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
