package com.aircas.ptr.foundry.common.util;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

public class PreconditionUtils {

    public static void checkArgument(boolean expression, String message) {
        if (!expression) {
            throw new BusinessException(message);
        }
    }

    public static void checkArgument(boolean expression, String message, ResultCode resultCode) {
        if (!expression) {
            throw new BusinessException(message, resultCode);
        }
    }

    public static void checkArgument(boolean expression, String message, HttpStatus httpStatus) {
        if (!expression) {
            throw new BusinessException(message, httpStatus);
        }
    }

    public static void checkArgument(boolean expression, String message, ResultCode resultCode, HttpStatus httpStatus) {
        if (!expression) {
            throw new BusinessException(message, resultCode, httpStatus);
        }
    }

    public static void checkNotNull(Object object, String message) {
        if (object == null) {
            throw new BusinessException(message);
        }
    }

    public static void checkNotNull(Object object, String message, HttpStatus httpStatus) {
        if (object == null) {
            throw new BusinessException(message, httpStatus);
        }
    }

    public static void checkNotEmpty(String string, String message) {
        if (StringUtils.isEmpty(string)) {
            throw new BusinessException(message);
        }
    }
}
