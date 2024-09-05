package com.aircas.ptr.foundry.rule.executor;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import tk.mybatis.mapper.autoconfigure.MapperAutoConfiguration;
import tk.mybatis.spring.annotation.MapperScan;

@EnableScheduling
//@MapperScan({"com.aircas.ptr.foundry.rule.executor.mapper"})
@SpringBootApplication(scanBasePackages={"com.aircas.ptr.foundry.rule.executor","com.aircas.ptr.foundry.common"})
public class OntologyRuleExecutorApplication {

    public static ApplicationContext context;

    public static void main(String[] args) {
        context = SpringApplication.run(OntologyRuleExecutorApplication.class, args);
    }

}
