package com.aircas.ptr.foundry.ontology.exception;


public class OwlUriInvalidClassNotFoundException extends BaseException {

    public OwlUriInvalidClassNotFoundException(Throwable cause) {
        super(ErrorCodeMap.OWL_URI_INVALID_CLASS_NOT_FOUND_CODE, cause);
    }

}
