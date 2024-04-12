package com.aircas.ptr.foundry.ontology.Exception;


public class FunctionNotFoundException extends BaseException {
    public FunctionNotFoundException(Throwable cause) {
        super(ErrorCodeMap.FUNCTION_FILE_NOT_FOUND_CODE, cause);
    }
}
