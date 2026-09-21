package com.aircas.ptr.foundry.agent.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent 服务启动类。
 *
 * <p>职责：集成大模型（DeepSeek，OpenAI 兼容接口）对话能力，并将 ontology-server 暴露的原子操作
 * 包装为 Spring AI {@code @Tool} 工具，由大模型按需编排调用。ontology-server 只负责原子操作，
 * 本服务负责 Agent 编排与对话入口。</p>
 */
@SpringBootApplication
public class AgentServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentServerApplication.class, args);
    }
}
