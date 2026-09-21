package com.aircas.ptr.foundry.common.filter;

import com.aircas.ptr.foundry.common.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@Slf4j
@Order(1)
public class LoggingFilter extends OncePerRequestFilter {

    public static final String LOG_ID_HEADER = "X-Request-ID";
    public static final String LOG_ID_KEY = "logId";

    private static final Set<String> SWAGGER_IGNORE_PATH = Set.of(
            "/webjars",
            "/swagger-ui",
            "/swagger-resources",
            "/v3/api-docs",
            "/doc.html"
    );

    /**
     * SSE / 流式端点：响应为长连接持续写出，若用 {@link ContentCachingResponseWrapper} 缓存响应体，
     * 会在异步处理开始时（{@code doFilter} 返回即触发 finally）就把响应 {@code copyBodyToResponse} 提前结束，
     * 导致后续经 SSE emitter 写出的数据（如 MCP 的 initialize/tools 响应）丢失、客户端超时。
     * 故这些端点直接透传，不做请求/响应体缓存。含 MCP Server 的 {@code /sse}、{@code /mcp/message}。
     */
    private static final Set<String> STREAMING_IGNORE_PATH = Set.of(
            "/sse",
            "/mcp/message"
    );

    /** SSE 响应内容类型：命中则同样跳过响应体缓存，兼容 agent 的 {@code /chat/stream} 等流式接口。 */
    private static final String TEXT_EVENT_STREAM = "text/event-stream";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var servletPath = request.getServletPath();
        var isSwaggerPath = SWAGGER_IGNORE_PATH.stream().anyMatch(v -> servletPath.startsWith(v));
        var isStreamingPath = STREAMING_IGNORE_PATH.stream().anyMatch(v -> servletPath.startsWith(v));

        var contentType = request.getContentType();
        var isMultipartRequest = contentType != null && contentType.toLowerCase().startsWith("multipart/");

        var accept = request.getHeader("Accept");
        var isSseRequest = accept != null && accept.toLowerCase().contains(TEXT_EVENT_STREAM);

        if (isSwaggerPath || isMultipartRequest || isStreamingPath || isSseRequest) {
            filterChain.doFilter(request, response);
            return;
        }

        String logId = request.getHeader(LOG_ID_HEADER);
        if (StringUtils.isEmpty(logId)) {
            logId = IdGenerator.generateLogId();
        }
        MDC.put(LOG_ID_KEY, logId);

        long startTime = System.currentTimeMillis();


        CachableHttpServletRequest requestWrapper = new CachableHttpServletRequest(request);
        this.logRequest(requestWrapper);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        responseWrapper.addHeader(LOG_ID_HEADER, logId);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            this.logResponse(responseWrapper, duration);
            responseWrapper.copyBodyToResponse();
            MDC.clear();
        }
    }

    private void logRequest(CachableHttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String queryString = request.getQueryString();
        String body = request.getBodyAsString();
        log.info("Request uri:{}, method:{}, query:{}, json body:{}", uri, method, queryString, body);
    }

    private void logResponse(ContentCachingResponseWrapper response, Long duration) {
        byte[] content = response.getContentAsByteArray();

        String body = null;
        if (content.length > 0) {
            try {
                body = new String(content, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.error("cannot decode response content", e);
            }
        }
        log.info("Response httpCode:{}, json body:{}, duration:{} ms", response.getStatus(), body, duration);
    }


}

