package com.aircas.ptr.foundry.agent.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * Jackson Record 序列化修复。
 *
 * <p>Spring AI 内部的 {@code ChatCompletionRequest} 等类型是 Java Record，
 * 需要 Jackson 的 {@link ParameterNamesModule} 才能正确序列化（否则输出 {@code {}}，
 * 导致模型服务端报 "type missing / body field required"）。</p>
 *
 * <p>本配置通过 {@link RestClientCustomizer} 替换 {@code RestClient.Builder} 中的
 * {@link MappingJackson2HttpMessageConverter}，强制使用注册了 {@link ParameterNamesModule}
 * 的 {@link ObjectMapper}，确保 Record 类型序列化正常。此修复对 Spring AI 内部创建的
 * {@code RestClient} 同样生效（因为 Spring AI 复用 auto-configured 的 {@code RestClient.Builder}）。</p>
 *
 * <p><b>离线部署注意</b>：需确保离线 Maven 仓库包含 {@code jackson-module-parameter-names} jar。
 * 该 jar 是 {@code jackson-databind} 的传递依赖，通常不会缺失；若确实缺失，启动时会报
 * {@link ClassNotFoundException}，从离线仓库补拷即可。</p>
 */
@Configuration
public class AiJacksonRecordFixConfig {

    private static final Logger log = LoggerFactory.getLogger(AiJacksonRecordFixConfig.class);

    /**
     * 构建支持 Java Record 序列化的 ObjectMapper。
     * 注册 ParameterNamesModule 使 Jackson 能通过构造器参数名（而非 JavaBean getter）识别 Record 字段。
     */
    private static ObjectMapper recordAwareObjectMapper() {
        return JsonMapper.builder()
                .addModule(new ParameterNamesModule())
                .build();
    }

    /**
     * 替换 RestClient.Builder 中的 MappingJackson2HttpMessageConverter，
     * 使用支持 Record 序列化的 ObjectMapper。
     */
    @Bean
    public RestClientCustomizer recordSerializationRestClientCustomizer() {
        return builder -> {
            ObjectMapper recordMapper = recordAwareObjectMapper();
            MappingJackson2HttpMessageConverter recordConverter =
                    new MappingJackson2HttpMessageConverter(recordMapper);

            // RestClient.Builder.messageConverters(Consumer) 可修改内部 converter 列表
            builder.messageConverters(customConverters -> {
                // 移除默认的 MappingJackson2HttpMessageConverter，替换为 Record 感知版本
                customConverters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                customConverters.add(recordConverter);
                log.info("[AI-Jackson-Fix] 已替换 MappingJackson2HttpMessageConverter 为 Record 感知版本");
            });
        };
    }
}
