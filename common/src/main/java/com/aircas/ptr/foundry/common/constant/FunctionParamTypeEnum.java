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

    OBJECT(1, "对象类型", "Object"),
    STRING(2, "字符串", "String"),
    INTEGER(3, "整型", "Integer"),
    LONG(4, "长整型", "Long"),
    FLOAT(5, "单浮点型", "Float"),
    DOUBLE(6, "双浮点型", "Double"),
    BOOL(7, "布尔类型", "Boolean"),
    BYTE(8, "字节", "Byte"),
    SHORT(9, "短整型", "Short"),
    CHAR(10, "字符", "Char"),
    LIST(11, "列表", "List"),
    DATE(12, "日期", "Date"),

    ;

    private final int id;

    private final String name;

    private final String vale;


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
        FUNC_PARAM_TYPE_MAP.put("java.util.List", FunctionParamTypeEnum.LIST);
        FUNC_PARAM_TYPE_MAP.put("java.util.Date", FunctionParamTypeEnum.DATE);
    }

    public static FunctionParamTypeEnum getByTypeName(String type) {
        return FUNC_PARAM_TYPE_MAP.get(type);
    }

    public static Boolean isBasicType(String type) {
        return FUNC_PARAM_TYPE_MAP.keySet().contains(type);
    }


}
