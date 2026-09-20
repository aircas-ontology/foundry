package com.aircas.ptr.foundry.agent.server.config;

import com.aircas.ptr.foundry.agent.server.tool.HttpOntologyQueryTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置。
 *
 * <p>基于阿里云百炼（DashScope）自动装配的 {@link ChatClient.Builder} 构建对话客户端，
 * 并通过 {@code defaultTools} 注册本体查询工具，使大模型具备按需调用 ontology-server
 * 原子操作的能力（Function Calling / Tool Calling）。</p>
 *
 * <p>模型与密钥由 {@code application.yml} 的 {@code spring.ai.dashscope.*} 配置，
 * 与 ontology-server 保持一致（默认 qwen-flash，密钥走环境变量）。</p>
 */
@Configuration
public class ChatClientConfig {

    /**
     * 默认系统提示词：约束助手角色与回答风格。
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是 Foundry 本体平台的智能助手，负责帮助用户查询和理解本体（Ontology）知识图谱、
            本体元数据、分组等信息。

            工作原则：
            1. 当用户的问题需要真实数据时，主动调用可用的工具（如搜索本体、查询本体详情、查询空间分组）获取，
               不要凭空编造本体名称、数量或标识。
            2. 工具返回结果后，用简洁、准确的中文总结回答；涉及数量、标识等关键信息时如实呈现。
            3. 若工具返回为空或调用失败，明确告知用户未查询到相关数据，而不是虚构内容。
            4. 与本体数据无关的闲聊，正常友好回答即可。
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, HttpOntologyQueryTools ontologyQueryTools) {
        return builder
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultTools(ontologyQueryTools)
                .build();
    }
}
