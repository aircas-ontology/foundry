package com.aircas.ptr.foundry.common.response;


import com.aircas.ptr.foundry.common.enums.ResponseEnum;

/**
 * @Description 公共API数据返回工具类
 * @Author 李杰
 * @Date 2020/7/16 2:15 下午
 **/
public class ResultUtil<T> {

    /**
     * 执行成功
     * @param t 数据对象
     * @param <T> 数据类型
     * @return result公共返回数据对象
     */
    public static <T> Result success(T t) {
        return Result.builder()
                .code(ResponseEnum.SUCCESS.getCode())
                .msg(ResponseEnum.SUCCESS.getMessage())
                .data(t)
                .build();
    }

    /**
     * 执行成功
     * @param <T>
     * @return result公共返回数据对象
     */
    public static Result success() {
        return Result.builder()
                .code(ResponseEnum.SUCCESS.getCode())
                .msg(ResponseEnum.SUCCESS.getMessage())
                .build();
    }

    /**
     * 执行成功
     * @param code 错误码
     * @param msg 返回信息
     * @return result公共返回数据对象
     */
    public static Result success(String code, String msg) {
        return Result.builder()
                .code(code)
                .msg(msg)
                .build();
    }

    /**
     * 执行失败
     * @param errorMsg 错误信息
     * @return result公共返回数据对象
     */
    public static Result error(String errorMsg) {
        return Result.builder()
                .code(ResponseEnum.ERROR.getCode())
                .msg(errorMsg)
                .build();
    }

    public static <T> Result errorData(T data) {
        return Result.builder()
                .code(ResponseEnum.ERROR.getCode())
                .data(data)
                .build();
    }

    /**
     * 执行失败
     * @param errorCode 错误码
     * @param errorMsg 错误信息
     * @return result公共返回数据对象
     */
    public static Result error(String errorCode, String errorMsg) {
        return Result.builder()
                .code(errorCode)
                .msg(errorMsg)
                .build();
    }

    public static Result error(String errorMsg, Object data, Throwable e) {
        return Result.builder()
                .code(ResponseEnum.ERROR.getCode())
                .msg(errorMsg + " " + e.getMessage())
                .build();
    }

    public static boolean isSuccess(Result result) {
        return ResponseEnum.SUCCESS.getCode().equals(result.getCode());
    }

    public static <T> Result<T> result(int code, String msg, T data) {
        return new Result<T>(String.valueOf(code), msg, data);
    }
}
