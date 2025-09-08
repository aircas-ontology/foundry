package com.aircas.ptr.foundry.ontology.exception;


public class OntologyFunctionNotFoundException extends BaseException {

    public OntologyFunctionNotFoundException(Throwable cause) {
        super(ErrorCodeMap.ONTOLOGY_FUNCTION_NOT_FOUND_CODE, cause);
    }
}
