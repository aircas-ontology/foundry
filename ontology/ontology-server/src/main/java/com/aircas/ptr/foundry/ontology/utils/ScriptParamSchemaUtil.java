package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import org.apache.commons.lang.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * 脚本参数解析结果的补齐工具：为解析出的参数填充与保存结构（FunctionParamDTO）对齐的
 * referenceType（全限定类名）与 paramSchema（JSON Schema 字符串）。
 * <p>
 * 静态解析不编译、不加载类，因此：
 * <ul>
 *   <li>referenceType 仅对 JVM 语言（Java/Groovy）推导真实全限定类名（执行链路会用它做类型转换）；
 *       Python/TS 无 Java 类概念，留空，执行链路（buildInputMap）对空值按原始 JSON 透传，语义不变。</li>
 *   <li>paramSchema 按归一化枚举生成基础 JSON Schema（不含用户类字段明细，前端按 paramType 渲染即可）。</li>
 * </ul>
 */
public final class ScriptParamSchemaUtil {

    /** 数组/列表族类型文本 → java.util.List（Groovy 解析器把数组统一还原为 "array"） */
    private static final Set<String> LIST_TYPE_TEXTS = Set.of("list", "array", "set", "iterable", "collection");

    /** 标量类型文本 → JVM 装箱全限定类名（与 FunctionParamTypeEnum.FUNC_PARAM_TYPE_MAP 同源口径） */
    private static final Map<String, String> SCALAR_FQN = Map.ofEntries(
            Map.entry("int", "java.lang.Integer"),
            Map.entry("integer", "java.lang.Integer"),
            Map.entry("long", "java.lang.Long"),
            Map.entry("double", "java.lang.Double"),
            Map.entry("float", "java.lang.Float"),
            Map.entry("boolean", "java.lang.Boolean"),
            Map.entry("bool", "java.lang.Boolean"),
            Map.entry("char", "java.lang.Character"),
            Map.entry("character", "java.lang.Character"),
            Map.entry("byte", "java.lang.Byte"),
            Map.entry("short", "java.lang.Short"),
            Map.entry("string", "java.lang.String"),
            Map.entry("str", "java.lang.String"),
            Map.entry("date", "java.util.Date"),
            Map.entry("datetime", "java.util.Date"),
            Map.entry("localdate", "java.util.Date"),
            Map.entry("localdatetime", "java.util.Date"),
            Map.entry("timestamp", "java.util.Date"));

    /** 枚举 → JVM 全限定类名（启发推断项无声明类型时按枚举兜底，与 FunctionParamTypeEnum.getVale() 同源） */
    private static final Map<FunctionParamTypeEnum, String> JDK_FQN_BY_ENUM = Map.ofEntries(
            Map.entry(FunctionParamTypeEnum.OBJECT, "java.lang.Object"),
            Map.entry(FunctionParamTypeEnum.STRING, "java.lang.String"),
            Map.entry(FunctionParamTypeEnum.INTEGER, "java.lang.Integer"),
            Map.entry(FunctionParamTypeEnum.LONG, "java.lang.Long"),
            Map.entry(FunctionParamTypeEnum.FLOAT, "java.lang.Float"),
            Map.entry(FunctionParamTypeEnum.DOUBLE, "java.lang.Double"),
            Map.entry(FunctionParamTypeEnum.BOOL, "java.lang.Boolean"),
            Map.entry(FunctionParamTypeEnum.BYTE, "java.lang.Byte"),
            Map.entry(FunctionParamTypeEnum.SHORT, "java.lang.Short"),
            Map.entry(FunctionParamTypeEnum.CHAR, "java.lang.Character"),
            Map.entry(FunctionParamTypeEnum.LIST, "java.util.List"),
            Map.entry(FunctionParamTypeEnum.DATE, "java.util.Date"));

    private ScriptParamSchemaUtil() {
    }

    /**
     * 把 JVM 源码里的类型文本归一化为全限定类名（referenceType）。
     * 已知标量/集合族映射到 JDK 类名；其余（用户类名/已是全限定名）原样返回——
     * 执行链路 Class.forName 失败时自带 Object 兜底，不会报错。
     */
    public static String jvmReferenceType(String rawType) {
        if (StringUtils.isBlank(rawType)) {
            return null;
        }
        String s = rawType.trim();
        // 去泛型后判断数组后缀：String[] / List<String>[] 统一按 java.util.List 承接
        String withoutGeneric = s;
        int angle = withoutGeneric.indexOf('<');
        if (angle > 0) {
            withoutGeneric = withoutGeneric.substring(0, angle);
        }
        if (withoutGeneric.trim().endsWith("[]")) {
            return "java.util.List";
        }
        if (angle > 0) {
            // 保留泛型实参（如 java.util.List<java.lang.String>），SchemaHandleUtil 支持类型表达式
            return s;
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            // 已是全限定名：JDK 名、用户类名都直接返回
            return s;
        }
        String lower = s.toLowerCase();
        if (LIST_TYPE_TEXTS.contains(lower)) {
            return "java.util.List";
        }
        String fqn = SCALAR_FQN.get(lower);
        if (fqn != null) {
            return fqn;
        }
        // object/def/map 等动态写法 → java.lang.Object
        if ("object".equals(lower) || "def".equals(lower) || "map".equals(lower)
                || "hashmap".equals(lower) || "dictionary".equals(lower)) {
            return "java.lang.Object";
        }
        // FunctionParamTypeEnum.getVale() 产出的简单类名（Integer/List/Date...）：先查 JDK 映射表
        FunctionParamTypeEnum mapped = FunctionParamTypeEnum.FUNC_PARAM_TYPE_MAP.get(s);
        if (mapped != null) {
            return JDK_FQN_BY_ENUM.get(mapped);
        }
        // 兼容小写开头的未知简单名按 java.lang. 装箱族尝试无望，统一原样返回（执行侧 Class.forName 失败按 Object 兜底）
        return s;
    }
}
