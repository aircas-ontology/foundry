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

    public static OntologyFunctionNotFoundException getOntologyFunctionNotFoundException(Throwable cause) {
        return new OntologyFunctionNotFoundException(cause);
    }

    public static OntologyFunctionMappedPropertyNotFoundException getOntologyFunctionMappedPropertyNotFoundException(Throwable cause) {
        return new OntologyFunctionMappedPropertyNotFoundException(cause);
    }

    public static OwlUriInvalidClassNotFoundException getOwlUriInvalidClassNotFoundException(Throwable cause) {
        return new OwlUriInvalidClassNotFoundException(cause);
    }

    public static OwlUrilInvalidPrimaryKeyNotFoundException getOwlUrilInvalidPrimaryKeyNotFoundException(Throwable cause) {
        return new OwlUrilInvalidPrimaryKeyNotFoundException(cause);
    }

}
