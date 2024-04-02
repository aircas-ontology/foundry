package com.aircas.ptr.foundry.ontology;

import groovy.lang.GroovyClassLoader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URL;

@Component
class ApplicationReadyHandler implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        GroovyClassLoader classLoader = GroovyClassLoaderManager.getParentClassLoader();
        URL resource = getClass().getClassLoader().getResource("groovy/Ontology.groovy");
        try {
            classLoader.parseClass(new File(resource.getPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
