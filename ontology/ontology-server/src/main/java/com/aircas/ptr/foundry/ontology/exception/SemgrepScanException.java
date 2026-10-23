package com.aircas.ptr.foundry.ontology.exception;

import com.aircas.ptr.foundry.ontology.model.dto.scan.ScanError;
import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionScanResult;
import com.aircas.ptr.foundry.ontology.model.enums.ScanErrorCode;

/** 扫描调用失败；阶段四由全局异常处理器转换为固定响应形状。 */
public class SemgrepScanException extends RuntimeException {

    private final int httpStatus;
    private final ScanError error;
    private final String topLevelMessage;
    private final FunctionScanResult scanResult;

    public SemgrepScanException(ScanErrorCode code) {
        this(code, code.message(), null, null);
    }

    public SemgrepScanException(ScanErrorCode code, Throwable cause) {
        this(code, code.message(), null, cause);
    }

    public SemgrepScanException(ScanErrorCode code, String stableMessage) {
        this(code, stableMessage, null, null);
    }

    public SemgrepScanException(ScanErrorCode code, FunctionScanResult scanResult) {
        this(code, code.message(), scanResult, null);
    }

    public SemgrepScanException(ScanErrorCode code, FunctionScanResult scanResult, Throwable cause) {
        this(code, code.message(), scanResult, cause);
    }

    private SemgrepScanException(ScanErrorCode code, String stableMessage,
                                 FunctionScanResult scanResult, Throwable cause) {
        super(stableMessage + "（" + code.name() + "）", cause);
        this.httpStatus = code.httpStatus();
        this.error = ScanError.of(code, stableMessage);
        this.topLevelMessage = getMessage();
        this.scanResult = scanResult;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public ScanError getError() {
        return error;
    }

    public String getTopLevelMessage() {
        return topLevelMessage;
    }

    public FunctionScanResult getScanResult() {
        return scanResult;
    }
}
