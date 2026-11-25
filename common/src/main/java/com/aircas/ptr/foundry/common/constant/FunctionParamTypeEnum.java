package com.aircas.ptr.foundry.common.constant;

import com.fasterxml.jackson.annotation.JsonCreator;
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
        // 时间族：与 ScriptParamTypeMapper 的 DATE 口径对齐，收藏与测试请求里的类型文本都能认出来
        FUNC_PARAM_TYPE_MAP.put("timestamp", FunctionParamTypeEnum.DATE);
        FUNC_PARAM_TYPE_MAP.put("datetime", FunctionParamTypeEnum.DATE);
        FUNC_PARAM_TYPE_MAP.put("localdate", FunctionParamTypeEnum.DATE);
        FUNC_PARAM_TYPE_MAP.put("localdatetime", FunctionParamTypeEnum.DATE);
        FUNC_PARAM_TYPE_MAP.put("instant", FunctionParamTypeEnum.DATE);
        FUNC_PARAM_TYPE_MAP.put("java.sql.Timestamp", FunctionParamTypeEnum.DATE);
        FUNC_PARAM_TYPE_MAP.put("java.time.LocalDateTime", FunctionParamTypeEnum.DATE);
        FUNC_PARAM_TYPE_MAP.put("java.time.LocalDate", FunctionParamTypeEnum.DATE);
        FUNC_PARAM_TYPE_MAP.put("java.time.Instant", FunctionParamTypeEnum.DATE);
        FUNC_PARAM_TYPE_MAP.put("java.lang.Object", FunctionParamTypeEnum.OBJECT);
        FUNC_PARAM_TYPE_MAP.put("java.util.Map", FunctionParamTypeEnum.OBJECT);
        FUNC_PARAM_TYPE_MAP.put("Object", FunctionParamTypeEnum.OBJECT);
        FUNC_PARAM_TYPE_MAP.put("Map", FunctionParamTypeEnum.OBJECT);
    }

    /**
     * Jackson 反序列化入口：兼容前端 TypeScript/Python 的通用类型别名。
     * <ul>
     *   <li>NUMBER → DOUBLE（TS/Python 的 number 为 IEEE 754 双精度浮点）</li>
     *   <li>ARRAY → LIST</li>
     *   <li>BOOLEAN → BOOL</li>
     *   <li>INT → INTEGER</li>
     * </ul>
     */
    @JsonCreator
    public static FunctionParamTypeEnum fromJson(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        // 先尝试精确匹配枚举名
        try {
            return FunctionParamTypeEnum.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
        // 别名映射
        return switch (value.toUpperCase()) {
            case "NUMBER" -> DOUBLE;
            case "ARRAY" -> LIST;
            case "BOOLEAN" -> BOOL;
            case "INT" -> INTEGER;
            case "STR" -> STRING;
            case "DICT", "MAP" -> OBJECT;
            default -> throw new IllegalArgumentException("未知的参数类型: " + value);
        };
    }

    /**
     * 参数类型文本还原枚举：兼容库里的展示文本（{@code List<Double>}、{@code OrbitPoint[]}）、
     * 枚举名（{@code LIST}/{@code DOUBLE}）以及 TS/Python 别名；认不出的自定义类名按 OBJECT 处理。
     */
    public static FunctionParamTypeEnum fromTypeText(String typeText) {
        if (typeText == null || typeText.isBlank()) {
            return null;
        }
        String base = typeText.trim();
        int angle = base.indexOf('<');
        if (angle > 0) {
            base = base.substring(0, angle).trim();
        }
        if (base.endsWith("[]")) {
            return LIST;
        }
        FunctionParamTypeEnum mapped = FUNC_PARAM_TYPE_MAP.get(base);
        if (mapped == null) {
            // 大小写写法（TimeStamp/Timestamp/LocalDateTime）：类型文本按不区分大小写再查一次
            mapped = FUNC_PARAM_TYPE_MAP.get(base.toLowerCase());
        }
        if (mapped != null) {
            return mapped;
        }
        try {
            return fromJson(base);
        } catch (IllegalArgumentException e) {
            return OBJECT;
        }
    }

    public static FunctionParamTypeEnum getByTypeName(String type) {
        return FUNC_PARAM_TYPE_MAP.get(type);
    }

    public static Boolean isBasicType(String type) {
        return FUNC_PARAM_TYPE_MAP.keySet().contains(type);
    }


}
