package com.aircas.ptr.foundry.runner.web;

import lombok.Data;

/**
 * 校验请求：仅含脚本源码，做语法/试编译校验，不执行。
 */
@Data
public class ValidateRequest {

    private String code;

}
