package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@AllArgsConstructor
@Getter
public enum FunctionParamTypeEnum {

    OBJECT(1, "对象类型"),
    STRING(2, "字符串"),
    INTEGER(3, "整型"),
    LONG(4, "长整型"),
    FLOAT(5, "单浮点型"),
    DOUBLE(6, "双浮点型"),
    BOOL(7, "布尔类型"),
    BYTE(8, "字节"),
    SHORT(9, "短整型"),
    CHAR(10, "字符"),
    List(11, "列表"),
    ;

    private final int value;

    private final String name;


    public static final Map<String, FunctionParamTypeEnum> FUNC_PARAM_TYPE_MAP = new HashMap<>();

    static {
        FUNC_PARAM_TYPE_MAP.put("int", FunctionParamTypeEnum.INTEGER);
        FUNC_PARAM_TYPE_MAP.put("long", FunctionParamTypeEnum.LONG);
        FUNC_PARAM_TYPE_MAP.put("double", FunctionParamTypeEnum.DOUBLE);
        FUNC_PARAM_TYPE_MAP.put("float", FunctionParamTypeEnum.FLOAT);
        FUNC_PARAM_TYPE_MAP.put("boolean", FunctionParamTypeEnum.BOOL);
        FUNC_PARAM_TYPE_MAP.put("char", FunctionParamTypeEnum.CHAR);
        FUNC_PARAM_TYPE_MAP.put("byte", FunctionParamTypeEnum.BYTE);
        FUNC_PARAM_TYPE_MAP.put("short", FunctionParamTypeEnum.SHORT);
        FUNC_PARAM_TYPE_MAP.put("java.lang.Integer", FunctionParamTypeEnum.INTEGER);
        FUNC_PARAM_TYPE_MAP.put("java.lang.Long", FunctionParamTypeEnum.LONG);
        FUNC_PARAM_TYPE_MAP.put("java.lang.Double", FunctionParamTypeEnum.DOUBLE);
        FUNC_PARAM_TYPE_MAP.put("java.lang.Float", FunctionParamTypeEnum.FLOAT);
        FUNC_PARAM_TYPE_MAP.put("java.lang.Boolean", FunctionParamTypeEnum.BOOL);
        FUNC_PARAM_TYPE_MAP.put("java.lang.Char", FunctionParamTypeEnum.CHAR);
        FUNC_PARAM_TYPE_MAP.put("java.lang.Byte", FunctionParamTypeEnum.BYTE);
        FUNC_PARAM_TYPE_MAP.put("java.lang.Short", FunctionParamTypeEnum.SHORT);
        FUNC_PARAM_TYPE_MAP.put("java.lang.String", FunctionParamTypeEnum.STRING);
        FUNC_PARAM_TYPE_MAP.put("java.util.List", FunctionParamTypeEnum.List);
    }

    public static FunctionParamTypeEnum getByTypeName(String type) {
        return FUNC_PARAM_TYPE_MAP.get(type);
    }

    public static Boolean isBasicType(String type) {
        return FUNC_PARAM_TYPE_MAP.keySet().contains(type);
    }


}
