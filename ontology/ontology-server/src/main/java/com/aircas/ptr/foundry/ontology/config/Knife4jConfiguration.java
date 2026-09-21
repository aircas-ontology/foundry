package com.aircas.ptr.foundry.ontology.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfiguration {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("本体服务")
                        .description("中国科学院空天信息创新研究院")
                        .version("1.0.0"));
    }

    @Bean
    public GroupedOpenApi ontologyApi() {
        return GroupedOpenApi.builder()
                .group("ontology")
                .packagesToScan("com.aircas.ptr.foundry.ontology.controller")
                .build();
    }
}
