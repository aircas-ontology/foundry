package com.aircas.ptr.foundry.common.exception;

/**
 * @author dongjunchuan
 * @description 自定义重复插入数据库的异常类
 * @since 2023/12/18 17:42
 */


public class DuplicatedDataException extends RuntimeException{
     public DuplicatedDataException(String msg) {
          super(msg);
     }
}
