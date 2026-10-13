package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.model.param.CompositeParameter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析转换节点脚本上的端口注解（{@code // @in name:type} / {@code // @out name:type}），
 * 与原型 functionOperatorCompositeLogic.ts 的 parseTransformScriptPorts 保持一致，
 * 使服务端校验出的端口与前端画布展示对齐。
 * <p>无注解时按脚本约定推导：函数签名（{@code function transform(...)}、
 * {@code const transform = (...) => ...}、简写箭头 {@code const transform = x => ...}）
 * 决定输入端口，最后一个非空 {@code return { ... }} 字面量的键决定输出端口，
 * 解析失败的一侧回退 data / result。</p>
 * <p>本实现是原型规则的可解析超集：额外兼容任意函数名（原型只识别 transform）
 * 与多参数签名、以及末尾的简写属性；这些差异只会让服务端识别出更多端口，
 * 不会与注解端口（画布展示的主路径）冲突。</p>
 */
public final class CompositeTransformPortParser {

    /** 原型默认转换脚本，前端未填写脚本时按该脚本推导端口 */
    public static final String DEFAULT_SCRIPT = "\n"
            + "// @in result:object\n"
            + "// @in confidence:number\n"
            + "// @out sensorData:object\n"
            + "// @out timestamp:string\n"
            + "function transform({ result, confidence }) {\n"
            + "  const payload = result && typeof result === \"object\" ? result : { value: result };\n"
            + "  return {\n"
            + "    sensorData: {\n"
            + "      ...payload,\n"
            + "      confidence: typeof confidence === \"number\" ? confidence : Number(confidence) || 0,\n"
            + "    },\n"
            + "    // 卡尔曼滤波要求 string 时间戳；示例用当前时间，也可改为业务字段\n"
            + "    timestamp: new Date().toISOString(),\n"
            + "  };\n"
            + "}\n";

    private static final Pattern ANNOTATION = Pattern.compile(
            "^\\s*//\\s*@(in|out)\\s+([A-Za-z_$][\\w$]*)\\s*:\\s*([A-Za-z_$][\\w$]*)\\s*$", Pattern.MULTILINE);

    /** function name({...}) / function name(a, b)（服务端兼容任意函数名与多参数签名） */
    private static final Pattern FUNCTION_PARAMS = Pattern.compile(
            "function\\s+\\w+\\s*\\(\\s*([^)]*)\\)");

    /** const|let|var name = (arg) => */
    private static final Pattern ARROW_PARAMS = Pattern.compile(
            "(?:const|let|var)\\s+\\w+\\s*=\\s*(?:async\\s*)?\\(\\s*([^)]*)\\)\\s*=>");

    /** const|let|var name = name => */
    private static final Pattern CONCISE_ARROW_PARAMS = Pattern.compile(
            "(?:const|let|var)\\s+\\w+\\s*=\\s*(?:async\\s*)?([A-Za-z_$][\\w$]*)\\s*=>");

    /** const|let|var name = (...) => { 直接返回对象字面量 */
    private static final Pattern ARROW_OBJECT_BODY = Pattern.compile(
            "(?:const|let|var)\\s+\\w+\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z_$][\\w$]*)\\s*=>\\s*\\{");

    /** return { 或 return{ */
    private static final Pattern RETURN_OBJECT = Pattern.compile("return\\s*\\{");

    /** 带引号的对象键：'a' / "a" / 'a': 1 */
    private static final Pattern QUOTED_KEY = Pattern.compile("^(['\"])(.*?)\\1\\s*(?::|$)");

    /** 不带引号的对象键：a / a: 1（键后必须是冒号或到末尾，与原型 (?:|,)(?=:) 判定一致） */
    private static final Pattern BARE_KEY = Pattern.compile("^([A-Za-z_$][\\w$]*)\\s*(?::|$)");

    private static final Set<String> PARAMETER_TYPES = new LinkedHashSet<>(List.of("string", "number", "boolean", "object", "array"));

    private CompositeTransformPortParser() {
    }

    /**
     * 转换节点的输入端口
     */
    public static List<CompositeParameter> inputs(String script) {
        return parse(script).getInputs();
    }

    /**
     * 转换节点的输出端口
     */
    public static List<CompositeParameter> outputs(String script) {
        return parse(script).getOutputs();
    }

    public static Ports parse(String script) {
        String text = script == null ? "" : script;
        Ports annotated = parseAnnotated(text);
        if (annotated != null) {
            return annotated;
        }
        String cleaned = stripComments(text);
        List<String> inputNames = parseInputNames(cleaned);
        List<String> outputNames = parseOutputNames(cleaned);
        Ports ports = new Ports();
        ports.inputs = objectPorts(inputNames.isEmpty() ? List.of("data") : inputNames);
        ports.outputs = objectPorts(outputNames.isEmpty() ? List.of("result") : outputNames);
        return ports;
    }

    private static Ports parseAnnotated(String script) {
        List<CompositeParameter> inputs = new ArrayList<>();
        List<CompositeParameter> outputs = new ArrayList<>();
        Set<String> inputIds = new LinkedHashSet<>();
        Set<String> outputIds = new LinkedHashSet<>();
        Matcher matcher = ANNOTATION.matcher(script);
        while (matcher.find()) {
            String name = matcher.group(2);
            String type = parseType(matcher.group(3));
            if ("in".equals(matcher.group(1))) {
                if (inputIds.add(name)) {
                    inputs.add(port(name, name, type));
                }
            } else if (outputIds.add(name)) {
                outputs.add(port(name, name, type));
            }
        }
        if (inputs.isEmpty() && outputs.isEmpty()) {
            return null;
        }
        Ports ports = new Ports();
        ports.inputs = inputs.isEmpty() ? List.of(port("data", "data", "object")) : inputs;
        ports.outputs = outputs.isEmpty() ? List.of(port("result", "result", "object")) : outputs;
        return ports;
    }

    /** 函数签名 / 箭头函数形参，解构参数取顶层键名 */
    private static List<String> parseInputNames(String cleaned) {
        String param = firstGroup(FUNCTION_PARAMS, cleaned);
        if (param == null) {
            param = firstGroup(ARROW_PARAMS, cleaned);
        }
        if (param == null) {
            param = firstGroup(CONCISE_ARROW_PARAMS, cleaned);
        }
        if (param == null) {
            return new ArrayList<>();
        }
        param = param.trim();
        if (param.isEmpty()) {
            return new ArrayList<>();
        }
        if (param.startsWith("{")) {
            String inner = extractBalancedBlock(param, 0);
            return parseDestructuredKeys(inner == null ? param.substring(1) : inner);
        }
        List<String> names = new ArrayList<>();
        for (String token : splitTopLevel(param)) {
            String name = token.trim();
            if (name.matches("[A-Za-z_$][\\w$]*")) {
                names.add(name);
            }
        }
        return names;
    }

    /** 解构参数键名，与原型 parseDestructuredKeys 一致（嵌套解构的键同样视为端口） */
    private static List<String> parseDestructuredKeys(String block) {
        List<String> keys = new ArrayList<>();
        Matcher matcher = Pattern.compile("([A-Za-z_$][\\w$]*)(?:\\s*:\\s*[A-Za-z_$][\\w$]*)?").matcher(block);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /** 输出端口：优先箭头函数直接返回的对象字面量，否则取最后一个非空 return 字面量的键 */
    private static List<String> parseOutputNames(String cleaned) {
        Matcher arrow = ARROW_OBJECT_BODY.matcher(cleaned);
        if (arrow.find()) {
            String block = extractBalancedBlock(cleaned, arrow.end() - 1);
            if (block != null && !block.contains("return ")) {
                List<String> keys = parseKeys(block);
                if (!keys.isEmpty()) {
                    return keys;
                }
            }
        }
        List<String> lastKeys = new ArrayList<>();
        Matcher matcher = RETURN_OBJECT.matcher(cleaned);
        while (matcher.find()) {
            String block = extractBalancedBlock(cleaned, matcher.end() - 1);
            if (block != null) {
                List<String> keys = parseKeys(block);
                if (!keys.isEmpty()) {
                    lastKeys = keys;
                }
            }
        }
        return lastKeys;
    }

    /** 对象字面量/解构参数的顶层键，兼容 {@code a}、{@code a: b}、{@code 'a': 1}、{@code "a": 1} */
    private static List<String> parseKeys(String block) {
        List<String> keys = new ArrayList<>();
        for (String entry : splitTopLevel(block)) {
            String token = entry.trim();
            if (token.isEmpty() || token.startsWith("...")) {
                continue;
            }
            String key = null;
            Matcher quoted = QUOTED_KEY.matcher(token);
            if (quoted.find()) {
                key = quoted.group(2);
            } else {
                Matcher bare = BARE_KEY.matcher(token);
                if (bare.find()) {
                    key = bare.group(1);
                }
            }
            if (key != null && !key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** 按顶层逗号切分，忽略 {} [] () 内部（含嵌套对象）的逗号 */
    private static List<String> splitTopLevel(String block) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < block.length(); index++) {
            char ch = block.charAt(index);
            if (ch == '{' || ch == '[' || ch == '(') {
                depth++;
            } else if (ch == '}' || ch == ']' || ch == ')') {
                depth--;
            }
            if (ch == ',' && depth <= 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        parts.add(current.toString());
        return parts;
    }

    /**
     * 取 openIndex 处左花括号对应的右花括号之间的内容（不含花括号本身），
     * 与原型 extractBalancedBlock 一致，支持任意层级嵌套；括号不闭合时返回 null。
     */
    private static String extractBalancedBlock(String source, int openIndex) {
        if (openIndex < 0 || openIndex >= source.length() || source.charAt(openIndex) != '{') {
            return null;
        }
        int depth = 0;
        for (int index = openIndex; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openIndex + 1, index);
                }
            }
        }
        return null;
    }

    private static String firstGroup(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String stripComments(String script) {
        return script.replaceAll("/\\*[\\s\\S]*?\\*/", "\n").replaceAll("(?m)(^|[^:\\\\])//.*$", "$1");
    }

    private static String parseType(String raw) {
        String type = raw == null ? "" : raw.trim().toLowerCase();
        return PARAMETER_TYPES.contains(type) ? type : "object";
    }

    private static List<CompositeParameter> objectPorts(List<String> names) {
        List<CompositeParameter> ports = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : names) {
            String name = raw == null ? "" : raw.trim();
            if (!name.isEmpty()) {
                unique.add(name);
            }
        }
        for (String name : unique) {
            ports.add(port(name, name, "object"));
        }
        return ports;
    }

    private static CompositeParameter port(String id, String name, String type) {
        return CompositeParameter.builder().id(id).name(name).type(type).description("").build();
    }

    /**
     * 转换节点端口
     */
    public static class Ports {
        private List<CompositeParameter> inputs = new ArrayList<>();
        private List<CompositeParameter> outputs = new ArrayList<>();

        public List<CompositeParameter> getInputs() {
            return inputs;
        }

        public List<CompositeParameter> getOutputs() {
            return outputs;
        }
    }
}
