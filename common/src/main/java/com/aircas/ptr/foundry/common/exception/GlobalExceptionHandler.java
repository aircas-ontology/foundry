package com.aircas.ptr.foundry.common.exception;

import com.aircas.ptr.foundry.common.base.ApiResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * @author dongjunchuan
 * @description 全局异常处理器，用于捕获并处理自定义的异常
 * @since 2023/12/18 17:44
 */

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
     @ExceptionHandler(DuplicatedDataException.class)
     public ResponseEntity<ApiResult> handleDuplicateDataException(DuplicatedDataException exception) {
          exception.printStackTrace();
          ApiResult result = new ApiResult();
          result.setResult(ApiResult.FALSE);
          result.setRejectReason(exception.getMessage());
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
     }
}
