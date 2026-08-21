package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "threadpool")
public class TaskExecutorProperties {

    /**
     * 核心线程数
     */
    private int corePoolSize = 10;

    /**
     * 最大线程数
     */
    private int maxPoolSize = 20;

    /**
     * 有界队列容量
     */
    private int queueCapacity = 100;

    private int keepAliveSeconds = 60;

    private String threadNamePrefix = "ontology-thread-";

}
