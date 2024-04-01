package com.aircas.ptr.foundry.ontology.application.service.impl;


import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.alibaba.fastjson.JSON;
import groovy.lang.GroovyObject;
import lombok.RequiredArgsConstructor;
import groovy.lang.GroovyClassLoader;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;


@Service
@RequiredArgsConstructor

public class FunctionServiceImpl implements FunctionService {

    GroovyClassLoader classLoader = new GroovyClassLoader();

    @Override
    public Object handle(String functionName, Boolean isPreview, HashMap<String, Object> parameters) {
        Object result = null;
        try {
            classLoader.parseClass(new File("D:\\weilong(1)\\workplaces\\foundry2\\ontology\\ontology-server\\src\\main\\java\\com\\aircas\\ptr\\foundry\\ontology\\function\\Ontology.groovy"));

            String fileName = this.getFileName(functionName,isPreview);
            Class groovyClass = classLoader.parseClass(new File(fileName));
            GroovyObject groovyObject = (GroovyObject)groovyClass.newInstance();
            result = groovyObject.invokeMethod("handle", parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public Boolean write(String functionName, String code, Boolean isPreview) {
        String fileName = getFileName(functionName, isPreview);
        File file = new File(fileName);
        if (isPreview) {
            file.delete();
        }
        try {
            if (file.createNewFile()) {
                System.out.println("file created in path" +  file.getAbsolutePath());
                String decodeCode = URLDecoder.decode(code, "UTF-8");
                FileUtils.writeStringToFile(file, decodeCode);
            } else {
                System.out.println("file already exists in path {}" + file.getAbsolutePath());
                return false;
            }
        } catch (IOException e) {
            System.out.println("create file failed");
            return false;
        }
        return true;
    }

    @Override
    public String get(String functionName, Boolean isPreview) {
        String fileName = getFileName(functionName, isPreview);
        File file = new File(fileName);
        try {
            String code = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            String decodeCode = URLEncoder.encode(code, "UTF-8");
            return decodeCode;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getFileName(String functionName, Boolean isPreview) {
        String fileName = functionName + ".groovy";
        if (isPreview) {
            fileName = "is_preview_" + functionName + ".groovy";
        }
        return fileName;
    }
}
