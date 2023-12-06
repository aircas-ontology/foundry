package com.aircas.ptr.foundry.common.aspect;

import java.lang.annotation.*;

/**
 * 用户身份信息注解
 * 获取请求头中的用户信息userId和token，并封装到AuthContext中
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Auth {
}
