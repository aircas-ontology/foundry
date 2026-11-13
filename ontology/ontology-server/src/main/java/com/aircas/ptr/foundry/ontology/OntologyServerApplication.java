package com.aircas.ptr.foundry.ontology;

import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = "com.aircas.ptr.foundry", exclude = {
        DataSourceAutoConfiguration.class,
        DruidDataSourceAutoConfigure.class
})
@EnableKafka
public class OntologyServerApplication {

    public static ApplicationContext context;

    public static void main(String[] args) {
        context = SpringApplication.run(OntologyServerApplication.class, args);
    }

}
