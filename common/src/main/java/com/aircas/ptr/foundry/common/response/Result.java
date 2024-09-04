package com.aircas.ptr.foundry.common.response;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description 公共API返回数据对象
 * @Author 李杰
 * @Date 2020/7/16 2:15 下午
 **/
@Data
@Builder
@NoArgsConstructor
public class Result<T> {
    private String code;
    private String msg;
    private T data;

    /**
     * 带参构造函数
     * @param code 返回码
     */
    public Result(String code) {
        this.code = code;
    }

    /**
     * 带参构造函数
     * @param code 返回码
     * @param msg 返回信息
     */
    public Result(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 带参构造函数
     * @param code 返回码
     * @param msg 返回信息
     * @param data 返回数据
     */
    public Result(String code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }
}
