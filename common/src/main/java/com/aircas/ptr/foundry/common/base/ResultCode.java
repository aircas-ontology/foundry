package com.aircas.ptr.foundry.common.base;

/**
 * @ClassName: ResultCode
 * @Description: 返回的状态码
 * @date
 */
public enum ResultCode {

    /**
     * HTTP通信使用
     */
    SUCCESS(200, "SUCCESS"),

    ERROR(500, "业务异常"),

    NO_PERMISSION(403, "无权限操作"),

    NOT_FOUND(404, "接口不存在"),

    PARAM_ERROR(400, "参数异常"),

    UNAUTHORIZED(401, "未登陆或token无效"),

    DUPLICATION(409, "参数冲突"),


    ;


    public Integer code;

    public String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
