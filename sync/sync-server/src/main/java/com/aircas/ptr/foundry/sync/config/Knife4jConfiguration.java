package com.aircas.ptr.foundry.sync.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;

@Configuration
@EnableSwagger2WebMvc
public class Knife4jConfiguration {
    @Value("${ontology.swagger.enable:true}")
    private boolean enable;

    @Bean
    public Docket retrievalWebDocs() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .groupName("sync")
                .enable(enable)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.aircas.ptr.foundry.sync.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("同步服务")
                .description("中国科学院空天信息创新研究院")
                .version("1.0.0")
                .build();
    }
}
