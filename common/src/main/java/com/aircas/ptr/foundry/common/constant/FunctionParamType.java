package com.aircas.ptr.foundry.common.constant;
import java.util.HashMap;
import java.util.Map;

public class FunctionParamType {

    public static final Map<String,String> FUNC_PARAM_TYPE_MAP = new HashMap<>();
    static{
        FUNC_PARAM_TYPE_MAP.put("int","INTEGER");
        FUNC_PARAM_TYPE_MAP.put("long","LONG");
        FUNC_PARAM_TYPE_MAP.put("double","DOUBLE");
        FUNC_PARAM_TYPE_MAP.put("float","FLOAT");
        FUNC_PARAM_TYPE_MAP.put("boolean","BOOL");
        FUNC_PARAM_TYPE_MAP.put("char","CHAR");
        FUNC_PARAM_TYPE_MAP.put("byte","BYTE");
        FUNC_PARAM_TYPE_MAP.put("short","SHORT");
        FUNC_PARAM_TYPE_MAP.put("java.lang.Integer","INTEGER");
        FUNC_PARAM_TYPE_MAP.put("java.lang.Long","LONG");
        FUNC_PARAM_TYPE_MAP.put("java.lang.Double","DOUBLE");
        FUNC_PARAM_TYPE_MAP.put("java.lang.Float","FLOAT");
        FUNC_PARAM_TYPE_MAP.put("java.lang.Boolean","BOOL");
        FUNC_PARAM_TYPE_MAP.put("java.lang.Char","CHAR");
        FUNC_PARAM_TYPE_MAP.put("java.lang.Byte","BYTE");
        FUNC_PARAM_TYPE_MAP.put("java.lang.Short","SHORT");
        FUNC_PARAM_TYPE_MAP.put("java.lang.String","STRING");
    }

    public static String getTypeEnum(String type){
        return FUNC_PARAM_TYPE_MAP.get(type);
    }

    public static boolean isBasicType(String type){
        return FUNC_PARAM_TYPE_MAP.keySet().contains(type);
    }
}
