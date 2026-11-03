package com.aircas.ptr.foundry.agent.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * AI 请求调试日志拦截器。
 *
 * <p>通过 {@link RestClientCustomizer} 向 Spring AI 内部使用的 {@code RestClient.Builder}
 * 注入 HTTP 拦截器，在 DEBUG 级别打印发往大模型的完整请求体（model、messages、tools 等参数）
 * 以及响应体，方便排查 400 等错误。</p>
 *
 * <p>仅在 {@code application-local.yml} 中配置 {@code logging.level.ai.http.wire=DEBUG} 时生效。</p>
 */
@Configuration
public class AiRequestLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger("ai.http.wire");

    @Bean
    public RestClientCustomizer aiLoggingRestClientCustomizer() {
        return builder -> builder.requestInterceptor(new AiWireLoggingInterceptor());
    }

    /**
     * 拦截 RestClient 请求，DEBUG 级别打印完整请求体与响应体。
     */
    private static class AiWireLoggingInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            if (!log.isDebugEnabled()) {
                return execution.execute(request, body);
            }

            String requestBody = new String(body, StandardCharsets.UTF_8);
            log.debug(">>> {} {}  content-length={}", request.getMethod(), request.getURI(), body.length);
            log.debug(">>> Headers: {}", request.getHeaders());
            // 请求体可能很长（含完整 system prompt + messages），截断到 8000 字符防止日志爆炸
            if (requestBody.length() > 8000) {
                log.debug(">>> Body (truncated, total={}): {}...", requestBody.length(), requestBody.substring(0, 8000));
            } else {
                log.debug(">>> Body: {}", requestBody);
            }

            long start = System.currentTimeMillis();
            ClientHttpResponse response = execution.execute(request, body);
            long elapsed = System.currentTimeMillis() - start;

            log.debug("<<< {} {}  elapsed={}ms", response.getStatusCode(), request.getURI(), elapsed);
            log.debug("<<< Headers: {}", response.getHeaders());
            // 响应体通常较短（模型回复），全量打印
            String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("<<< Body: {}", responseBody);

            // 响应体已被消费，需要重新包装回去让上层正常读取
            return new BufferingClientHttpResponse(response, responseBody);
        }
    }

    /**
     * 将已读取的响应体重新包装回 {@link ClientHttpResponse}，避免上层因流已消费而拿不到数据。
     */
    private static class BufferingClientHttpResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final byte[] bufferedBody;

        BufferingClientHttpResponse(ClientHttpResponse delegate, String body) {
            this.delegate = delegate;
            this.bufferedBody = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public java.io.InputStream getBody() throws IOException {
            return new java.io.ByteArrayInputStream(bufferedBody);
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
