//package com.aircas.ptr.foundry.ontology.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import springfox.documentation.builders.PathSelectors;
//import springfox.documentation.builders.RequestHandlerSelectors;
//import springfox.documentation.spi.DocumentationType;
//import springfox.documentation.spring.web.plugins.Docket;
//
//@Configuration
//public class SwaggerConfig {
//
//    @Bean
//    public Docket api() {
//        return new Docket(DocumentationType.SWAGGER_2)
//                .select()
//                .apis(RequestHandlerSelectors.basePackage("com.aircas.ptr.foundry.ontology.userinterface.controller"))
//                .paths(PathSelectors.any())
//                .build();
//    }
//
////    private ApiInfo apiInfo() {
////        return new ApiInfoBuilder()
////                .title("Data Manager Api-dhsp-Server RESTful API Document")
////                .description("REST API powered by Swagger2")
////                .version("1.0.0")
////                .build();
////    }
//}
//
