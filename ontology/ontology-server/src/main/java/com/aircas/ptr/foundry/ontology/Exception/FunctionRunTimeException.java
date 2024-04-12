package com.aircas.ptr.foundry.ontology.Exception;


public class FunctionRunTimeException extends BaseException {

    public FunctionRunTimeException(Throwable cause) {
        super(ErrorCodeMap.FUNCTION_RUNTIME_ERROR_CODE, cause);
    }

}
