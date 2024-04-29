package com.aircas.ptr.foundry.ontology.entity.bo;


import lombok.Data;

import java.util.HashMap;

@Data
public class FunctionRequestBodyBO {
    private boolean isPreview;
    private OntologyBaseObjectBo currentObject;
    private String functionName;
    private HashMap<String, Object> parameters;
}
