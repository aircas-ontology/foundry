package com.aircas.ptr.foundry.common.exception;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultCode;
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

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * @description 全局异常处理器，用于捕获并处理自定义的异常
 * @since 2023/12/18 17:44
 */

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
    public ResponseEntity<RestResult> handleBadRequestException(Exception ex) {
        RestResult result = new RestResult(ResultCode.PARAM_ERROR);
        String errorMsg = ex.getMessage();
        // MethodArgumentNotValidException 继承 BindException，两者的 bindingResult 展开方式一致
        if (ex instanceof BindException bindException) {
            errorMsg = String.join(";", bindException.getBindingResult().getAllErrors().stream()
                    .map(error -> error.getDefaultMessage()).collect(Collectors.toList()));
            result.setMessage(errorMsg);
        } else if (ex instanceof ConstraintViolationException exception) {
            errorMsg = String.join(";", exception.getConstraintViolations().stream()
                    .map(violation -> violation.getMessage()).collect(Collectors.toList()));
            result.setMessage(errorMsg);
        } else {
            // 其余参数异常（缺参数、请求体不可读、绑定失败等）原先只回「参数异常」，这里换成具体原因
            result.setMessage(SystemErrorClassifier.classifyMessage(ex));
        }
        log.error("handleBadRequestException:{}", errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }


    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<RestResult> handleBusinessException(BusinessException exception) {
        log.error("handleBusinessException:", exception);
        return ResponseEntity.status(exception.getHttpStatus()).body(new RestResult(exception.getResultCode(), exception.getMessage()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<RestResult> handleThrowable(Throwable t) {
        String prompt = SystemErrorClassifier.classifyMessage(t);
        log.error("handleThrowable:{}", prompt, t);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new RestResult(ResultCode.ERROR, prompt));
    }
}
