package com.aircas.ptr.foundry.ontology.exception;

import com.aircas.ptr.foundry.ontology.model.dto.scan.ScanError;

/** 修改或发布扫描期间目标版本发生变化。 */
public class FunctionVersionConflictException extends RuntimeException {

    public static final String CODE = "FUNCTION_VERSION_CONFLICT";
    public static final String MESSAGE = "函数版本已被修改或发布，请刷新后重试";

    private final ScanError error = new ScanError(CODE, MESSAGE, true);

    public FunctionVersionConflictException() {
        super(MESSAGE + "（" + CODE + "）");
    }

    public int getHttpStatus() {
        return 409;
    }

    public ScanError getError() {
        return error;
    }
}
