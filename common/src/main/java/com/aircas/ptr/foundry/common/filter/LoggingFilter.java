package com.aircas.ptr.foundry.common.filter;

import com.aircas.ptr.foundry.common.filter.CachableHttpServletRequest;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@Order(1)
public class LoggingFilter extends OncePerRequestFilter {

    public static final String LOG_ID_HEADER = "X-Request-ID";
    public static final String LOG_ID_KEY = "logId";


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String logId = IdGenerator.generateLogId();
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

