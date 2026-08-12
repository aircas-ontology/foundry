package com.aircas.ptr.foundry.common.exception;

import com.aircas.ptr.foundry.common.base.ResultCode;
import lombok.Data;
import org.springframework.http.HttpStatus;


@Data
public class BusinessException extends RuntimeException {

    private String msg;

    private ResultCode resultCode = ResultCode.ERROR;

    private HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

    public BusinessException(String msg) {
        super(msg);
    }

    public BusinessException(String msg, ResultCode resultCode, HttpStatus httpStatus) {
        super(msg);
        this.resultCode = resultCode;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String msg, ResultCode resultCode) {
        super(msg);
        this.resultCode = resultCode;
    }

    public BusinessException(String msg, HttpStatus httpStatus) {
        super(msg);
        this.httpStatus = httpStatus;
    }
}
