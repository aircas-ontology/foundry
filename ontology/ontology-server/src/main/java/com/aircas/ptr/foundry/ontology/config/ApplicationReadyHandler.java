package com.aircas.ptr.foundry.ontology.config;

import com.aircas.ptr.foundry.ontology.GroovyClassLoaderManager;
import groovy.lang.GroovyClassLoader;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

//@Component
class ApplicationReadyHandler implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        GroovyClassLoader classLoader = GroovyClassLoaderManager.getParentClassLoader();
        String content = null;
        try {
            content = readResourceContent("groovy/Ontology.groovy");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Ontology.groovy is not parsed");
        }
        classLoader.parseClass(content, "Ontology");

        try {
            content = readResourceContent("groovy/OntologBaseObject.groovy");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Ontology.OntologBaseObject is not parsed");
        }
        System.out.println(content);
        classLoader.parseClass(content, "OntologBaseObject");

        try {
            content = readResourceContent("groovy/FunctionProxy.groovy");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Ontology.BaseFunction is not parsed");
        }
        System.out.println(content);
        classLoader.parseClass(content, "BaseFunction");
    }

    private String readResourceContent(String path) throws IOException {
        Resource resource = new ClassPathResource(path);
        InputStream inputStream = resource.getInputStream();
        String content = new String(FileCopyUtils.copyToByteArray(inputStream), StandardCharsets.UTF_8);
        inputStream.close();
        return content;
    }
}
