package com.aircas.ptr.foundry.ontology.exception;

public class BaseException extends Exception {

    public Integer code;

    private Throwable cause;

    public BaseException(Integer code, Throwable cause) {
        super(ErrorCodeMap.getErrorDesc(code));
        this.code = code;
        this.cause = cause;
    }

    private Throwable getRootCause() {
        Throwable rootCause = cause;
        if (rootCause == null) {
            return null;
        }
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    public String getRootCauseMessage() {
        Throwable rootCause = this.getRootCause();
        if (rootCause == null) {
            return null;
        }
        return rootCause.getMessage();
    }
}
