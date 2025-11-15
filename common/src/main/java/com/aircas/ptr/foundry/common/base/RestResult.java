package com.aircas.ptr.foundry.common.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>封装统一的返回结果</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class RestResult<T> {

    private Integer code;


    private String message;


    private T data;

    public Integer getCode() {
        return code;
    }


    public RestResult(ResultCode resultCode) {
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    public RestResult(ResultCode resultCode, T data) {
        this(resultCode);
        this.data = data;
    }

    public RestResult(ResultCode resultCode, String message) {
        this.code = resultCode.code;
        this.message = message;
    }

    public RestResult(ResultCode resultCode, String message, T data) {
        this.code = resultCode.code;
        this.message = message;
        this.data = data;
    }

    public RestResult setCode(Integer code) {
        this.code = code;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public RestResult setMessage(String message) {
        this.message = message;
        return this;
    }

    public T getData() {
        return data;
    }

    public RestResult setData(T data) {
        this.data = data;
        return this;
    }

    public static <T> RestResult<T> ofData(T data) {
        return new RestResult(ResultCode.SUCCESS, data);
    }


    public static <T> RestResult<T> success() {
        return new RestResult(ResultCode.SUCCESS);
    }

    public static <T> RestResult<T> failed() {
        return new RestResult(ResultCode.ERROR);
    }


}
