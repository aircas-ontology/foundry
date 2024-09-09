package com.aircas.ptr.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan({"com.aircas.ptr.api.mapper"})
@SpringBootApplication(scanBasePackages={"com.aircas.ptr.api","com.aircas.ptr.foundry.common"})
public class ApiApplication {

    public static ApplicationContext context;

    public static void main(String[] args) {
        context = SpringApplication.run(ApiApplication.class, args);
    }
}
