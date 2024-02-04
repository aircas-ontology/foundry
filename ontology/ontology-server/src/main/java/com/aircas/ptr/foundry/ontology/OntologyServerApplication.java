package com.aircas.ptr.foundry.ontology;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import tk.mybatis.mapper.autoconfigure.MapperAutoConfiguration;

@SpringBootApplication(scanBasePackages = "com.aircas.ptr", exclude = {DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class, MapperAutoConfiguration.class})
public class OntologyServerApplication {


    public static void main(String[] args) {
        SpringApplication.run(OntologyServerApplication.class, args);
    }

}
