package com.aircas.ptr.foundry.agent.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;


@Configuration
public class AiHttpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiHttpClientConfig.class);

    @Bean
    public RestClientCustomizer simpleHttpClientRestClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            builder.requestFactory(factory);
            log.info("[AI-HTTP-CLIENT] 已将 RestClient 请求工厂切换为 SimpleClientHttpRequestFactory");
        };
    }
}