package com.aircas.ptr.foundry.ontology.Exception;

public class FunctionFileNotCompiled extends BaseException {
    public FunctionFileNotCompiled(Throwable cause) {
        super(ErrorCodeMap.FUNCTION_FILE_NOT_FOUND_CODE, cause);
    }
}
