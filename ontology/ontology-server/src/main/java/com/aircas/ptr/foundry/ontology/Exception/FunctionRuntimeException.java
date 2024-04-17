package com.aircas.ptr.foundry.ontology.Exception;


public class FunctionRuntimeException extends BaseException {

    public FunctionRuntimeException(Throwable cause) {
        super(ErrorCodeMap.FUNCTION_RUNTIME_ERROR_CODE, cause);
    }

}
