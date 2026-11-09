package com.aircas.ptr.foundry.ontology.exception;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionScanErrorResponse;
import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionScanResult;
import com.aircas.ptr.foundry.ontology.model.dto.scan.ScanError;
import com.aircas.ptr.foundry.ontology.model.enums.ScanStatusEnum;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

/** 全局异常处理器。 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            ServletRequestBindingException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestPartException.class,
            BindException.class})
    public ResponseEntity<RestResult<?>> handleBadRequestException(Exception exception) {
        RestResult<?> result = new RestResult<>(ResultCode.PARAM_ERROR);
        String errorMessage = exception.getMessage();
        if (exception instanceof MethodArgumentNotValidException validationException) {
            errorMessage = validationException.getBindingResult().getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(";"));
            result.setMessage(errorMessage);
        }
        if (exception instanceof ConstraintViolationException violationException) {
            errorMessage = violationException.getConstraintViolations().stream()
                    .map(violation -> violation.getMessage())
                    .collect(Collectors.joining(";"));
            result.setMessage(errorMessage);
        }
        log.error("handleBadRequestException: {}", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<RestResult<?>> handleBusinessException(BusinessException exception) {
        log.error("handleBusinessException:", exception);
        return ResponseEntity.status(exception.getHttpStatus())
                .body(new RestResult<>(exception.getResultCode(), exception.getMessage()));
    }

    @ExceptionHandler(SemgrepScanException.class)
    public ResponseEntity<RestResult<FunctionScanErrorResponse>> handleScanException(
            SemgrepScanException exception) {
        FunctionScanResult scanResult = exception.getScanResult();
        ScanStatusEnum status = scanResult != null
                ? scanResult.status()
                : exception.getHttpStatus() == 422 ? ScanStatusEnum.REJECTED : ScanStatusEnum.FAILED;
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

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<RestResult<?>> handleThrowable(Throwable throwable) {
        log.error("handleThrowable:", throwable);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RestResult<>(ResultCode.ERROR));
    }
}
