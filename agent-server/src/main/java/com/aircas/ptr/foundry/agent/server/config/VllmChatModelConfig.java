package com.aircas.ptr.foundry.agent.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 自定义 ChatModel 和 OpenAiApi：绕过 Spring AI 自动配置，直接控制参数，
 * 避免发送 vLLM 不支持的字段（如 stream_options）导致 400 Bad Request。
 *
 * <p>同步调用走 RestClient，流式调用走 WebClient，两者都需要正确配置。</p>
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
    public OpenAiApi vllmOpenAiApi(WebClient.Builder webClientBuilder, RestClient.Builder restClientBuilder) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .webClientBuilder(webClientBuilder)
                .restClientBuilder(restClientBuilder)
                .build();

        log.info("[VllmChatModel] 已创建自定义 OpenAiApi，base-url={}", baseUrl);
        return api;
    }

    @Bean
    @Primary
    public ChatModel vllmChatModel(OpenAiApi openAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .build())
                .build();
    }
}
