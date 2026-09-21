package com.aircas.ptr.foundry.ontology.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Server 配置。
 *
 * <p>通过 {@link McpTools} 标记接口自动收集容器内所有工具 Bean（{@code OntologyMetaMcpTools}、
 * {@code OntologyGroupMcpTools}、{@code OntologyCategoryMcpTools}、{@code DatasourceMcpTools} 等），
 * 交给 {@link MethodToolCallbackProvider} 反射扫描其 {@code @Tool} 方法，构成 {@link ToolCallbackProvider}；
 * Spring AI MCP Server 自动配置据此生成 {@code tools/list} 能力清单，供 agent-server 启动时动态发现。
 * SSE 端点由 {@code spring-ai-starter-mcp-server-webmvc} 自动注册（默认 {@code /sse} 建连、
 * {@code /mcp/message} 收发消息）。</p>
 *
 * <p>扩展约定：新增工具类只需 {@code implements McpTools} 并加 {@code @Component}，无需修改本配置。</p>
 *
 * <p>同时注册 {@link McpServiceTokenFilter}，仅对 {@code /sse} 建连做服务令牌校验（服务级信任）。
 * 过滤器不声明为 {@code @Component}，改由本 {@link FilterRegistrationBean} 精确绑定 URL，避免被
 * Spring Boot 自动注册到 {@code /*} 造成重复拦截。</p>
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(List<McpTools> mcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpTools.toArray())
                .build();
    }

    @Bean
    public FilterRegistrationBean<McpServiceTokenFilter> mcpServiceTokenFilterRegistration(
            @Value("${mcp.service-token:}") String serviceToken) {
        FilterRegistrationBean<McpServiceTokenFilter> registration =
                new FilterRegistrationBean<>(new McpServiceTokenFilter(serviceToken));
        registration.addUrlPatterns("/sse");
        registration.setName("mcpServiceTokenFilter");
        registration.setOrder(1);
        return registration;
    }
}
