package com.aircas.ptr.foundry.ontology.entity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 资源未找到异常
 * 当请求的资源不存在时抛出此异常
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建一个资源未找到异常
     */
    public ResourceNotFoundException() {
        super("请求的资源不存在");
    }

    /**
     * 创建一个资源未找到异常，并指定错误消息
     * 
     * @param message 错误消息
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * 创建一个资源未找到异常，并指定错误消息和原因
     * 
     * @param message 错误消息
     * @param cause 原因
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
} 