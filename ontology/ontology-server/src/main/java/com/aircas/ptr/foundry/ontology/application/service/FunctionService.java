package com.aircas.ptr.foundry.ontology.application.service;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

public interface FunctionService {
    Object handle(String functionName, Boolean isPreview, HashMap<String, Object> parameters);

    Boolean write(String functionName, String code, Boolean isPreview);

    String get(String functionName, Boolean isPreview);
}
