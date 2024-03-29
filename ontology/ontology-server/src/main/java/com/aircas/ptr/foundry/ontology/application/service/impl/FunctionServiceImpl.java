package com.aircas.ptr.foundry.ontology.application.service.impl;


import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import groovy.lang.GroovyObject;
import lombok.RequiredArgsConstructor;
import groovy.lang.GroovyClassLoader;

import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;


@Service
@RequiredArgsConstructor

public class FunctionServiceImpl implements FunctionService {

    GroovyClassLoader classLoader = new GroovyClassLoader();

    @Override
    public Object handle(String path, HashMap<String, Object> parameters) {
        Object result = null;
        try {
            classLoader.parseClass(new File("D:\\weilong(1)\\workplaces\\foundry2\\ontology\\ontology-server\\src\\main\\java\\com\\aircas\\ptr\\foundry\\ontology\\function\\Ontology.groovy"));
            Class groovyClass = classLoader.parseClass(new File(path));
            GroovyObject groovyObject = (GroovyObject)groovyClass.newInstance();

            result = groovyObject.invokeMethod("handle", parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
