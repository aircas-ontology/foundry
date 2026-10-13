package com.aircas.ptr.foundry.ontology.config;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语言 Runner 容器地址配置。
 * <p>
 * 每种语言对应一个独立 runner（{@code language → base URL} 严格一对一），
 * 地址写在 application.yml（占位）+ application-local.yml（本地覆盖），风格对齐既有 {@code xxl.job}/{@code rag} 等配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "script-runner")
public class ScriptRunnerProperties {

    /**
     * groovy-runner 基地址
     */
    private String groovy;

    /**
     * java-runner 基地址
     */
    private String java;

    /**
     * python-runner 基地址
     */
    private String python;

    /**
     * ts-runner 基地址
     */
    private String typescript;

    /**
     * 调用级超时（毫秒）
     */
    private int timeoutMs = 5000;

    /**
     * 取指定语言 runner 的基地址；未配置则视为运行时不可用。
     */
    public String baseUrl(FunctionLanguageEnum language) {
        String url = switch (language) {
            case GROOVY -> groovy;
            case JAVA -> java;
            case PYTHON -> python;
            case TYPESCRIPT -> typescript;
        };
        if (url == null || url.isBlank()) {
            throw new BusinessException("语言运行时未配置：" + language.getCode());
        }
        return trimTrailingSlash(url);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

}
