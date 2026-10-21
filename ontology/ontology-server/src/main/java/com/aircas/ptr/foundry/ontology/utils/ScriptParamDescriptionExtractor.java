package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本注释参数描述提取器：从脚本源码注释里提取入参/出参（含展开字段）与返回值的说明文本，
 * 用于补全脚本参数静态解析结果的 {@code description}。
 * <p>
 * 四语言共用一套纯文本扫描（不各写一遍 AST）：解析器负责"认出参数"，而参数说明本就活在注释里，
 * 只需按语言差异配置注释符号与声明写法即可；Python/TypeScript 因此无需改动 runner 镜像。
 * <p>
 * 支持的注释写法（同名取先出现的一条；标签式优先于就近式）：
 * <ol>
 *   <li>Javadoc/JSDoc 标签：{@code @param name 描述}、{@code @param {string} name - 描述}；</li>
 *   <li>Sphinx 标签：{@code :param name: 描述}；</li>
 *   <li>Google 风格 docstring：{@code Args:} 段内 {@code name (str): 描述}；</li>
 *   <li>就近注释：声明行上一行的整段注释，或声明行同行尾部注释，作为该参数/字段描述；</li>
 *   <li>返回值：{@code @return}/{@code @returns}/{@code @result}/{@code :returns:}/{@code Returns:} 段，
 *       用于唯一的标量出参（各语言解析器统一命名为 {@code result}）。</li>
 * </ol>
 * 提取按名字匹配、尽力而为：不同类里的同名字段共用一条描述；超长描述截断到
 * {@value ScriptParamDescriptionExtractor#MAX_DESC_LENGTH} 字符。
 */
public final class ScriptParamDescriptionExtractor {

    /** 单条描述的最大长度，超出截断，避免注释长段落整体入库。 */
    private static final int MAX_DESC_LENGTH = 200;

    /** 各语言标量返回值在解析结果里的统一出参名。 */
    private static final String RESULT_PARAM_NAME = "result";

    /** Javadoc/JSDoc：{@code @param name 描述} / {@code @param {string} name - 描述}。 */
    private static final Pattern PARAM_TAG = Pattern.compile(
            "^@(?:param|parameter)\\s+(?:\\{[^}]*}\\s*)?([\\w$.]+)\\s*(?:[-:：]\\s*)?(.*)$");
    /** Sphinx：{@code :param name: 描述}，兼容 {@code :param str name: 描述}。 */
    private static final Pattern SPHINX_PARAM = Pattern.compile(
            "^:param\\s+(?:[\\w$.]+\\s+)?([\\w$.]+)\\s*:\\s*(.*)$");
    /** 返回值标签：{@code @return 描述} / {@code @result 描述}。 */
    private static final Pattern RETURN_TAG = Pattern.compile("^@(?:returns?|result)\\s+(?:\\{[^}]*}\\s*)?(.*)$");
    /** Sphinx 返回值标签：{@code :returns: 描述}。 */
    private static final Pattern SPHINX_RETURN = Pattern.compile("^:returns?\\s*:\\s*(.*)$");
    /** Google 风格入参段起始。 */
    private static final Pattern ARGS_SECTION = Pattern.compile("^(?:args|arguments|parameters|参数)\\s*[:：]\\s*$",
            Pattern.CASE_INSENSITIVE);
    /** Google 风格出参段起始（允许同行直接跟描述），捕获组为同行描述正文。 */
    private static final Pattern RETURNS_SECTION = Pattern.compile("^(?:returns?|返回(?:值)?|出参)\\s*[:：]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    /** Google 风格出参描述的类型前缀：{@code float: 总额}、{@code List[str]: 名称列表}，只留冒号后的正文。 */
    private static final Pattern RETURNS_TYPE_PREFIX = Pattern.compile(
            "^[A-Za-z_$][\\w$.]*(?:<[^>]*>)?(?:\\[[^\\]]*\\])?(?:\\.\\.\\.)?\\s*[:：]\\s*(.+)$");
    /** 其他 docstring 段落起始：遇到即结束 Args/Returns 上下文。 */
    private static final Pattern OTHER_SECTION = Pattern.compile(
            "^(?:raises?|yields?|example|examples|note|notes|attributes?|see also|异常|注意|示例)\\s*[:：]\\s*$",
            Pattern.CASE_INSENSITIVE);
    /** Google 风格条目：{@code name (str): 描述} 或 {@code name: 描述}。 */
    private static final Pattern ARG_ENTRY = Pattern.compile("^([\\w$]+)\\s*(?:\\([^)]*\\))?\\s*[:：]\\s*(.+)$");

    /** 注释行里出现这些标记说明是结构化标签段，不再整段当作就近描述。 */
    private static final Pattern TAG_MARKER = Pattern.compile("(?:@param|:param|@returns?|:returns?|@result)",
            Pattern.CASE_INSENSITIVE);
    /** 工具指令类行注释（非业务描述），直接跳过。 */
    private static final Pattern TOOL_DIRECTIVE = Pattern.compile(
            "^(?:(?:noqa|type:|noinspection|region|endregion|todo|fixme|FIXME|eslint|fmt:)[\\s:]).*", Pattern.CASE_INSENSITIVE);
    /** 纯分隔符注释（{@code ====}、{@code ----} 等）。 */
    private static final Pattern SEPARATOR_ONLY = Pattern.compile("^[-=*#/_.\\s]+$");

    /** Java/Groovy 声明行：类字段（Groovy 可省分号，故允许行尾结束）、def 字段、多行签名里的形参。 */
    private static final List<Pattern> JVM_DECLARATIONS = List.of(
            Pattern.compile("^(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|private|protected|static|final|transient|volatile)\\s+)*"
                    + "(?:[\\w$.]+(?:<[^>]*>)?(?:\\[\\])?)\\s+([A-Za-z_$][\\w$]*)\\s*(?:;|=[^=]|$)"),
            Pattern.compile("^def\\s+([A-Za-z_$][\\w$]*)\\s*(?::|=)"),
            Pattern.compile("^(?:final\\s+)?(?:[\\w$.]+(?:<[^>]*>)?(?:\\[\\])?)\\s+([A-Za-z_$][\\w$]*)\\s*(?:,\\s*$|\\))"));
    /** TypeScript 声明行：接口/类型别名成员、对象字面量键、带类型的形参。 */
    private static final List<Pattern> TS_DECLARATIONS = List.of(
            Pattern.compile("^(?:readonly\\s+)?([A-Za-z_$][\\w$]*)\\??\\s*:"),
            Pattern.compile("^(?:readonly\\s+)?([A-Za-z_$][\\w$]*)\\s*,\\s*$"));
    /** Python 声明行：注解字段、self 字段、dict 字面量键（含引号键）。 */
    private static final List<Pattern> PYTHON_DECLARATIONS = List.of(
            Pattern.compile("^self\\.([A-Za-z_]\\w*)\\s*[:=]"),
            Pattern.compile("^([A-Za-z_]\\w*)\\s*:"),
            Pattern.compile("^[\"']([A-Za-z_]\\w*)[\"']\\s*:"));

    /** TS 里长得像成员但并非成员的关键字，避免误取描述。 */
    private static final Set<String> TS_NON_MEMBERS = Set.of(
            "default", "case", "public", "private", "protected", "static", "readonly", "new", "return",
            "await", "async", "export", "import", "const", "let", "var", "if", "else", "for", "while",
            "type", "interface", "enum", "function");
    /** Python 关键字，避免把 {@code else:}、{@code for x in:} 之类当成声明。 */
    private static final Set<String> PYTHON_KEYWORDS = Set.of(
            "if", "elif", "else", "for", "while", "return", "yield", "def", "class", "try", "except",
            "finally", "with", "lambda", "import", "from", "pass", "break", "continue", "global",
            "nonlocal", "assert", "raise", "del", "async", "await");

    private ScriptParamDescriptionExtractor() {
    }

    /**
     * 提取结果。
     *
     * @param byName     参数/字段名 → 描述
     * @param returnValue 返回值描述（对应解析出的 {@code result} 出参），无则 null
     */
    public record Descriptions(Map<String, String> byName, String returnValue) {

        /** 按参数名取描述，未命中返回 null。 */
        public String describe(String name) {
            return name == null ? null : byName.get(name);
        }

        /**
         * 取参数描述：优先按名字命中注释；顶层出参命中不到时，若脚本只有一个出参（或统一命名为 {@code result}），
         * 沿用返回值注释——注释里通常只写 {@code @return}，不会再给唯一出参单独命名。
         *
         * @param name        参数/字段名
         * @param output      该参数是否出参
         * @param topLevel    是否顶层参数，嵌套字段不参与返回值兜底
         * @param outputCount 顶层出参数量
         */
        public String describe(String name, boolean output, boolean topLevel, long outputCount) {
            String description = describe(name);
            if (description != null) {
                return description;
            }
            boolean reuseReturn = output && topLevel && (outputCount <= 1 || RESULT_PARAM_NAME.equals(name));
            return reuseReturn ? returnValue : null;
        }

        public static Descriptions empty() {
            return new Descriptions(Map.of(), null);
        }
    }

    /**
     * 从脚本源码注释中提取参数描述。
     *
     * @param language 脚本语言（决定注释符号与声明写法），null 按 Groovy 处理
     * @param code     脚本源码
     * @return 提取结果，永不为 null；无注释时返回空集合
     */
    public static Descriptions extract(FunctionLanguageEnum language, String code) {
        if (code == null || code.isBlank()) {
            return Descriptions.empty();
        }
        FunctionLanguageEnum lang = language == null ? FunctionLanguageEnum.GROOVY : language;
        Scan scan = scan(lang, code);
        Map<String, String> tagged = new LinkedHashMap<>();
        String returnValue = collectTagged(scan.regions(), tagged);
        Map<String, String> byName = collectAdjacent(scan, lang, tagged.keySet());
        byName.putAll(tagged);
        return new Descriptions(byName, returnValue);
    }

    // ==================== 注释区域扫描 ====================

    /** 一段注释：清洗后的文本行 + 起止行号（0 基）。 */
    private record Region(List<String> lines, int startLine, int endLine) {

        /** 整段拼成一行文本，空段返回 null。 */
        String text() {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(line);
            }
            String joined = sb.toString().trim();
            if (joined.isEmpty()) {
                return null;
            }
            return joined.length() > MAX_DESC_LENGTH ? joined.substring(0, MAX_DESC_LENGTH) : joined;
        }
    }

    /** 扫描产物：源码行、注释区域、纯注释行下标集合。 */
    private record Scan(List<String> lines, List<Region> regions, Set<Integer> commentOnly) {
    }

    /**
     * 逐行扫描注释区域，跳过字符串字面量：行注释、块注释（JVM/TS）、三引号文档串（Python）。
     */
    private static Scan scan(FunctionLanguageEnum language, String code) {
        boolean python = language == FunctionLanguageEnum.PYTHON;
        List<String> lines = List.of(code.split("\r?\n", -1));
        List<Region> regions = new ArrayList<>();
        Set<Integer> commentOnly = new LinkedHashSet<>();
        List<String> block = null;
        String blockClose = null;
        int blockStart = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.stripLeading();
            if (block == null && (trimmed.startsWith(python ? "#" : "//")
                    || (!python && trimmed.startsWith("/*")))) {
                commentOnly.add(i);
            } else if (block != null) {
                commentOnly.add(i);
            }
            int pos = 0;
            while (pos <= line.length()) {
                if (block != null) {
                    int end = line.indexOf(blockClose, pos);
                    if (end < 0) {
                        block.add(clean(line.substring(pos)));
                        break;
                    }
                    block.add(clean(line.substring(pos, end)));
                    regions.add(new Region(block, blockStart, i));
                    int resume = end + blockClose.length();
                    block = null;
                    blockClose = null;
                    pos = resume;
                    continue;
                }
                if (pos == line.length()) {
                    break;
                }
                char c = line.charAt(pos);
                // Python 三引号文档串
                if (python && (line.startsWith("\"\"\"", pos) || line.startsWith("'''", pos))) {
                    String token = line.substring(pos, pos + 3);
                    int end = line.indexOf(token, pos + 3);
                    if (end < 0) {
                        block = new ArrayList<>();
                        blockClose = token;
                        blockStart = i;
                        block.add(clean(line.substring(pos + 3)));
                        break;
                    }
                    regions.add(new Region(List.of(clean(line.substring(pos + 3, end))), i, i));
                    pos = end + 3;
                    continue;
                }
                // 字符串字面量整段跳过（跨行字符串按行截断，用户脚本极少出现）
                if (c == '"' || c == '\'' || (!python && c == '`')) {
                    int end = closingQuote(line, pos);
                    if (end < 0) {
                        break;
                    }
                    pos = end + 1;
                    continue;
                }
                // 块注释
                if (!python && c == '/' && line.startsWith("/*", pos)) {
                    int end = line.indexOf("*/", pos + 2);
                    if (end < 0) {
                        block = new ArrayList<>();
                        blockClose = "*/";
                        blockStart = i;
                        block.add(clean(line.substring(pos + 2)));
                        break;
                    }
                    regions.add(new Region(List.of(clean(line.substring(pos + 2, end))), i, i));
                    pos = end + 2;
                    continue;
                }
                // 行注释
                if (isLineComment(line, pos, python)) {
                    regions.add(new Region(List.of(clean(line.substring(pos + (python ? 1 : 2)))), i, i));
                    break;
                }
                pos++;
            }
        }
        if (block != null) {
            regions.add(new Region(block, blockStart, lines.size() - 1));
        }
        return new Scan(lines, regions, commentOnly);
    }

    /** 找引号的闭合位置，支持反斜杠转义；未闭合返回 -1。 */
    private static int closingQuote(String line, int start) {
        char quote = line.charAt(start);
        for (int i = start + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == quote) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isLineComment(String line, int pos, boolean python) {
        if (python) {
            return line.charAt(pos) == '#';
        }
        return line.charAt(pos) == '/' && pos + 1 < line.length() && line.charAt(pos + 1) == '/';
    }

    /** 去掉注释标记与前导星号。 */
    private static String clean(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.startsWith("*")) {
            s = s.substring(1).strip();
        }
        return s;
    }

    // ==================== 标签式描述 ====================

    /**
     * 扫描注释区域里的标签与 docstring 段落，产出"参数名 → 描述"，并返回返回值描述。
     */
    private static String collectTagged(List<Region> regions, Map<String, String> tagged) {
        String returnValue = null;
        for (Region region : regions) {
            String lastKey = null;
            boolean lastIsReturn = false;
            boolean inArgs = false;
            boolean inReturns = false;
            for (String line : region.lines()) {
                if (line.isBlank()) {
                    lastKey = null;
                    inArgs = false;
                    inReturns = false;
                    continue;
                }
                Matcher param = PARAM_TAG.matcher(line);
                if (!param.matches()) {
                    param = SPHINX_PARAM.matcher(line);
                }
                if (param.matches()) {
                    lastKey = simpleName(param.group(1));
                    lastIsReturn = false;
                    inArgs = false;
                    put(tagged, lastKey, param.group(2));
                    continue;
                }
                Matcher ret = RETURN_TAG.matcher(line);
                if (!ret.matches()) {
                    ret = SPHINX_RETURN.matcher(line);
                }
                if (ret.matches()) {
                    lastKey = null;
                    lastIsReturn = true;
                    inArgs = false;
                    returnValue = append(returnValue, ret.group(1));
                    continue;
                }
                if (ARGS_SECTION.matcher(line).matches()) {
                    inArgs = true;
                    inReturns = false;
                    lastKey = null;
                    continue;
                }
                Matcher retSection = RETURNS_SECTION.matcher(line);
                if (retSection.matches()) {
                    inReturns = true;
                    inArgs = false;
                    lastKey = null;
                    String inline = retSection.group(1).trim();
                    if (!inline.isEmpty()) {
                        returnValue = append(returnValue, stripReturnTypePrefix(inline));
                    }
                    continue;
                }
                if (OTHER_SECTION.matcher(line).matches()) {
                    inArgs = false;
                    inReturns = false;
                    lastKey = null;
                    continue;
                }
                if (inArgs) {
                    Matcher entry = ARG_ENTRY.matcher(line);
                    if (entry.matches()) {
                        lastKey = simpleName(entry.group(1));
                        put(tagged, lastKey, entry.group(2));
                        continue;
                    }
                }
                if (inReturns) {
                    returnValue = append(returnValue, stripReturnTypePrefix(line));
                    continue;
                }
                // 标签描述的换行续行
                if (lastKey != null) {
                    put(tagged, lastKey, join(tagged.get(lastKey), line));
                    continue;
                }
                if (lastIsReturn) {
                    returnValue = append(returnValue, line);
                }
            }
        }
        tagged.values().removeIf(v -> v == null || v.isBlank());
        return returnValue == null || returnValue.isBlank() ? null : returnValue;
    }

    /** 去掉出参描述的类型前缀（{@code float: 总额} → {@code 总额}），无类型前缀时原样返回。 */
    private static String stripReturnTypePrefix(String line) {
        Matcher typed = RETURNS_TYPE_PREFIX.matcher(line);
        return typed.matches() ? typed.group(1) : line;
    }

    private static void put(Map<String, String> map, String name, String desc) {
        if (name == null || name.isEmpty()) {
            return;
        }
        if (map.containsKey(name)) {
            return;
        }
        map.put(name, desc == null ? "" : desc.trim());
    }

    private static String join(String base, String extra) {
        if (base == null || base.isBlank()) {
            return extra == null ? "" : extra.trim();
        }
        if (extra == null || extra.isBlank()) {
            return base;
        }
        String merged = base + " " + extra.trim();
        return merged.length() > MAX_DESC_LENGTH ? merged.substring(0, MAX_DESC_LENGTH) : merged;
    }

    private static String append(String base, String extra) {
        return join(base, extra);
    }

    /** 点号路径取末段：{@code input.startTime} → {@code startTime}。 */
    private static String simpleName(String name) {
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    // ==================== 就近注释描述 ====================

    /**
     * 声明行相邻注释（上一行整段 / 同行尾部）作为该参数、字段描述；标签式已命中的名字不覆盖。
     */
    private static Map<String, String> collectAdjacent(Scan scan, FunctionLanguageEnum language, Set<String> taggedNames) {
        Map<String, String> adjacent = new LinkedHashMap<>();
        // 注释区域按结束行索引，便于查"紧挨声明行上方"的那一段
        Map<Integer, Region> endingAt = new LinkedHashMap<>();
        Map<Integer, Region> startingAt = new LinkedHashMap<>();
        for (Region region : scan.regions()) {
            endingAt.put(region.endLine(), region);
            startingAt.put(region.startLine(), region);
        }
        for (int i = 0; i < scan.lines().size(); i++) {
            if (scan.commentOnly().contains(i)) {
                continue;
            }
            String name = declaredName(scan.lines().get(i), language);
            if (name == null || taggedNames.contains(name) || adjacent.containsKey(name)) {
                continue;
            }
            Region region = i > 0 ? endingAt.get(i - 1) : null;
            if (region == null) {
                region = startingAt.get(i);
            }
            String desc = nearbyText(region);
            if (desc != null) {
                adjacent.put(name, desc);
            }
        }
        return adjacent;
    }

    /** 就近注释文本：结构化标签段、工具指令、纯分隔符都不作描述。 */
    private static String nearbyText(Region region) {
        if (region == null) {
            return null;
        }
        String text = region.text();
        if (text == null || TAG_MARKER.matcher(text).find() || TOOL_DIRECTIVE.matcher(text).find()
                || SEPARATOR_ONLY.matcher(text).matches()) {
            return null;
        }
        return text;
    }

    /** 从声明行提取被声明的标识符（参数名/字段名），非声明行返回 null。 */
    private static String declaredName(String line, FunctionLanguageEnum language) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        List<Pattern> patterns;
        Set<String> denies;
        switch (language) {
            case PYTHON -> {
                patterns = PYTHON_DECLARATIONS;
                denies = PYTHON_KEYWORDS;
            }
            case TYPESCRIPT -> {
                patterns = TS_DECLARATIONS;
                denies = TS_NON_MEMBERS;
            }
            default -> {
                patterns = JVM_DECLARATIONS;
                denies = Set.of();
            }
        }
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(trimmed);
            if (matcher.find()) {
                String name = matcher.group(1);
                if (name != null && !denies.contains(name)) {
                    return name;
                }
            }
        }
        return null;
    }
}
