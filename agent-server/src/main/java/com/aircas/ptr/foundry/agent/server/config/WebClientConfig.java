package com.aircas.ptr.foundry.agent.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * WebClient 配置：更换底层 HTTP 客户端 + 增大 SSE 缓冲区大小 + 流式请求调试日志。
 *
 * <p>Spring WebFlux 默认缓冲区上限为 256KB，ontology-server 的 MCP 工具列表在空域/数据源较多时
 * 可能超过该限制，导致 SSE 连接中断。本配置将上限提升至 16MB。</p>
 *
 * <p>底层 connector 默认为 Reactor Netty HttpClient，其对 chunked 传输编码的帧格式校验严格，
 * 内网模型服务器的非标准 HTTP 实现会导致流式（SSE）调用失败或挂起（同步路径已由
 * {@code AiHttpClientConfig} 切换 SimpleClientHttpRequestFactory 解决，但那只作用于 RestClient，
 * 管不到 WebClient）。此处参照同一思路将 connector 切换为 {@link JdkClientHttpConnector}
 * （JDK HttpClient，与 HttpURLConnection 同源，宽容度高）。必须显式强制 HTTP/1.1：
 * JDK HttpClient 默认会先尝试 HTTP/2 upgrade，非标准服务器同样可能失败。</p>
 *
 * <p>同时注入 {@link ExchangeFilterFunction}，在 DEBUG 级别打印流式请求的实际 URL 与响应状态，
 * 配合 {@link AiRequestLoggingConfig}（同步 RestClient 拦截）实现全链路可观测。
 * 仅在 {@code ai.http.wire=DEBUG} 时生效。</p>
 */
@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);
    private static final Logger wireLog = LoggerFactory.getLogger("ai.http.wire");

    /**
     * 自定义 WebClient.Builder，供 Spring AI 流式调用与 MCP Client 的 WebFlux SSE 传输使用。
     *
     * <p>通过 {@link JdkClientHttpConnector}（强制 HTTP/1.1）替换默认的 Reactor Netty connector，
     * 兼容内网非标准 HTTP 服务器。通过 {@link ExchangeStrategies} 调高 {@code maxInMemorySize}，
     * 使 SSE 长消息不被截断。通过 {@link ExchangeFilterFunction} 在 DEBUG 级别打印请求 URL 与响应状态。</p>
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        log.info("[AI-HTTP-Client] 已将 WebClient 底层 connector 切换为 JDK HttpClient（强制 HTTP/1.1）");

        // 16MB，覆盖超大规模工具列表场景（ontology-server 返回数据可能超过 2MB）
        int maxInMemorySize = 16 * 1024 * 1024;

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    ClientCodecConfigurer clientCodecConfigurer = configurer;
                    clientCodecConfigurer.defaultCodecs().maxInMemorySize(maxInMemorySize);
                })
                .build();

        return WebClient.builder()
                .clientConnector(new JdkClientHttpConnector(jdkHttpClient))
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
