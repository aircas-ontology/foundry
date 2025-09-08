package com.aircas.ptr.foundry.ontology.exception;


public class FunctionClassNotNewInstanceException extends BaseException {
    public FunctionClassNotNewInstanceException(Throwable cause) {
        super(ErrorCodeMap.FUNCTION_CLASS_NOT_NEW_INSTANCE_CODE, cause);
    }
}
