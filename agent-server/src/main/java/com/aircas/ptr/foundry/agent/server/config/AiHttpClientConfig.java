package com.aircas.ptr.foundry.agent.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * 将 Spring AI 内部 RestClient 的请求工厂替换为 SimpleClientHttpRequestFactory，
 * 解决内网部署的大模型 HTTP 服务器对 chunked encoding / 连接管理不标准导致的调用失败。
 * <p>
 * 默认 JdkClientHttpRequestFactory 对 HTTP 协议处理更严格，
 * 内网模型（vLLM / Ollama 等）的 HTTP 服务器实现不够标准时会报错或挂起；
 * SimpleClientHttpRequestFactory 底层用 HttpURLConnection，容忍度更高。
 * </p>
 */
@Configuration
public class AiHttpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiHttpClientConfig.class);

    @Bean
    public RestClientCustomizer simpleHttpClientRestClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            builder.requestFactory(factory);
            log.info("[AI-HTTP-Client] 已将 RestClient 请求工厂切换为 SimpleClientHttpRequestFactory");
        };
    }
}
