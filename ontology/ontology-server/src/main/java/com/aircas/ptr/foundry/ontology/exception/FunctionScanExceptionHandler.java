package com.aircas.ptr.foundry.ontology.exception;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionScanErrorResponse;
import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionScanResult;
import com.aircas.ptr.foundry.ontology.model.dto.scan.ScanError;
import com.aircas.ptr.foundry.ontology.model.enums.ScanStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将扫描异常和函数版本冲突转换为固定、无上游诊断泄漏的接口契约。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class FunctionScanExceptionHandler {

    @ExceptionHandler(SemgrepScanException.class)
    public ResponseEntity<RestResult<FunctionScanErrorResponse>> handleScanException(
            SemgrepScanException exception) {
        FunctionScanResult scanResult = exception.getScanResult();
        ScanStatus status = scanResult != null
                ? scanResult.status()
                : exception.getHttpStatus() == 422 ? ScanStatus.REJECTED : ScanStatus.FAILED;
        ScanError externalError = scanResult == null ? exception.getError() : null;
        FunctionScanErrorResponse data = new FunctionScanErrorResponse(status, scanResult, externalError);
        log.warn("function scan request rejected errorCode={} httpStatus={}",
                exception.getError().code(), exception.getHttpStatus());
        return ResponseEntity.status(exception.getHttpStatus())
                .body(new RestResult<>(exception.getHttpStatus(), exception.getTopLevelMessage(), data));
    }

    @ExceptionHandler(FunctionVersionConflictException.class)
    public ResponseEntity<RestResult<FunctionScanErrorResponse>> handleVersionConflict(
            FunctionVersionConflictException exception) {
        FunctionScanErrorResponse data = new FunctionScanErrorResponse(null, null, exception.getError());
        log.warn("function version conflict errorCode={}", exception.getError().code());
        return ResponseEntity.status(exception.getHttpStatus())
                .body(new RestResult<>(exception.getHttpStatus(), exception.getMessage(), data));
    }
}
