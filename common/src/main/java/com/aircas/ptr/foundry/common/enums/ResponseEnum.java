package com.aircas.ptr.foundry.common.enums;

import lombok.Getter;

/**
 * @Description 公共异常枚举类
 **/
@Getter
public enum ResponseEnum {

    SUCCESS("200", "成功"), ERROR("500", "失败"), PARAMS_ERROR("A1000", "参数错误");

    private String code;
    private String message;

    ResponseEnum() {
    }

    public static boolean isSuccess(String code) {
        if (SUCCESS.getCode().equals(code)) {
            return true;
        } else {
            return false;
        }
    }

    ResponseEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
