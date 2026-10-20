package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import org.apache.commons.lang.StringUtils;

import java.util.Set;

/**
 * 脚本参数类型归一化：把各语言源码里的类型文本（TypeScript/Python/Java/Groovy 写法）
 * 统一映射为后端 {@link FunctionParamTypeEnum}。
 * <p>
 * 解析器只负责"认出类型文本"，映射口径集中在此，避免四种语言各写一套。
 * 未知/复杂类型一律降级为 OBJECT（与既有 DICT/MAP→OBJECT 契约一致）。
 */
public final class ScriptParamTypeMapper {

    /** 整型族（各语言写法）：32 位整型 */
    private static final Set<String> INTEGER_TYPES = Set.of(
            "int", "integer", "i32", "u32");
    /** 长整族 */
    private static final Set<String> LONG_TYPES = Set.of("long", "i64", "u64", "int64");
    /** 短整族 */
    private static final Set<String> SHORT_TYPES = Set.of("short", "i16", "u16");
    /** 字节族 */
    private static final Set<String> BYTE_TYPES = Set.of("byte", "i8", "u8");
    /** 双精度浮点族（TS/Python number 归此） */
    private static final Set<String> DOUBLE_TYPES = Set.of(
            "double", "number", "f64", "bigdecimal", "decimal");
    /** 单精度浮点族 */
    private static final Set<String> FLOAT_TYPES = Set.of("float", "f32");
    /** 字符串族 */
    private static final Set<String> STRING_TYPES = Set.of("string", "str", "text");
    /** 字符族 */
    private static final Set<String> CHAR_TYPES = Set.of("char", "character");
    /** 布尔族 */
    private static final Set<String> BOOL_TYPES = Set.of("boolean", "bool");
    /** 列表族 */
    private static final Set<String> LIST_TYPES = Set.of("list", "array", "set", "iterable", "[]");
    /** 日期族 */
    private static final Set<String> DATE_TYPES = Set.of("date", "datetime", "localdate", "localdatetime", "timestamp");

    private ScriptParamTypeMapper() {
    }

    /**
     * 将源码类型文本映射为参数类型枚举；空/未知统一按 OBJECT。
     *
     * @param typeText 类型文本（如 string / number / List / TypedDict 名 / User 等）。
     * @return 映射后的枚举，永不为 null。
     */
    public static FunctionParamTypeEnum map(String typeText) {
        if (StringUtils.isBlank(typeText)) {
            return FunctionParamTypeEnum.OBJECT;
        }
        // 去掉泛型参数与数组后缀：List<String> → list；String[] → string[]
        String base = stripGeneric(typeText).trim().toLowerCase();
        // 数组/列表：a[] 或 []
        if (base.endsWith("[]") || LIST_TYPES.contains(base)) {
            return FunctionParamTypeEnum.LIST;
        }
        if (INTEGER_TYPES.contains(base)) {
            return FunctionParamTypeEnum.INTEGER;
        }
        if (LONG_TYPES.contains(base)) {
            return FunctionParamTypeEnum.LONG;
        }
        if (SHORT_TYPES.contains(base)) {
            return FunctionParamTypeEnum.SHORT;
        }
        if (BYTE_TYPES.contains(base)) {
            return FunctionParamTypeEnum.BYTE;
        }
        if (DOUBLE_TYPES.contains(base)) {
            return FunctionParamTypeEnum.DOUBLE;
        }
        if (FLOAT_TYPES.contains(base)) {
            return FunctionParamTypeEnum.FLOAT;
        }
        if (STRING_TYPES.contains(base)) {
            return FunctionParamTypeEnum.STRING;
        }
        if (CHAR_TYPES.contains(base)) {
            return FunctionParamTypeEnum.CHAR;
        }
        if (BOOL_TYPES.contains(base)) {
            return FunctionParamTypeEnum.BOOL;
        }
        if (DATE_TYPES.contains(base)) {
            return FunctionParamTypeEnum.DATE;
        }
        // 其余（object/dict/map/自定义类名/any/unknown 等）统一按 OBJECT
        return FunctionParamTypeEnum.OBJECT;
    }

    /**
     * 已知基础类型（非 OBJECT）的集合，供 {@link #map} 与 {@link #isScalarType} 复用同一份判定，
     * 避免两处口径分叉。
     */
    private static final Set<FunctionParamTypeEnum> KNOWN_SCALAR_TYPES = Set.of(
            FunctionParamTypeEnum.INTEGER, FunctionParamTypeEnum.LONG, FunctionParamTypeEnum.SHORT,
            FunctionParamTypeEnum.BYTE, FunctionParamTypeEnum.DOUBLE, FunctionParamTypeEnum.FLOAT,
            FunctionParamTypeEnum.STRING, FunctionParamTypeEnum.CHAR, FunctionParamTypeEnum.BOOL,
            FunctionParamTypeEnum.DATE, FunctionParamTypeEnum.LIST);

    /**
     * 判定类型文本是否为"标量/已知基础类型"，用于解析器区分"直接落参数"还是"需展开对象字段"。
     * <p>
     * 复用 {@link #map} 的精确集合结果（而非子串匹配），避免把 {@code PlaylistInput}、{@code DatasetRow}
     * 这类名字里含 list/set/array 子串的用户自定义类误判为标量、从而跳过字段展开。
     */
    public static boolean isScalarType(String typeText) {
        if (StringUtils.isBlank(typeText)) {
            return false;
        }
        return KNOWN_SCALAR_TYPES.contains(map(typeText));
    }

    private static String stripGeneric(String typeText) {
        int angle = typeText.indexOf('<');
        return angle > 0 ? typeText.substring(0, angle) : typeText;
    }
}
