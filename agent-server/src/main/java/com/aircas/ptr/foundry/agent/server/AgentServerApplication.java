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
// 显式声明扫描范围：默认仅扫本包（agent.server），此处追加 common.config 以复用全局 CORS 配置
// （CorsConfig 为 WebMvcConfigurer，与 agent 的 servlet 栈匹配，且已 exposedHeaders access-token/refresh-token）。
// 刻意不扫 common.filter：其 LoggingFilter 会缓存响应体、打断 /chat/stream 的 SSE 流。
@SpringBootApplication(scanBasePackages = {
        "com.aircas.ptr.foundry.agent.server",
        "com.aircas.ptr.foundry.common.config"
})
public class AgentServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentServerApplication.class, args);
    }
}
