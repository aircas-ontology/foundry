package com.aircas.ptr.foundry.ontology.entity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement  // 启用事务管理
public class EntityApplication {
    public static void main(String[] args) {
        SpringApplication.run(EntityApplication.class, args);
    }
} 