package com.aircas.ptr.foundry.sync;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tk.mybatis.mapper.autoconfigure.MapperAutoConfiguration;

@SpringBootApplication(scanBasePackages = "com.aircas.ptr", exclude = {DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class, MapperAutoConfiguration.class})
@EnableScheduling
public class SyncServerApplication {
    public static void main(String[] args) {
        try {
            SpringApplication.run(SyncServerApplication.class, args);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
