package com.aircas.ptr.foundry.ontology.config;

import com.aircas.ptr.foundry.ontology.intercepter.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/function/callback",
                        "/user/login",
                        "/user/create",
                        "/ai/**",
                        // Agent 工具接口：供 agent-server 服务间内部调用，暂免 JWT。
                        // 生产环境应加固：改为透传用户 JWT，或增加服务间共享密钥/网关鉴权，避免工具接口裸露。
                        "/tool/**",
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/error",
                        "/favicon.ico"
                );
    }
}
