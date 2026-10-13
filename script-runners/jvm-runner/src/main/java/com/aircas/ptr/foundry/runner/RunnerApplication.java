package com.aircas.ptr.foundry.runner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.aircas.ptr.foundry.runner.config.RunnerProperties;

/**
 * JVM 语言脚本 Runner 启动类。
 * <p>
 * 一份代码通过 {@code runner.language}（groovy / java）分别部署为 groovy-runner 与 java-runner 两个独立容器，
 * 每个容器只服务一种语言。主应用（ontology-server）不执行任何用户代码，统一 HTTP 调本服务。
 */
@SpringBootApplication
@EnableConfigurationProperties(RunnerProperties.class)
public class RunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunnerApplication.class, args);
    }

}
