package com.aircas.ptr.foundry.agent.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置：增大 SSE 缓冲区大小 + 流式请求调试日志。
 *
 * <p>Spring WebFlux 默认缓冲区上限为 256KB，ontology-server 的 MCP 工具列表在空域/数据源较多时
 * 可能超过该限制，导致 SSE 连接中断。本配置将上限提升至 16MB。</p>
 *
 * <p>同时注入 {@link ExchangeFilterFunction}，在 DEBUG 级别打印流式请求的实际 URL 与响应状态，
 * 配合 {@link AiRequestLoggingConfig}（同步 RestClient 拦截）实现全链路可观测。
 * 仅在 {@code ai.http.wire=DEBUG} 时生效。</p>
 */
@Configuration
public class WebClientConfig {

    private static final Logger wireLog = LoggerFactory.getLogger("ai.http.wire");

    /**
     * 自定义 WebClient.Builder，供 Spring AI 流式调用与 MCP Client 的 WebFlux SSE 传输使用。
     *
     * <p>通过 {@link ExchangeStrategies} 调高 {@code maxInMemorySize}，使 SSE 长消息不被截断。
     * 通过 {@link ExchangeFilterFunction} 在 DEBUG 级别打印请求 URL 与响应状态。</p>
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
                .exchangeStrategies(strategies)
                .filter(streamLoggingFilter());
    }

    /**
     * 流式请求日志过滤器：打印实际请求 URL（排查双 /v1 拼接）与响应状态/错误。
     * 仅在 {@code ai.http.wire=DEBUG} 时生效，非 DEBUG 级别零开销。
     */
    private ExchangeFilterFunction streamLoggingFilter() {
        return (request, next) -> {
            if (!wireLog.isDebugEnabled()) {
                return next.exchange(request);
            }
            wireLog.debug("~~~> {} {} (stream)", request.method(), request.url());
            return next.exchange(request)
                    .doOnSuccess(response -> wireLog.debug("<~~~ {} {} (stream)",
                            response.statusCode(), request.url()))
                    .doOnError(e -> wireLog.error("<~~~ ERROR {} : {}",
                            request.url(), e.getMessage()));
        };
    }
}
