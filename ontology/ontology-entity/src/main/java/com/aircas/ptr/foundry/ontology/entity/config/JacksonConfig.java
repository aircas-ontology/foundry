package com.aircas.ptr.foundry.ontology.entity.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        
        // 注册自定义的Long类型反序列化器，处理ISO 8601格式的日期时间字符串
        module.addDeserializer(Long.class, new JsonDeserializer<Long>() {
            @Override
            public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String value = p.getValueAsString();
                if (value == null || value.isEmpty()) {
                    return null;
                }
                
                try {
                    // 尝试将值解析为Long
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    // 如果不是数字，尝试将其解析为日期时间，然后转换为时间戳
                    try {
                        ZonedDateTime dateTime = ZonedDateTime.parse(value);
                        return dateTime.toInstant().toEpochMilli();
                    } catch (DateTimeParseException ex) {
                        throw new IOException("无法将值 '" + value + "' 解析为Long或日期时间", ex);
                    }
                }
            }
        });
        
        objectMapper.registerModule(module);
        return objectMapper;
    }
} 