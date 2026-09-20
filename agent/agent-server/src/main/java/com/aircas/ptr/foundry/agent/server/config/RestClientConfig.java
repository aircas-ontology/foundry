package com.aircas.ptr.foundry.agent.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient 配置。
 *
 * <p>构建指向 ontology-server 的 {@link RestClient}，供工具适配器调用其暴露的原子操作
 * （{@code /tool/query/**}）。基础地址与超时由 {@link AgentProperties} 提供。</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient ontologyRestClient(AgentProperties agentProperties) {
        AgentProperties.Ontology ontology = agentProperties.getOntology();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ontology.getConnectTimeout());
        requestFactory.setReadTimeout(ontology.getReadTimeout());

        return RestClient.builder()
                .baseUrl(ontology.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
