package com.aircas.ptr.foundry.ontology.exception;

public class OntologyFunctionMappedPropertyNotFoundException extends BaseException {

    public OntologyFunctionMappedPropertyNotFoundException(Throwable cause) {
        super(ErrorCodeMap.ONTOLOGY_FUNCTION_MAPPED_PROPERTY_NOT_FOUND_CODE, cause);
    }

}
