package com.aircas.ptr.foundry.ontology.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 对话服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ChatClient chatClient;

    /**
     * 同步对话
     */
    public String chat(String userMessage) {
        log.info("AI Chat request: {}", userMessage);
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * 流式对话
     */
    public Flux<String> chatStream(String userMessage) {
        log.info("AI Chat stream request: {}", userMessage);
        return chatClient.prompt()
                .user(userMessage)
                .stream()
                .content();
    }
}
