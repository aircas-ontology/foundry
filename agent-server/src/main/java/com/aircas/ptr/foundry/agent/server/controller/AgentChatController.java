package com.aircas.ptr.foundry.agent.server.controller;

import com.aircas.ptr.foundry.agent.server.model.param.AgentChatParam;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatStreamVO;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatVO;
import com.aircas.ptr.foundry.agent.server.model.vo.DocumentUploadVO;
import com.aircas.ptr.foundry.agent.server.service.AgentChatService;
import com.aircas.ptr.foundry.agent.server.service.DocumentUploadService;
import com.aircas.ptr.foundry.agent.server.session.ConversationResetService;
import com.aircas.ptr.foundry.common.base.RestResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 对话入口。
 *
 * <p>对外提供两类对话接口：同步（一次性返回完整回复）与流式（SSE 类型化事件逐段返回）。与大模型交互的
 * 编排（system 提示词拼装、toolContext 构建、ChatClient 调用、工具事件与回复增量合流、stage 状态采集）
 * 统一下沉到 {@link AgentChatService}；本 controller 只负责收参数与调用 service。</p>
 *
 * <p>流式接口直接返回领域事件流 {@code Flux<AgentChatStreamVO>}，配合
 * {@code produces = MediaType.TEXT_EVENT_STREAM_VALUE} 由 Spring 默认的 SSE 编码器输出
 * {@code data:{json}\n\n} 帧；事件类型（thinking/tool_call/…）已内闭在 VO 的 {@code type} 字段，
 * 无需额外的 SSE 元数据（id/event/retry）。另有 {@code /chat/stream_ndjson} 复用同一事件流，
 * 以 HTTP chunked + NDJSON（每行一个 JSON 对象）输出，供不支持/不想用 SSE 的调用方使用。</p>
 *
 * <p>大模型在对话过程中会按需调用 MCP 动态发现的工具（ontology-server 的 MCP Server 提供）。
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

    /** 会话参考文档上传：第 2 步导入资料时解析 txt/md 并登记到会话状态。 */
    private final DocumentUploadService documentUploadService;

    @PostMapping("/completions")
    @Operation(summary = "同步对话：一次性返回大模型完整回复")
    public RestResult<AgentChatVO> completions(@RequestBody @Valid AgentChatParam param) {
        return RestResult.ofData(agentChatService.chat(param));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话：以 SSE 类型化事件逐段返回思考过程与回复")
    public Flux<AgentChatStreamVO> stream(@RequestBody @Valid AgentChatParam param) {
        return agentChatService.chatStream(param);
    }

    /** NDJSON 行序列化：与 SSE 编码器一致省略 null 字段，前端按行 JSON.parse。 */
    private static final ObjectMapper NDJSON_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @PostMapping(value = "/stream_ndjson", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "流式对话（NDJSON）：HTTP chunked 每行一个 JSON 事件，事件结构与 /chat/stream 完全一致，非 SSE")
    public ResponseEntity<ResponseBodyEmitter> streamNdjson(@RequestBody @Valid AgentChatParam param) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);
        // 写失败（客户端断开）时在 onNext 内直接取消上游，避免抛异常产生 reactor onErrorDropped 噪音日志
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        Disposable subscription = agentChatService.chatStream(param).subscribe(
                event -> {
                    try {
                        // 以 UTF-8 字节写出，避免字符串转换器默认 ISO-8859-1 破坏中文
                        emitter.send((NDJSON_MAPPER.writeValueAsString(event) + "\n").getBytes(StandardCharsets.UTF_8),
                                MediaType.APPLICATION_NDJSON);
                    } catch (IOException | IllegalStateException e) {
                        Disposable d = subscriptionRef.get();
                        if (d != null) {
                            d.dispose();
                        }
                    }
                },
                error -> {
                    try {
                        emitter.send((NDJSON_MAPPER.writeValueAsString(AgentChatStreamVO.error(error.getMessage())) + "\n")
                                .getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_NDJSON);
                    } catch (IOException | IllegalStateException ignored) {
                        // 客户端已断开，无需再写
                    }
                    emitter.complete();
                },
                emitter::complete);
        subscriptionRef.set(subscription);
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(t -> subscription.dispose());
        return ResponseEntity.ok()
                // 告知 Nginx 等反向代理关闭响应缓冲，否则流会被攒成一次性输出
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(emitter);
    }

    @PostMapping("/clear_context")
    @Operation(summary = "清空会话上下文：清除多轮记忆与对象构建状态（用于“新对话/清空”）")
    public RestResult<Boolean> clearContext(@RequestParam String sessionId) {
        conversationResetService.reset(sessionId);
        return RestResult.ofData(Boolean.TRUE);
    }

    @PostMapping("/abort")
    @Operation(summary = "中断对话：取消当前正在进行的流式回复（前端“停止”按钮）")
    public RestResult<Boolean> abort(@RequestParam String sessionId) {
        return RestResult.ofData(agentChatService.abort(sessionId));
    }

    @PostMapping(value = "/upload_document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传会话参考文档（对象构建第2步导入资料，仅支持 txt/md，重复上传以最新一份为准）")
    public RestResult<DocumentUploadVO> uploadDocument(@RequestParam("file") MultipartFile file,
                                                       @RequestParam String sessionId) {
        return RestResult.ofData(documentUploadService.upload(file, sessionId));
    }
}
