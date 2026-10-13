package com.aircas.ptr.foundry.agent.server.service;

import com.aircas.ptr.foundry.agent.server.config.ChatClientConfig;
import com.aircas.ptr.foundry.agent.server.model.param.AgentChatParam;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatStreamVO;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatVO;
import com.aircas.ptr.foundry.agent.server.session.SessionStageCollector;
import com.aircas.ptr.foundry.agent.server.session.SessionStateStore;
import com.aircas.ptr.foundry.agent.server.stream.AgentStreamEvents;
import com.aircas.ptr.foundry.agent.server.stream.SelectionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.reactivestreams.Subscription;

/**
 * Agent 对话编排服务。
 *
 * <p>承载与大模型交互的全部业务编排：system 提示词拼装（默认提示词 + 本次会话上下文 + 跨轮持久的对象构建
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

    /**
     * 流式聚合：每批最多攒多少个模型响应块（token 级增量，中文常约 1 字/块）。
     * 配合句子边界检测使用：攒够该数量后开始检查句子边界，在标点处才推送，
     * 规避逐 token 推送导致前端"思考过程"每次只蹦 2~3 字的碎片化。
     */
    private static final int STREAM_BUFFER_SIZE = 30;

    /** 流式聚合：距上一批超过该时长即冲刷，为块延迟设上界，避免模型吐字慢时长时间不出块。 */
    private static final Duration STREAM_BUFFER_TIMEOUT = Duration.ofMillis(300);

    private final ChatClient chatClient;

    /** 会话级对象构建状态存储：跨轮持久化各 stage，注入 system 消息，规避 ChatMemory 滑动窗口淘汰。 */
    private final SessionStateStore sessionStateStore;

    /** 从模型回复中抽取带 stage 的 JSON 块并写入 {@link #sessionStateStore}。 */
    private final SessionStageCollector sessionStageCollector;

    /** 确定性选择卡片解析器：识别模型输出的 request_selection 标记，程序化取数推 selection_request。 */
    private final SelectionResolver selectionResolver;

    /** 活跃流订阅注册表：sessionId → Subscription，供中断接口取消模型 token 流。 */
    private final Map<String, Subscription> activeSubscriptions = new ConcurrentHashMap<>();

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
     * content 增量先逐字累积（供流结束时整体解析 stage 状态，单订阅内 reactor 算子串行、无并发写），
     * 再按 {@link #CONTENT_BUFFER_SIZE}/{@link #CONTENT_BUFFER_TIMEOUT} 聚合成块下发，规避逐字 SSE 帧爆炸与前端抖动。
     * SSE 封装由 controller 完成。</p>
     *
     * @param param 对话入参
     * @return 领域事件流，末尾追加 {@code done}，异常降级为 {@code error} 事件
     */
    public Flux<AgentChatStreamVO> chatStream(AgentChatParam param) {
        // 每请求一个事件 sink：工具回调经 toolContext 拿到它，把 tool_call/tool_result 推入本流
        Sinks.Many<AgentChatStreamVO> sink = Sinks.many().unicast().onBackpressureBuffer();
        String sessionId = param.getSessionId();
        // 累积本轮 content 正文，流结束后整体解析 stage 状态（thinking 不参与 stage 解析）
        StringBuilder replyBuffer = new StringBuilder();
        // 句子边界缓冲：thinking 与 content 分别聚合，确保以完整句子推送且事件类型正确
        StringBuilder thinkingBuffer = new StringBuilder();
        StringBuilder contentBuffer = new StringBuilder();

        Flux<AgentChatStreamVO> modelEvents = chatClient.prompt()
                .system(buildSystemPrompt(param, sessionId))
                .user(param.getMessage())
                .toolContext(buildToolContext(sink, sessionId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse()
                // 把模型原生 token 流按「攒够 STREAM_BUFFER_SIZE 个响应块或距上一批超过 STREAM_BUFFER_TIMEOUT」
                // 聚成一批再下发，规避逐 token 导致的 SSE 帧爆炸与前端抖动（尤其"思考过程"碎片化）；
                // 上游 complete 时会冲刷尾部残余，末批不丢
                .bufferTimeout(STREAM_BUFFER_SIZE, STREAM_BUFFER_TIMEOUT)
                // 每批归并事件：reasoning 增量 → thinking 事件，content 增量 → content 事件 + replyBuffer 累积
                .flatMapIterable(batch -> toStreamEvents(batch, replyBuffer, thinkingBuffer, contentBuffer))
                .doOnComplete(() -> {
                    // 冲刷句子缓冲区残余，确保前端不丢末尾文本
                    if (thinkingBuffer.length() > 0) {
                        sink.tryEmitNext(AgentChatStreamVO.thinking(thinkingBuffer.toString()));
                        thinkingBuffer.setLength(0);
                    }
                    if (contentBuffer.length() > 0) {
                        sink.tryEmitNext(AgentChatStreamVO.content(contentBuffer.toString()));
                        replyBuffer.append(contentBuffer);
                        contentBuffer.setLength(0);
                    }
                    String reply = replyBuffer.toString();
                    // 先落盘本轮 stage 状态（properties_proposed / relations_proposed 等），供下面确定性回查取数
                    sessionStageCollector.collectAndStore(sessionId, reply);
                    // 再由状态机确定性判断是否推选择卡片（不依赖模型调工具/输出标记，在 done 之前推）
                    // 传入当前请求的 datasourceId/spaceId，使解析器能感知前端已选状态，避免重复推卡
                    selectionResolver.resolveForRound(sessionId, reply, param.getDatasourceId(), param.getSpaceId(), sink);
                    sink.tryEmitComplete();
                })
                .doOnError(e -> sink.tryEmitComplete())
                .doOnSubscribe(sub -> activeSubscriptions.put(sessionId, sub))
                .doFinally(signal -> activeSubscriptions.remove(sessionId));

        return Flux.merge(sink.asFlux(), modelEvents)
                .concatWith(Mono.fromSupplier(AgentChatStreamVO::done))
                .onErrorResume(e -> Flux.just(AgentChatStreamVO.error(e.getMessage())));
    }

    /**
     * 中断指定会话的流式对话：取消模型 token 流的上游订阅，使 SSE 连接随之关闭。
     *
     * <p>前端点击"停止"按钮时调用。取消订阅后 Reactor 的 {@code doFinally} 会自动清理注册表；
     * 若该会话无活跃流（已结束或不存在）则返回 false。</p>
     *
     * @param sessionId 要中断的会话 id
     * @return 是否成功找到并取消了活跃流
     */
    public boolean abort(String sessionId) {
        Subscription sub = activeSubscriptions.remove(sessionId);
        if (sub != null) {
            sub.cancel();
            return true;
        }
        return false;
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
     * 构建每请求发送给模型的完整 system 文本 = 默认系统提示词（角色/规范/对象构建全流程）+ 本次会话上下文。
     *
     * <p>必须这样拼接：Spring AI 的 {@code .system(String)} 会整体覆盖 {@code ChatClientConfig} 里
     * {@code defaultSystem(DEFAULT_SYSTEM_PROMPT)} 设置的内容；若只传上下文，默认提示词将丢失、
     * 对象构建全流程引导不会生效。</p>
     */
    private String buildSystemPrompt(AgentChatParam param, String sessionId) {
        return ChatClientConfig.DEFAULT_SYSTEM_PROMPT + "\n\n" + buildContextSystem(param, sessionId);
    }

    /**
     * 构建本次会话上下文片段（追加在默认系统提示词之后）：告知模型当前已选数据源/空间，并注入跨轮持久的对象构建状态。
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
            sb.append("\n【已记录的对象构建状态】（跨轮持久，优先于短期记忆；当上方前端入参未提供 spaceId/datasourceId 时，以本块中的 space_selected/datasource_selected 为准）：")
              .append(state);
        }
        return sb.toString();
    }

    /**
     * 把一批流式 {@link ChatResponse} 归并为本轮事件，按句子边界聚合。
     *
     * <p>reasoning 增量追加到 thinkingBuffer，在标点处切分后以 {@code thinking} 事件发出；
     * content 增量追加到 contentBuffer，在标点处切分后以 {@code content} 事件发出，
     * 同时累积进 replyBuffer 供流末 stage 解析。两者互不混淆。</p>
     *
     * <p>同时检测模型循环重复（连续输出相同内容），发现时插入打断提示。</p>
     */
    private static List<AgentChatStreamVO> toStreamEvents(
            List<ChatResponse> batch, StringBuilder replyBuffer,
            StringBuilder thinkingBuffer, StringBuilder contentBuffer) {
        List<AgentChatStreamVO> events = new ArrayList<>();
        for (ChatResponse resp : batch) {
            String r = extractReasoning(resp);
            String c = extractContent(resp);
            if (!r.isEmpty()) {
                thinkingBuffer.append(r);
                flushSentences(thinkingBuffer, null, events, false);
            }
            if (!c.isEmpty()) {
                contentBuffer.append(c);
                flushSentences(contentBuffer, replyBuffer, events, true);
            }
        }
        // 检测模型循环重复（如反复输出相同内容），插入打断提示
        events.addAll(detectLoopAndInterrupt(replyBuffer, contentBuffer));
        return events;
    }

    /**
     * 冲刷句子缓冲区：按句子边界（。！？.\n）切分，将完整句子作为事件发出。
     * 超长无标点（&gt;{@code MAX_SENTENCE_LENGTH} 字）强制切一次。
     *
     * @param buffer      句子累积缓冲区（调用方持有，本方法修改其内容）
     * @param replyBuffer 原始回复累积器（仅 content 类型时写入，thinking 传 null）
     * @param events      输出事件列表；为 null 时仅冲刷不产事件
     * @param isContent   true 发出 content 事件并写入 replyBuffer；false 发出 thinking 事件
     */
    private static void flushSentences(StringBuilder buffer,
                                       StringBuilder replyBuffer,
                                       List<AgentChatStreamVO> events,
                                       boolean isContent) {
        while (buffer.length() > 0) {
            int cutIdx = findSentenceBoundary(buffer);
            if (cutIdx < 0 && buffer.length() > MAX_SENTENCE_LENGTH) {
                cutIdx = buffer.length();
            }
            if (cutIdx < 0) {
                break;
            }
            String sentence = buffer.substring(0, cutIdx);
            buffer.delete(0, cutIdx);
            if (replyBuffer != null) {
                replyBuffer.append(sentence);
            }
            if (events != null && !sentence.isEmpty()) {
                events.add(isContent ? AgentChatStreamVO.content(sentence) : AgentChatStreamVO.thinking(sentence));
            }
        }
    }

    /**
     * 在缓冲区中查找第一个句子边界位置（句号/问号/感叹号/换行之后）。
     * 返回切割位置（不含标点），未找到返回 -1。
     */
    private static int findSentenceBoundary(StringBuilder sb) {
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '\n' || c == '\r') {
                return i + 1;
            }
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                int end = i + 1;
                while (end < sb.length() && (sb.charAt(end) == ' ' || sb.charAt(end) == '\u3000')) {
                    end++;
                }
                return end;
            }
        }
        return -1;
    }

    /** 句子缓冲区最大长度：超过此值无标点则强制切出，避免无限等待。 */
    private static final int MAX_SENTENCE_LENGTH = 200;

    /**
     * 检测模型循环重复：取回复缓冲区末尾文本，若同一短片段重复出现 ≥3 次，
     * 则生成打断提示事件，引导模型换种表达。
     */
    private static List<AgentChatStreamVO> detectLoopAndInterrupt(
            StringBuilder replyBuffer, StringBuilder contentBuffer) {
        List<AgentChatStreamVO> events = new ArrayList<>(2);
        String fullReply = replyBuffer.toString();
        if (fullReply.length() < 120) {
            return events;
        }
        int tailLen = Math.min(200, fullReply.length());
        String recent = fullReply.substring(fullReply.length() - tailLen);
        for (int segLen = 15; segLen <= tailLen / 3; segLen += 5) {
            String seg = recent.substring(tailLen - segLen);
            int count = 1;
            int pos = tailLen - segLen;
            while (pos >= segLen && recent.substring(pos - segLen, pos).equals(seg)) {
                count++;
                pos -= segLen;
            }
            if (count >= 3) {
                contentBuffer.setLength(0);
                String hint = "\n\n（检测到重复输出，请换种方式表达或询问用户是否需要帮助）";
                replyBuffer.append(hint);
                events.add(AgentChatStreamVO.content(hint));
                break;
            }
        }
        return events;
    }

    /**
     * 从流式 {@link ChatResponse} 抽取正文文本；无 result/output 的块（如仅携 usage 的尾块）返回空串。
     */
    private static String extractContent(ChatResponse resp) {
        AssistantMessage output = outputOf(resp);
        if (output == null) {
            return "";
        }
        String text = output.getText();
        return text == null ? "" : text;
    }

    /**
     * 从流式 {@link ChatResponse} 抽取模型思维链（reasoning content）增量。
     *
     * <p>OpenAI 兼容接口下思维链置于 {@link AssistantMessage} 的 metadata；不同厂商/版本键名不一，
     * 故依次尝试 {@code reasoningContent} / {@code reasoning_content}；均无则返回空串（不推 thinking）。</p>
     */
    private static String extractReasoning(ChatResponse resp) {
        AssistantMessage output = outputOf(resp);
        if (output == null || output.getMetadata() == null) {
            return "";
        }
        Object reasoning = output.getMetadata().get("reasoningContent");
        if (reasoning == null) {
            reasoning = output.getMetadata().get("reasoning_content");
        }
        return reasoning == null ? "" : reasoning.toString();
    }

    private static AssistantMessage outputOf(ChatResponse resp) {
        if (resp == null || resp.getResult() == null) {
            return null;
        }
        return resp.getResult().getOutput();
    }
}
