package com.aircas.ptr.foundry.common.base;

public class ResultGenerator {

    public static RestResult genSuccessResult() {
        return new RestResult(ResultCode.SUCCESS);
    }


    public static RestResult genFailResult(String message) {
        return new RestResult(ResultCode.ERROR, message);
    }

    /**
     * 未验证error 构造器
     * @return
     */
    public static RestResult genUnauthResult() {
        return new RestResult(ResultCode.UNAUTHORIZED);
    }
}
