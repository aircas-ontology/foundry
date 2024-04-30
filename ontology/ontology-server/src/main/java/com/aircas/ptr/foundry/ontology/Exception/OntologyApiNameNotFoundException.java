package com.aircas.ptr.foundry.ontology.Exception;

public class OntologyApiNameNotFoundException extends BaseException {

    public OntologyApiNameNotFoundException(Throwable cause) {
        super(ErrorCodeMap.ONTOLOGY_API_NAME_NOT_FOUND_CODE, cause);
    }

}
