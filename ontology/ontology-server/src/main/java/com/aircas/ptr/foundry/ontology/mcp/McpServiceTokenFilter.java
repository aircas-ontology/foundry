package com.aircas.ptr.foundry.ontology.mcp;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;

/**
 * MCP 服务级信任令牌校验过滤器。
 *
 * <p>MCP 采用"服务级信任"鉴权：agent-server 作为受信内部服务，在建立 SSE 连接时通过查询参数
 * {@code token} 携带共享服务令牌（而非每用户 JWT）。因 MCP 的能力发现（握手 + {@code tools/list}）
 * 发生在 agent-server 启动时、无登录用户，故无法沿用每用户 JWT 门禁。</p>
 *
 * <p>之所以用 Servlet {@link Filter} 而非 {@code HandlerInterceptor}：MCP WebMVC 的 SSE 端点通过
 * {@code RouterFunction} 注册，拦截器对其是否生效存在 Spring 版本差异；Filter 处于容器层、对任何
 * 请求必然生效，最稳妥。仅拦截 SSE 建连端点 {@code /sse}；消息端点 {@code /mcp/message} 依赖建连时
 * 派生的 sessionId，无需重复校验。</p>
 */
public class McpServiceTokenFilter implements Filter {

    /** 服务令牌在查询参数 / 请求头中的名称。 */
    private static final String TOKEN_KEY = "token";

    private final String serviceToken;

    public McpServiceTokenFilter(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String token = req.getParameter(TOKEN_KEY);
        if (token == null || token.isBlank()) {
            token = req.getHeader(TOKEN_KEY);
        }

        if (serviceToken == null || serviceToken.isBlank() || !serviceToken.equals(token)) {
            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":401,\"message\":\"MCP 服务令牌无效\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
