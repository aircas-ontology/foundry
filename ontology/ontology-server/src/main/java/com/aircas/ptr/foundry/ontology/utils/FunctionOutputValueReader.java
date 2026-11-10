package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 行为出参回写的取值器：把算子返回体按绑定里写的 JsonPath 取成要落库的属性值。
 * <p>
 * 算子不强制按 {@code FunctionResultVO} 返回，返回体形状和前端写的取值路径经常对不上：有没有 {@code data}
 * 外壳、有没有出参名一级、直接返回标量还是包成数组都不固定。字面路径取不到时按这几种差异放宽重取，
 * 只认形状与目标属性类型匹配的值——放宽后把整个容器当成属性值写库比报错更糟。
 * 两种取法都取不到才返回 null，由调用方报错，不静默跳过；字面路径取到过值但形状不匹配时仍按字面值写回，
 * 保持放宽前能写回的继续写回。
 * </p>
 * <p>
 * 路径里写 {@code [*]}（如 {@code $.data[*].longitude}）表示对数组逐元素展开，改由 {@link #readMany} 取一批值，
 * 调用方按批插行；{@link #read} 的口径不受影响。
 * </p>
 */
public final class FunctionOutputValueReader {

    /** 取值路径里的外壳层，脚本算子统一用它包住出参 */
    private static final String ENVELOPE_KEY = "data";

    /** 递归兜底查找的最大深度，防止畸形返回体把栈撑爆 */
    private static final int MAX_SEARCH_DEPTH = 6;

    /** 通配标记：路径里写它就表示「对数组逐元素展开」，一次取出一批值而不是一个值 */
    private static final String WILDCARD = "[*]";

    private static final Configuration SAFE_CONFIG = Configuration.builder().build()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private FunctionOutputValueReader() {
    }

    /**
     * @param outParamName 该绑定行所属的顶层算子出参名；用于在路径里补/删出参名一级，不绑算子时传 null
     * @param propertyType 目标属性的数据类型；只用于在多个候选值里挑形状匹配的那个，不做转换
     */
    public static Object read(String jsonString, String expression, String outParamName,
                              OntologyDataTypeEnum propertyType) {
        var value = readLiteral(jsonString, expression);
        if (value != null && isShapeCompatible(value, propertyType)) {
            return value;
        }
        var tolerant = readTolerant(jsonString, expression, outParamName, propertyType);
        return tolerant != null ? tolerant : value;
    }

    /**
     * 按通配路径批量取值：{@code $.data[*].longitude} 取出数组里每个元素的同一列，供调用方逐元素插行。
     * <p>先按字面数组层找，再按 {@code data} 外壳与出参名的错位放宽找；都没有时按路径里通配后面第一个
     * 字段名在返回体里找一个「元素含该字段」的数组，口径同单值取值的兜底——前端按参数树写的
     * {@code $.result.data[*].x} 与返回体的 {@code $.data.data[*]} 就是同一层的两种叫法，只按字面层级找必然取不到。
     * 兜底要求元素里确有该字段，认不到就返回 null 由调用方报错，不会把别的数组当成这批行。</p>
     *
     * @return 每个元素一列值（元素里没有该字段时该项为 null）；路径不含通配、含多处通配、
     *         或找不到数组时返回 null，由调用方报错
     */
    public static List<Object> readMany(String jsonString, String expression, String outParamName) {
        return readMany(parse(jsonString), expression, outParamName);
    }

    /** 解析返回体；同一返回体的多列取值共用一份解析结果，不是 JSON 时返回 null */
    public static JsonNode parse(String jsonString) {
        try {
            return JSON_MAPPER.readTree(jsonString);
        } catch (Exception e) {
            return null;
        }
    }

    /** 同 {@link #readMany(String, String, String)}，用已解析的返回体，避免逐列重复解析 */
    public static List<Object> readMany(JsonNode root, String expression, String outParamName) {
        int mark = StringUtils.indexOf(expression, WILDCARD);
        if (mark < 0 || StringUtils.contains(expression.substring(mark + WILDCARD.length()), WILDCARD)) {
            return null;
        }
        var prefixSegments = pathSegments(StringUtils.substring(expression, 0, mark));
        var suffixSegments = pathSegments(StringUtils.substring(expression, mark + WILDCARD.length()));
        if (prefixSegments == null || suffixSegments == null) {
            return null;
        }
        if (root == null || root.isNull()) {
            return null;
        }
        var candidates = prefixSegments.isEmpty() ? List.<List<Object>>of(prefixSegments)
                : tolerantSegmentLists(prefixSegments, outParamName);
        for (var candidate : candidates) {
            var arrayNode = walkNode(root, candidate, 0);
            if (arrayNode != null && arrayNode.isArray()) {
                return valuesOf(arrayNode, suffixSegments);
            }
        }
        // 兜底只按「元素里有该字段」定位数组：通配后没有字段层（整包当一个值）或按下标取元素时形状认不出来，宁可不猜
        if (suffixSegments.isEmpty() || !(suffixSegments.get(0) instanceof String elementKey)) {
            return null;
        }
        var arrayNode = findArrayNode(root, elementKey, 0);
        return arrayNode == null ? null : valuesOf(arrayNode, suffixSegments);
    }

    /** 数组逐元素取同一列：元素里没有该字段时该项为 null，由调用方判定整批是否取到值 */
    private static List<Object> valuesOf(JsonNode arrayNode, List<Object> suffixSegments) {
        List<Object> values = new ArrayList<>();
        for (var element : arrayNode) {
            var found = walkNode(element, suffixSegments, 0);
            values.add(found == null ? null : JSON_MAPPER.convertValue(found, Object.class));
        }
        return values;
    }

    /**
     * 按「元素是含该字段的对象」在返回体里找数组，按文档顺序取最外层的一个。
     * <p>空数组不算命中：兜底无法从形状认出它就是要的那批行，认错了会插出一批来自别的数组的行。</p>
     */
    private static JsonNode findArrayNode(JsonNode node, String elementKey, int depth) {
        if (node == null || depth > MAX_SEARCH_DEPTH) {
            return null;
        }
        if (node.isArray() && isTargetArray(node, elementKey)) {
            return node;
        }
        if (node.isObject()) {
            for (var field : node.properties()) {
                var found = findArrayNode(field.getValue(), elementKey, depth + 1);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (node.isArray()) {
            for (var element : node) {
                var found = findArrayNode(element, elementKey, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** 数组是否就是「一批行」：任一个对象元素里有该字段（首个元素是空对象时也认得出） */
    private static boolean isTargetArray(JsonNode arrayNode, String elementKey) {
        for (var element : arrayNode) {
            if (element.isObject() && element.has(elementKey)) {
                return true;
            }
        }
        return false;
    }

    /** 绑定路径里是否写了通配展开 */
    public static boolean hasWildcard(String expression) {
        return StringUtils.contains(expression, WILDCARD);
    }

    /** 通配循环所在的数组层（第一个 {@code [*]} 之前的文本）：同一批列靠它归成一组，非通配路径返回 null */
    public static String loopPrefix(String expression) {
        int mark = StringUtils.indexOf(expression, WILDCARD);
        return mark < 0 ? null : StringUtils.substring(expression, 0, mark);
    }

    /**
     * 通配路径的格式校验：返回问题描述，格式可用时返回 null。
     * <p>创建时就能认出的写法错误先拦住，不留到执行链路取不到值才报：多处通配定位不到同一批元素，
     * 通配后没有字段层则每个元素是一整包对象，写不进单个属性列。</p>
     */
    public static String wildcardPathProblem(String expression) {
        if (StringUtils.isEmpty(expression) || !hasWildcard(expression)) {
            return null;
        }
        int mark = StringUtils.indexOf(expression, WILDCARD);
        if (StringUtils.contains(expression.substring(mark + WILDCARD.length()), WILDCARD)) {
            return "只能写一处 [*]，多处通配定位不到同一批元素";
        }
        var prefixSegments = pathSegments(StringUtils.substring(expression, 0, mark));
        var suffixSegments = pathSegments(StringUtils.substring(expression, mark + WILDCARD.length()));
        if (prefixSegments == null || suffixSegments == null) {
            return "路径无法解析，只支持 $.字段 与 [下标] 写法";
        }
        if (suffixSegments.isEmpty() || !(suffixSegments.get(0) instanceof String)) {
            return "通配后要写到元素内的具体字段（如 " + expression + ".字段名），整包对象写不进单个属性列";
        }
        return null;
    }

    /** 值的形状是否与目标类型匹配，供调用方在写回前校验；数组里给标量属性取到一个元素对象会写坏整行 */
    public static boolean shapeMatches(Object value, OntologyDataTypeEnum propertyType) {
        return isShapeCompatible(value, propertyType);
    }

    /** 通配前后两段的路径片段：空文本按根节点处理，写法无法解析时返回 null */
    private static List<Object> pathSegments(String pathText) {
        if (StringUtils.isBlank(pathText) || StringUtils.equals(pathText.trim(), "$")) {
            return List.of();
        }
        return splitJsonPath(pathText);
    }

    /** 字面路径取值：与改造前完全同一口径，取不到或返回体不是 JSON 时返回 null */
    private static Object readLiteral(String jsonString, String expression) {
        try {
            return JsonPath.using(SAFE_CONFIG).parse(jsonString).read(expression);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 放宽取值，按可能性从高到低依次尝试：
     * <ol>
     *   <li>路径里的 {@code data} 层与返回体外壳错位（多一层/少一层）；</li>
     *   <li>出参名一级缺失或多出（脚本直接返回该出参的值时常见）；</li>
     *   <li>沿途遇到数组就进元素取（算子把结果包成数组返回）；</li>
     *   <li>整个返回体就是标量，直接当出参值；</li>
     *   <li>以上都没有，按路径最后一段的字段名在返回体里递归找。</li>
     * </ol>
     * 只认形状与目标属性类型匹配的值：放宽后取到一个容器（比如把 {@code $.data} 整包当成属性值）比报错更糟，
     * 这种情况交给调用方报错，不静默写错数据。
     */
    private static Object readTolerant(String jsonString, String expression, String outParamName,
                                       OntologyDataTypeEnum propertyType) {
        var segments = splitJsonPath(expression);
        if (segments == null) {
            return null;
        }
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(jsonString);
        } catch (Exception e) {
            return null;
        }
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.isValueNode()) {
            var whole = JSON_MAPPER.convertValue(root, Object.class);
            return isShapeCompatible(whole, propertyType) ? whole : null;
        }
        for (var candidate : tolerantSegmentLists(segments, outParamName)) {
            var value = navigate(root, candidate, 0);
            if (value != null && isShapeCompatible(value, propertyType)) {
                return value;
            }
        }
        // 兜底：按路径最后一段的字段名在返回体里递归找，只认形状匹配的值，所以同名层级取错也只会取到类型对得上的那个
        var leafKey = lastKey(segments);
        if (leafKey == null) {
            return null;
        }
        return findByKey(root, leafKey, propertyType, 0);
    }

    /**
     * 由字面路径派生的候选段序列：{@code data} 外壳层与出参名层各自去掉/补上后的组合，按离字面路径由近到远排。
     * <p>一层都不剩的组合（如路径只有 {@code $.data}）不参与，避免把整个返回体当出参值。</p>
     */
    private static List<List<Object>> tolerantSegmentLists(List<Object> segments, String outParamName) {
        List<List<Object>> dataVariants = new ArrayList<>();
        addDistinct(dataVariants, segments);
        addDistinct(dataVariants, removeLeading(segments, ENVELOPE_KEY));
        if (!startsWith(segments, ENVELOPE_KEY)) {
            addDistinct(dataVariants, prepend(segments, ENVELOPE_KEY));
        }
        List<List<Object>> res = new ArrayList<>();
        for (var dataVariant : dataVariants) {
            if (dataVariant.isEmpty()) {
                continue;
            }
            for (var nameVariant : nameVariants(dataVariant, outParamName)) {
                addDistinct(res, nameVariant);
            }
        }
        return res;
    }

    /** 出参名一层的变化：路径里写了这一级就再多试一个去掉它的版本，没写就在 data 之后（或开头）补上再试 */
    private static List<List<Object>> nameVariants(List<Object> segments, String outParamName) {
        if (StringUtils.isEmpty(outParamName)) {
            return List.of(segments);
        }
        if (segments.contains(outParamName)) {
            var without = removeKey(segments, outParamName);
            return without.isEmpty() ? List.of(segments) : List.of(segments, without);
        }
        return List.of(segments, insertName(segments, outParamName));
    }

    /** 把出参名插到第一层业务节点上：有 data 外壳就插在它之后，否则插在开头 */
    private static List<Object> insertName(List<Object> segments, String outParamName) {
        List<Object> res = new ArrayList<>(segments);
        res.add(startsWith(segments, ENVELOPE_KEY) ? 1 : 0, outParamName);
        return res;
    }

    private static void addDistinct(List<List<Object>> variants, List<Object> variant) {
        if (!variants.contains(variant)) {
            variants.add(variant);
        }
    }

    /** 沿段序列取值：与 {@link #walkNode} 同口径，终点再转成普通 Object */
    private static Object navigate(JsonNode node, List<Object> segments, int index) {
        var found = walkNode(node, segments, index);
        return found == null ? null : JSON_MAPPER.convertValue(found, Object.class);
    }

    /** 沿段序列取节点：对象按 key，数组逐个元素试（取第一个命中节点）；段用尽即当前节点 */
    private static JsonNode walkNode(JsonNode node, List<Object> segments, int index) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (index >= segments.size()) {
            return node;
        }
        var segment = segments.get(index);
        if (node.isArray()) {
            for (var element : node) {
                var value = walkNode(element, segments, index);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        if (segment instanceof Integer i) {
            return walkNode(node.get(i), segments, index + 1);
        }
        return walkNode(node.get(String.valueOf(segment)), segments, index + 1);
    }

    /** 按字段名在返回体里递归找，只认形状与目标属性类型匹配的值 */
    private static Object findByKey(JsonNode node, String key, OntologyDataTypeEnum propertyType, int depth) {
        if (node == null || node.isNull() || depth > MAX_SEARCH_DEPTH) {
            return null;
        }
        if (node.isObject()) {
            var direct = node.get(key);
            if (direct != null && !direct.isNull()) {
                var value = JSON_MAPPER.convertValue(direct, Object.class);
                if (isShapeCompatible(value, propertyType)) {
                    return value;
                }
            }
            return searchChildren(node, key, propertyType, depth + 1);
        }
        if (node.isArray()) {
            return searchChildren(node, key, propertyType, depth + 1);
        }
        return null;
    }

    private static Object searchChildren(JsonNode node, String key, OntologyDataTypeEnum propertyType, int depth) {
        if (node.isObject()) {
            for (var field : node.properties()) {
                var found = findByKey(field.getValue(), key, propertyType, depth);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (var element : node) {
                var found = findByKey(element, key, propertyType, depth);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** 值的形状是否与目标属性类型匹配：数组/向量类要列表、Map 与对象类要对象，其余要标量 */
    private static boolean isShapeCompatible(Object value, OntologyDataTypeEnum propertyType) {
        if (value == null) {
            return false;
        }
        if (propertyType == null) {
            return true;
        }
        return switch (propertyType) {
            case Array, Vector, TimeSeries -> value instanceof List;
            case Map, Ontology -> value instanceof Map;
            default -> !(value instanceof List) && !(value instanceof Map);
        };
    }

    /** 解析成段序列：字符串是字段名、整数是下标；含通配符等无法解析的片段返回 null */
    private static List<Object> splitJsonPath(String expression) {
        String path = StringUtils.trimToEmpty(expression);
        if (path.startsWith("$")) {
            path = path.substring(1);
        }
        if (path.startsWith(".")) {
            path = path.substring(1);
        }
        if (StringUtils.isEmpty(path)) {
            return null;
        }
        List<Object> segments = new ArrayList<>();
        int i = 0;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == '.') {
                i++;
                continue;
            }
            if (c == '[') {
                int end = path.indexOf(']', i);
                if (end < 0) {
                    return null;
                }
                String token = StringUtils.strip(path.substring(i + 1, end), "'\"");
                if (StringUtils.isEmpty(token) || token.contains("*") || token.contains("?") || token.contains(",")) {
                    return null;
                }
                if (StringUtils.isNumeric(token)) {
                    segments.add(Integer.valueOf(token));
                } else {
                    segments.add(token);
                }
                i = end + 1;
                continue;
            }
            int next = Math.min(indexOfNext(path, '.', i + 1), indexOfNext(path, '[', i + 1));
            if (next >= path.length()) {
                segments.add(path.substring(i));
                break;
            }
            segments.add(path.substring(i, next));
            i = next;
        }
        return segments.isEmpty() ? null : segments;
    }

    private static int indexOfNext(String path, char target, int from) {
        int index = path.indexOf(target, from);
        return index < 0 ? path.length() : index;
    }

    /** 路径最后一段的字段名；下标片段返回 null */
    private static String lastKey(List<Object> segments) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        var last = segments.get(segments.size() - 1);
        return last instanceof String key ? key : null;
    }

    private static boolean startsWith(List<Object> segments, String key) {
        return !segments.isEmpty() && key.equals(segments.get(0));
    }

    private static List<Object> prepend(List<Object> segments, String key) {
        List<Object> res = new ArrayList<>(segments.size() + 1);
        res.add(key);
        res.addAll(segments);
        return res;
    }

    private static List<Object> removeLeading(List<Object> segments, String key) {
        return startsWith(segments, key) ? new ArrayList<>(segments.subList(1, segments.size())) : segments;
    }

    private static List<Object> removeKey(List<Object> segments, String key) {
        List<Object> res = new ArrayList<>(segments.size());
        segments.forEach(segment -> {
            if (!key.equals(segment)) {
                res.add(segment);
            }
        });
        return res;
    }
}
