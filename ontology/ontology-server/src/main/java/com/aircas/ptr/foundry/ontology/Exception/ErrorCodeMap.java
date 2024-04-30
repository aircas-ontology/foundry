package com.aircas.ptr.foundry.ontology.Exception;

import org.apache.groovy.util.Maps;

import java.util.Map;


public class ErrorCodeMap {

    //函数相关errorCode
    static Integer ONTOLOGY_API_NAME_NOT_FOUND_CODE = 101;

    //函数相关errorCode
    static Integer FUNCTION_FILE_NOT_FOUND_CODE = 301;
    static Integer FUNCTION_FILE_NOT_COMPILED_CODE = 302;
    static Integer FUNCTION_CLASS_NOT_NEW_INSTANCE_CODE = 303;
    static Integer FUNCTION_RUNTIME_ERROR_CODE = 304;

    static Integer ONTOLOGY_FUNCTION_NOT_FOUND_CODE = 401;
    static Integer ONTOLOGY_FUNCTION_MAPPED_PROPERTY_NOT_FOUND_CODE = 402;
    static Integer ONTOLOGY_FUNCTION_PARAMETE_PROPERTY_Type_NOT_SAME_CODE = 403;
    static Integer ONTOLOGY_FUNCTION_BINDG_PARAMETER_NOT_FOUND_CODE = 404;

    static Integer OWL_URI_INVALID_CLASS_NOT_FOUND_CODE = 501;
    static Integer OWL_URI_INVALID_PRIMARY_KEY_NOT_FOUND_CODE = 502;



    private static Map codeMap = Maps.of(
            ONTOLOGY_API_NAME_NOT_FOUND_CODE,"本体未找到",

            FUNCTION_FILE_NOT_FOUND_CODE,"函数文件未找到",
            FUNCTION_FILE_NOT_COMPILED_CODE,"函数文件无法编译",
            FUNCTION_CLASS_NOT_NEW_INSTANCE_CODE,"函数类不能创建实例",
            FUNCTION_RUNTIME_ERROR_CODE,"函数运行时错误",

            ONTOLOGY_FUNCTION_NOT_FOUND_CODE,"无法找到绑定到该本体的函数",
            ONTOLOGY_FUNCTION_MAPPED_PROPERTY_NOT_FOUND_CODE, "绑定的本体属性无法找到",
            ONTOLOGY_FUNCTION_PARAMETE_PROPERTY_Type_NOT_SAME_CODE, "绑定的本体属性和参数类型不一致",
            ONTOLOGY_FUNCTION_BINDG_PARAMETER_NOT_FOUND_CODE, "绑定的参数无法找到",

            OWL_URI_INVALID_CLASS_NOT_FOUND_CODE, "无法找到OWL URI中此本体",
            OWL_URI_INVALID_PRIMARY_KEY_NOT_FOUND_CODE, "无法找到OWL URI中的主键对应的对象"
    );

    static String getErrorDesc(Integer code) {
        return (String) codeMap.getOrDefault(code, null);
    }
}
