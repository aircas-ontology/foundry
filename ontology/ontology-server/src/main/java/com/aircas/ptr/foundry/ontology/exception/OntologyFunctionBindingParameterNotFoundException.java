package com.aircas.ptr.foundry.ontology.exception;

public class OntologyFunctionBindingParameterNotFoundException extends BaseException {

    public OntologyFunctionBindingParameterNotFoundException(Throwable cause) {
        super(ErrorCodeMap.ONTOLOGY_FUNCTION_BINDG_PARAMETER_NOT_FOUND_CODE, cause);
    }

}
