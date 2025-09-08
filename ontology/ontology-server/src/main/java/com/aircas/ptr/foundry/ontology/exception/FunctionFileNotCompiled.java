package com.aircas.ptr.foundry.ontology.exception;

public class FunctionFileNotCompiled extends BaseException {
    public FunctionFileNotCompiled(Throwable cause) {
        super(ErrorCodeMap.FUNCTION_FILE_NOT_COMPILED_CODE, cause);
    }
}
