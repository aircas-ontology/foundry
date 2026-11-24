package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
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

    /** 集合/映射族裸类型 → JDK 全限定名（泛型实参补全时使用，如 List<Map<..>> 的内层） */
    private static final Set<String> SET_TYPE_TEXTS = Set.of("set", "hashset", "linkedhashset", "treeset");

    private static final Set<String> MAP_TYPE_TEXTS = Set.of("map", "hashmap", "linkedhashmap", "dictionary");

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
            // 保留泛型实参并补全全限定名（List<Double> → java.util.List<java.lang.Double>），
            // SchemaHandleUtil 支持类型表达式；元素类不可解析（脚本内类等）时收敛到集合裸类型
            String qualified = qualifyGenericType(s);
            if (qualified != null) {
                return qualified;
            }
            String base = baseFqn(s.substring(0, angle));
            return base != null ? base : s;
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

    /**
     * 参数类型的展示文本：库里的类型仍是枚举（{@code LIST}），集合参数返回时按
     * {@code type_reference_name} 的泛型实参拼成 {@code List<Double>}，裸 List 无法还原元素时仍返回 {@code LIST}。
     */
    public static String displayParamType(FunctionParamTypeEnum paramType, String typeReferenceName) {
        if (paramType == null || paramType == FunctionParamTypeEnum.LIST) {
            String simple = simpleTypeText(typeReferenceName);
            if (simple != null && simple.indexOf('<') >= 0) {
                return simple;
            }
        }
        return paramType == null ? null : paramType.name();
    }

    /**
     * 展示用的简化类型文本：去掉各层包名、保留泛型与数组结构
     * （{@code java.util.List<java.lang.Double>} → {@code List<Double>}）。仅用于界面展示，不参与类型转换。
     */
    public static String simpleTypeText(String typeText) {
        if (StringUtils.isBlank(typeText)) {
            return null;
        }
        String s = typeText.trim();
        if (s.endsWith("[]")) {
            String component = simpleTypeText(s.substring(0, s.length() - 2));
            return component == null ? null : component + "[]";
        }
        int angle = s.indexOf('<');
        if (angle < 0) {
            int dot = s.lastIndexOf('.');
            return dot >= 0 ? s.substring(dot + 1) : s;
        }
        if (!s.endsWith(">")) {
            return s;
        }
        String raw = s.substring(0, angle).trim();
        int dot = raw.lastIndexOf('.');
        if (dot >= 0) {
            raw = raw.substring(dot + 1);
        }
        List<String> args = splitTopLevelArguments(s.substring(angle + 1, s.length() - 1));
        StringBuilder sb = new StringBuilder(raw).append('<');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(simpleTypeText(args.get(i)));
        }
        return sb.append('>').toString();
    }

    /**
     * 泛型类型文本的全限定化：逐层补全裸类型与实参（{@code List<Double>} → {@code java.util.List<java.lang.Double>}），
     * 已是全限定名或 JDK 类名的部分原样保留；任一实参认不出（脚本内类等）返回 null，由调用方收敛到裸类型。
     */
    private static String qualifyGenericType(String typeText) {
        String s = typeText.trim();
        if (s.endsWith("[]")) {
            String component = qualifyGenericType(s.substring(0, s.length() - 2));
            return component == null ? null : component + "[]";
        }
        int angle = s.indexOf('<');
        if (angle < 0) {
            return baseFqn(s);
        }
        String raw = baseFqn(s.substring(0, angle));
        if (raw == null || !s.endsWith(">")) {
            return null;
        }
        List<String> args = splitTopLevelArguments(s.substring(angle + 1, s.length() - 1));
        StringBuilder sb = new StringBuilder(raw).append('<');
        for (int i = 0; i < args.size(); i++) {
            String qualified = qualifyGenericType(args.get(i));
            if (qualified == null) {
                return null;
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(qualified);
        }
        return sb.append('>').toString();
    }

    /** 裸类型文本 → 全限定名：已带包名原样返回，JDK 标量/集合族查表，其余（脚本内类等）返回 null。 */
    private static String baseFqn(String typeText) {
        String name = typeText == null ? "" : typeText.trim();
        if (name.isEmpty()) {
            return null;
        }
        if (name.indexOf('.') >= 0) {
            return name;
        }
        String lower = name.toLowerCase();
        if (SET_TYPE_TEXTS.contains(lower)) {
            return "java.util.Set";
        }
        if (MAP_TYPE_TEXTS.contains(lower)) {
            return "java.util.Map";
        }
        if (LIST_TYPE_TEXTS.contains(lower)) {
            return "java.util.List";
        }
        return SCALAR_FQN.get(lower);
    }

    private static List<String> splitTopLevelArguments(String argsPart) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < argsPart.length(); i++) {
            char c = argsPart.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(argsPart.substring(start, i));
                start = i + 1;
            }
        }
        args.add(argsPart.substring(start));
        return args;
    }
}
