package com.aircas.ptr.foundry.agent.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置：增大 SSE 缓冲区大小，避免 MCP 工具列表等大数据包触发 DataBufferLimitException。
 *
 * <p>Spring WebFlux 默认缓冲区上限为 256KB，ontology-server 的 MCP 工具列表在空域/数据源较多时
 * 可能超过该限制，导致 SSE 连接中断。本配置将上限提升至 16MB。</p>
 */
@Configuration
public class WebClientConfig {

    /**
     * 自定义 WebClient.Builder，供 Spring AI MCP Client 的 WebFluxSseClientTransport 使用。
     *
     * <p>通过 {@link ExchangeStrategies} 调高 {@code maxInMemorySize}，使 SSE 长消息不被截断。</p>
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // 16MB，覆盖超大规模工具列表场景（ontology-server 返回数据可能超过 2MB）
        int maxInMemorySize = 16 * 1024 * 1024;

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    ClientCodecConfigurer clientCodecConfigurer = configurer;
                    clientCodecConfigurer.defaultCodecs().maxInMemorySize(maxInMemorySize);
                })
                .build();

        return WebClient.builder()
                .exchangeStrategies(strategies);
    }
}
