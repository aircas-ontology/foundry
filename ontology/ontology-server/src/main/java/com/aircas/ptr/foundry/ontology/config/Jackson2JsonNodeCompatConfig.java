package com.aircas.ptr.foundry.ontology.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot4 的 HTTP 序列化器是 Jackson3，不认识 Jackson2 的 JsonNode：
 * 序列化会把它当普通 bean 输出（{"array":false,...}），反序列化因 JsonNode 是抽象类直接失败。
 * 给 Jackson3 注册桥接模块：序列化按原始 JSON 写出，反序列化先读 Jackson3 树再转 Jackson2 树。
 * 模块逻辑见 {@link Jackson2JsonNodeCompatModule}（同时经 SPI 注册覆盖 Spring AI 内置 mapper）。
 */
@Configuration
public class Jackson2JsonNodeCompatConfig {

    @Bean
    public JsonMapperBuilderCustomizer jackson2JsonNodeCompatCustomizer() {
        return builder -> builder.addModule(new Jackson2JsonNodeCompatModule());
    }
}