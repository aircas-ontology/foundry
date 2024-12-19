package com.aircas.ptr.foundry.ontology;

import com.aircas.ptr.foundry.ontology.repository.param.FilterParam;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import com.alibaba.fastjson.JSON;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import tk.mybatis.mapper.autoconfigure.MapperAutoConfiguration;

@SpringBootApplication(scanBasePackages = "com.aircas.ptr", exclude = {DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class, MapperAutoConfiguration.class})
public class OntologyServerApplication {

    public static ApplicationContext context;

    public static void main(String[] args) {

        context = SpringApplication.run(OntologyServerApplication.class, args);
    }

}
