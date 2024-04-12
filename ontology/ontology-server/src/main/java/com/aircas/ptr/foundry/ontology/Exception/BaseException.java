package com.aircas.ptr.foundry.ontology.Exception;

public class BaseException extends Exception {

    public Integer code;

    private Throwable cause;

    public BaseException(Integer code, Throwable cause) {
        super(ErrorCodeMap.getErrorDesc(code));
        this.code = code;
        this.cause = cause;
    }

    public Throwable getRootCause() {
        Throwable rootCause = cause;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
