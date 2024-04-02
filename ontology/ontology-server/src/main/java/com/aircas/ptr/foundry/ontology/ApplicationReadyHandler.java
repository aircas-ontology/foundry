package com.aircas.ptr.foundry.ontology;

import groovy.lang.GroovyClassLoader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
class ApplicationReadyHandler implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        GroovyClassLoader classLoader = GroovyClassLoaderManager.getParentClassLoader();
        Class XMTB = classLoader.parseClass("package com.aircas.ptr.foundry.ontology; class XTMB  extends Object {}");
        Class XMTB2 =  classLoader.parseClass("package com.aircas.ptr.foundry.ontology; class XTMB2  extends XTMB {}");
        String content = null;
        try {
            content = readResourceContent("groovy/Ontology.groovy");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Ontology.groovy is not parsed");
        }
        System.out.println(content);
        classLoader.parseClass(content, "Ontology");
    }

    private String readResourceContent(String path) throws IOException {
        Resource resource = new ClassPathResource(path);
        InputStream inputStream = resource.getInputStream();
        String content = new String(FileCopyUtils.copyToByteArray(inputStream), StandardCharsets.UTF_8);
        inputStream.close();
        return content;
    }
}
