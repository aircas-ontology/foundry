package com.aircas.ptr.foundry.ontology.entity;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = "com.aircas.ptr.foundry.ontology.entity", exclude = {DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class})
public class EntityApplication {
    public static void main(String[] args) {
        SpringApplication.run(EntityApplication.class, args);
    }
} 