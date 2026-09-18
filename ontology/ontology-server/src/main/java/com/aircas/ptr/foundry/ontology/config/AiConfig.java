package com.aircas.ptr.foundry.ontology.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置类
 * 配置 ChatClient 用于与阿里云百炼（Model Studio）上的大模型交互，
 * 当前默认模型为 qwen-flash（见 application.yml: spring.ai.dashscope.chat.options.model）。
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个智能本体管理助手，可以帮助用户查询和管理本体知识图谱、实体、属性等信息。请用中文回答。")
                .build();
    }
}
