package com.aircas.ptr.foundry.ontology.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson2 JsonNode 与 Jackson3 的桥接模块。
 * <p>除 {@link Jackson2JsonNodeCompatConfig} 显式注册到 HTTP 序列化器外，
 * 还经 META-INF/services/tools.jackson.databind.JacksonModule 以 SPI 注册：
 * Spring AI 的内置默认 JsonMapper（JacksonUtils.getDefaultJsonMapper，不走 Spring customizer）
 * 通过 ServiceLoader 发现本模块，从而 MCP 工具结果里的 Jackson2 JsonNode 也按原始 JSON 序列化，
 * 而不是被当普通 bean 输出 {"array":false,...} 垃圾结构。</p>
 */
public class Jackson2JsonNodeCompatModule extends SimpleModule {

    public Jackson2JsonNodeCompatModule() {
        super("jackson2-jsonnode-compat");
        addSerializer(JsonNode.class, new ValueSerializer<JsonNode>() {
            @Override
            public void serialize(JsonNode node, JsonGenerator gen, SerializationContext ctxt) {
                if (node == null) {
                    gen.writeNull();
                } else {
                    gen.writeRawValue(node.toString());
                }
            }
        });
        addDeserializer(JsonNode.class, new ValueDeserializer<JsonNode>() {
            private final ObjectMapper jackson2Mapper = new ObjectMapper();

            @Override
            @SneakyThrows
            public JsonNode deserialize(JsonParser p, DeserializationContext ctxt) {
                tools.jackson.databind.JsonNode tree = p.readValueAsTree();
                return jackson2Mapper.readTree(tree.toString());
            }
        });
    }
}