package com.aircas.ptr.foundry.common.exception;

import lombok.Getter;

@Getter
public class DmException extends RuntimeException {

    private Integer code;//标准编码，用于根据编码查询错误信息

    public DmException() {
        super();
    }

    public DmException(String message) {
        super(message);
    }

    public DmException(String message, Throwable cause) {
        super(message, cause);
    }

    public DmException(Integer code) {
        super();
        this.code = code;
    }
}
