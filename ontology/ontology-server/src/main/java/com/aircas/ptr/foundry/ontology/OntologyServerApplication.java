package com.aircas.ptr.foundry.ontology;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.aircas.ptr")
public class OntologyServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OntologyServerApplication.class, args);
    }

}
