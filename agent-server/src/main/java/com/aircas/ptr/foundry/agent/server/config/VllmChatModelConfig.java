package com.aircas.ptr.foundry.agent.server.config;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 自定义 ChatModel：绕过 Spring AI 自动配置，直接控制参数，
 * 避免发送 vLLM 不支持的字段（如 stream_options）导致 400 Bad Request。
 *
 * <p>Spring AI 2.0 起 OpenAI 模块改用官方 OpenAI Java SDK（OkHttp 传输），
 * 原 {@code OpenAiApi}（RestClient/WebClient）已移除；这里用 {@link SpringAiOpenAiHttpClient}
 * 构建 {@link OpenAIClient}，同步/流式共用同一客户端。</p>
 */
@Configuration
public class VllmChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(VllmChatModelConfig.class);

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash-0731}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double temperature;

    @Bean
    @Primary
    public ChatModel vllmChatModel() {
        OpenAIClient client = new OpenAIClientImpl(ClientOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .httpClient(SpringAiOpenAiHttpClient.builder().build())
                .build());

        log.info("[VllmChatModel] 已创建自定义 OpenAIClient，base-url={}", baseUrl);

        return OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(client.async())
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        // vLLM 不支持 stream_options，显式关闭 usage 统计避免 400
                        .streamUsage(false)
                        .build())
                .build();
    }
}