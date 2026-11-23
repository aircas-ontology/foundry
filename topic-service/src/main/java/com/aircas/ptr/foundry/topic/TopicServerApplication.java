package com.aircas.ptr.foundry.topic;

import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * 主题服务启动类。
 *
 * <p>数据源由 {@code DataSourceConfiguration} 手工装配（与 ontology-server 主库方式一致），
 * 因此关闭 Spring Boot / Druid 的数据源自动配置。父 POM 传递的 Elasticsearch
 * 本服务不使用，一并排除。扫描 {@code com.aircas.ptr.foundry} 以复用 common 的
 * 统一响应、全局异常、CORS 与请求日志。</p>
 */
@SpringBootApplication(scanBasePackages = "com.aircas.ptr.foundry", exclude = {
        DataSourceAutoConfiguration.class,
        DruidDataSourceAutoConfigure.class,
        ElasticsearchRestClientAutoConfiguration.class,
        ElasticsearchClientAutoConfiguration.class,
        DataElasticsearchRepositoriesAutoConfiguration.class
})
public class TopicServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TopicServerApplication.class, args);
    }
}
