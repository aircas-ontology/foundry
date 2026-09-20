package com.aircas.ptr.foundry.agent.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 服务配置属性，前缀 {@code agent}。
 *
 * <p>集中管理 Agent 对外部服务（ontology-server）的访问地址与超时等参数，
 * 避免在业务代码中硬编码 URL。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /**
     * ontology-server 访问配置。
     */
    private Ontology ontology = new Ontology();

    @Data
    public static class Ontology {

        /**
         * ontology-server 基础地址（含 context-path），例如 {@code http://127.0.0.1:37002/ontology}。
         * 工具适配器基于该地址拼接 {@code /tool/query/**} 调用原子操作。
         */
        private String baseUrl = "http://127.0.0.1:37002/ontology";

        /**
         * 连接超时（毫秒）。
         */
        private int connectTimeout = 5000;

        /**
         * 读取超时（毫秒）。大模型工具调用可能触发较重的查询，适当放宽。
         */
        private int readTimeout = 30000;
    }
}
