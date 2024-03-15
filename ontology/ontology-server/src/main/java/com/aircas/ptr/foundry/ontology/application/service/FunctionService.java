package com.aircas.ptr.foundry.ontology.application.service;


import java.util.HashMap;

public interface FunctionService {
    Object handle(String path, HashMap<String, Object> parameters);
}
