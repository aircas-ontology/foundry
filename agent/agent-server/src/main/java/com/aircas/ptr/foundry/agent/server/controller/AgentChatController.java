package com.aircas.ptr.foundry.agent.server.controller;

import com.aircas.ptr.foundry.agent.server.model.param.AgentChatParam;
import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatVO;
import com.aircas.ptr.foundry.common.base.RestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Agent 对话入口。
 *
 * <p>对外提供两类对话接口：同步（一次性返回完整回复）与流式（SSE 逐字返回）。
 * 大模型在对话过程中会按需调用已注册的本体查询工具（见 {@code HttpOntologyQueryTools}），
 * 由 Spring AI 自动完成"工具发现 → 调用 → 结果合并 → 循环决策"。</p>
 */
@Tag(name = "Agent 对话")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Validated
public class AgentChatController {

    private final ChatClient chatClient;

    @PostMapping("/completions")
    @Operation(summary = "同步对话：一次性返回大模型完整回复")
    public RestResult<AgentChatVO> completions(@RequestBody @Valid AgentChatParam param) {
        String reply = chatClient.prompt()
                .user(param.getMessage())
                .call()
                .content();
        return RestResult.ofData(AgentChatVO.builder().reply(reply).build());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话：以 SSE 逐段返回大模型回复")
    public Flux<String> stream(@RequestParam("message") String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }
}
