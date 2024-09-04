package com.aircas.ptr.foundry.common.exception;

import lombok.Getter;

import java.util.function.Supplier;

/**
 * @author guangya.zhao
 */
@Getter
public class
DmException extends RuntimeException implements Supplier<DmException> {

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

    public DmException(Throwable cause) {
        super(cause);
    }

    protected DmException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    @Override
    public DmException get() {
        return null;
    }
}
