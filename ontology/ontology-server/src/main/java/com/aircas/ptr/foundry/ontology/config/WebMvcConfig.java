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
                        "/doc.html",
                        "/swagger-resources/**",
                        "/v2/api-docs",
                        "/v2/api-docs/**",
                        "/webjars/**",
                        "/error",
                        "/ontology/favicon.ico"
                );
    }
}
