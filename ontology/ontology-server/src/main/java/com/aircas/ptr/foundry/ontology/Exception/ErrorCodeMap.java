package com.aircas.ptr.foundry.ontology.Exception;

import org.apache.groovy.util.Maps;

import java.util.Map;


public class ErrorCodeMap {

    //函数相关errorCode
    static Integer FUNCTION_FILE_NOT_FOUND_CODE = 301;
    static Integer FUNCTION_FILE_NOT_COMPILED_CODE = 302;
    static Integer FUNCTION_CLASS_NOT_NEW_INSTANCE_CODE = 303;
    static Integer FUNCTION_RUNTIME_ERROR_CODE = 304;


    private static Map codeMap = Maps.of(
            FUNCTION_FILE_NOT_FOUND_CODE,"函数文件未找到",
            FUNCTION_FILE_NOT_COMPILED_CODE,"函数文件无法编译",
            FUNCTION_CLASS_NOT_NEW_INSTANCE_CODE,"函数类不能创建实例",
            FUNCTION_RUNTIME_ERROR_CODE,"函数运行时错误"
    );

    static String getErrorDesc(Integer code) {
        return (String) codeMap.getOrDefault(code, null);
    }
}
