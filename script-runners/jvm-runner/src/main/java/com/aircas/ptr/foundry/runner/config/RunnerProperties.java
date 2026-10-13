package com.aircas.ptr.foundry.runner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runner 配置：{@code runner.language} 决定本容器服务哪种 JVM 语言（groovy / java）。
 * 同一份镜像通过环境变量注入不同 language，部署为 groovy-runner 与 java-runner 两个独立容器。
 */
@Data
@ConfigurationProperties(prefix = "runner")
public class RunnerProperties {

    /**
     * 本容器服务的语言：groovy 或 java
     */
    private String language = "groovy";

    /**
     * 单次执行超时（毫秒）
     */
    private long timeoutMs = 5000;

}
