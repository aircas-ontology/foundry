package com.aircas.ptr.foundry.ontology.Exception;

public class OwlUrilInvalidPrimaryKeyNotFoundException extends BaseException {
    public OwlUrilInvalidPrimaryKeyNotFoundException(Throwable cause) {
        super(ErrorCodeMap.OWL_URI_INVALID_PRIMARY_KEY_NOT_FOUND_CODE, cause);
    }
}
