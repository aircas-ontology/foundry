package com.aircas.ptr.foundry.ontology.Exception;


public class ExceptionFactory {

    public static FunctionClassNotNewInstanceException getFunctionClassNotNewInstanceException(Throwable cause) {
        return new FunctionClassNotNewInstanceException(cause);
    }

    public static FunctionFileNotCompiled getFunctionFileNotCompiledException(Throwable cause) {
        return new FunctionFileNotCompiled(cause);
    }

    public static FunctionNotFoundException getFunctionNotFoundException(Throwable cause) {
        return new FunctionNotFoundException(cause);
    }

    public static FunctionRuntimeException getFunctionRuntimeException(Throwable cause) {
        return new FunctionRuntimeException(cause);
    }

    public static OwlUriInvalidClassNotFoundException getOwlUriInvalidClassNotFoundException(Throwable cause) {
        return new OwlUriInvalidClassNotFoundException(cause);
    }
}
