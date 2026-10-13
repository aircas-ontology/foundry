package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 函数编排"转换节点"脚本执行器。
 *
 * <p>原型画布上的转换节点默认使用 JavaScript（{@code language: "javascript"}），脚本形如
 * {@code function transform({ result, confidence }) { ... return { ... }; }}；同时兼容 Groovy/Java 写法。
 * 执行约定：</p>
 * <ul>
 *   <li>脚本里显式声明了函数时，按形参名从入参中取值调用该函数，函数返回值即节点出参；形参是解构写法
 *       （{@code {a, b}}）或唯一形参未命中任何入参名时，把整体入参对象作为实参传入；</li>
 *   <li>脚本没有声明函数时，直接执行脚本体：Groovy 取最后一个表达式的值，JavaScript 需在脚本里写 {@code return}。</li>
 * </ul>
 *
 * <p>JavaScript 通过外部 {@code node} 可执行文件运行（JDK17 已移除 Nashorn，依赖里也没有
 * Rhino/GraalJS）。可执行文件路径与超时时间通过配置项调整，未安装 Node 时给出明确的中文报错。</p>
 */
@Slf4j
@Component
public class CompositeTransformExecutor {

    /** JavaScript 语言标识（原型默认） */
    private static final String LANGUAGE_JAVASCRIPT = "javascript";

    /** Groovy 语言标识（空值也按 Groovy 处理，与原子函数保持一致） */
    private static final String LANGUAGE_GROOVY = "groovy";

    /** 声明式函数：function name(...) / def name(...) / const name = (...) => */
    private static final Pattern DECLARED_FUNCTION = Pattern.compile(
            "(?:function|def)\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)"
                    + "|(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?\\(([^)]*)\\)\\s*=>");

    /** 控制流关键字，避免被误判为函数声明 */
    private static final Set<String> NOT_FUNCTION_NAMES = Set.of("if", "for", "while", "switch", "catch", "return");

    /** 传给脚本的"整体入参"变量名 */
    private static final String WHOLE_INPUT_NAME = "__input__";

    /** Groovy 编译缓存：脚本内容哈希 -> 编译产物 */
    private final Map<String, CompiledTransform> groovyCache = new ConcurrentHashMap<>();

    private static final int MAX_GROOVY_CACHE_SIZE = 256;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ontology.composite.node-executable:node}")
    private String nodeExecutable = "node";

    @Value("${ontology.composite.script-timeout-seconds:10}")
    private long scriptTimeoutSeconds = 10;

    /**
     * 执行转换脚本。
     *
     * @param language 脚本语言：javascript / groovy / java（空值按 groovy 处理）
     * @param script   脚本内容
     * @param inputs   节点入参：端口名 -&gt; 值
     * @return 脚本返回值，通常是 Map，由调用方映射到出参端口
     */
    public Object execute(String language, String script, Map<String, Object> inputs) {
        String text = script == null ? "" : script;
        Map<String, Object> args = inputs == null ? new LinkedHashMap<>() : inputs;
        String normalized = trimToEmpty(language);
        if (LANGUAGE_JAVASCRIPT.equalsIgnoreCase(normalized)) {
            return runJavaScript(text, args);
        }
        if (!normalized.isEmpty() && !LANGUAGE_GROOVY.equalsIgnoreCase(normalized)
                && !"java".equalsIgnoreCase(normalized)) {
            log.warn("编排转换脚本语言[{}]未支持，按 Groovy 执行", language);
        }
        return runGroovy(text, args);
    }

    // ================================================================== Groovy

    private Object runGroovy(String script, Map<String, Object> inputs) {
        String source = buildGroovySource(script, inputs.keySet());
        String key = sha256(source);
        CompiledTransform compiled = groovyCache.get(key);
        if (compiled == null) {
            compiled = compileGroovy(source);
            if (groovyCache.size() >= MAX_GROOVY_CACHE_SIZE) {
                closeAllCachedLoaders();
            }
            groovyCache.put(key, compiled);
        }
        Binding binding = new Binding();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            if (isIdentifier(entry.getKey())) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }
        }
        binding.setVariable(WHOLE_INPUT_NAME, inputs);
        try {
            Object instance = compiled.clazz().getDeclaredConstructor().newInstance();
            if (instance instanceof groovy.lang.Script groovyScript) {
                groovyScript.setBinding(binding);
                return groovyScript.run();
            }
            return compiled.clazz().getMethod("run").invoke(instance);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("转换脚本执行失败：" + rootMessage(e));
        }
    }

    private CompiledTransform compileGroovy(String source) {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setSourceEncoding(StandardCharsets.UTF_8.name());
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader(), config);
        try {
            Class<?> clazz = loader.parseClass(source, "CompositeTransform_" + sha256(source) + ".groovy");
            return new CompiledTransform(clazz, loader);
        } catch (Exception e) {
            closeQuietly(loader);
            throw new BusinessException("转换脚本编译失败：" + rootMessage(e));
        }
    }

    /** 缓存整体超限时释放旧 ClassLoader，避免 Metaspace 持续增长 */
    private void closeAllCachedLoaders() {
        for (CompiledTransform cached : groovyCache.values()) {
            closeQuietly(cached.loader());
        }
        groovyCache.clear();
    }

    /** 在脚本后追加运行期入口：调用脚本声明的函数 */
    private String buildGroovySource(String script, Set<String> inputKeys) {
        FunctionSignature signature = parseSignature(script);
        if (signature == null) {
            return script;
        }
        return script + "\nreturn " + signature.name() + "(" + buildArgs(signature, inputKeys, "null") + ");\n";
    }

    // ================================================================== JavaScript

    private Object runJavaScript(String script, Map<String, Object> inputs) {
        Path scriptFile = null;
        try {
            String harness = buildJavaScriptHarness(script, inputs.keySet());
            scriptFile = Files.createTempFile("composite-transform-", ".js");
            Files.write(scriptFile, harness.getBytes(StandardCharsets.UTF_8));
            List<String> command = List.of(resolveNodeExecutable(), scriptFile.toAbsolutePath().toString(),
                    base64(script), base64(objectMapper.writeValueAsString(inputs)));
            Process process = startProcess(command);
            boolean finished = process.waitFor(scriptTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("转换脚本执行超时（" + scriptTimeoutSeconds + " 秒）");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new BusinessException("转换脚本执行失败：" + abbreviate(output));
            }
            return parseJavaScriptOutput(output);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("转换脚本执行失败：" + rootMessage(e));
        } finally {
            deleteQuietly(scriptFile);
        }
    }

    private Process startProcess(List<String> command) {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new BusinessException("未找到 Node.js 可执行文件[" + resolveNodeExecutable()
                    + "]，无法执行 JavaScript 转换脚本；请安装 Node.js 或把转换节点脚本改为 Groovy");
        }
    }

    private Object parseJavaScriptOutput(String output) {
        Map<?, ?> envelope;
        try {
            envelope = objectMapper.readValue(output, Map.class);
        } catch (Exception e) {
            throw new BusinessException("转换脚本输出无法解析：" + abbreviate(output));
        }
        if (envelope.containsKey("__error__")) {
            throw new BusinessException("转换脚本运行异常：" + envelope.get("__error__"));
        }
        return envelope.get("__out__");
    }

    /** 生成在 Node 中运行的包装脚本 */
    private String buildJavaScriptHarness(String script, Set<String> inputKeys) {
        FunctionSignature signature = parseSignature(script);
        // new Function 生成的函数在全局作用域执行，无法写回模块作用域的 __result__，因此用 return 取值
        String call = signature == null ? ""
                : "\nreturn " + signature.name() + "(" + buildArgs(signature, inputKeys, "undefined") + ");";
        return "const body = Buffer.from(process.argv[2], \"base64\").toString(\"utf8\");\n"
                + "const input = JSON.parse(Buffer.from(process.argv[3], \"base64\").toString(\"utf8\"));\n"
                + "const keys = Object.keys(input).filter((k) => /^[A-Za-z_$][\\w$]*$/.test(k));\n"
                + "const values = keys.map((k) => input[k]);\n"
                + "let __result__ = undefined;\n"
                + "let __error__ = null;\n"
                + "try {\n"
                + "  const fn = new Function(...keys, \"__input__\", body + \"" + escapeJsStringLiteral(call) + "\");\n"
                + "  __result__ = fn.apply(null, values.concat([input]));\n"
                + "} catch (e) {\n"
                + "  __error__ = String((e && e.message) || e);\n"
                + "}\n"
                + "process.stdout.write(JSON.stringify(__error__ === null"
                + " ? { __out__: __result__ === undefined ? null : __result__ }"
                + " : { __error__: __error__ }));\n";
    }

    /**
     * 组装调用实参：形参与入参同名则逐个传入；解构形参或唯一形参未命中入参名时整体传入。
     */
    private String buildArgs(FunctionSignature signature, Set<String> inputKeys, String missingLiteral) {
        List<String> params = signature.params();
        if (params.isEmpty()) {
            return "";
        }
        if (params.size() == 1 && !inputKeys.contains(stripDestructure(params.get(0)))) {
            return WHOLE_INPUT_NAME;
        }
        List<String> args = new ArrayList<>(params.size());
        for (String param : params) {
            String name = stripDestructure(param);
            args.add(isIdentifier(name) && inputKeys.contains(name) ? name : missingLiteral);
        }
        return String.join(", ", args);
    }

    private String resolveNodeExecutable() {
        String configured = trimToEmpty(nodeExecutable);
        return configured.isEmpty() ? "node" : configured;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("编排转换脚本临时文件删除失败: {}", path, e);
        }
    }

    // ================================================================== 公共工具

    /** 解析脚本里声明的函数名与形参列表 */
    static FunctionSignature parseSignature(String script) {
        if (script == null || script.isEmpty()) {
            return null;
        }
        Matcher matcher = DECLARED_FUNCTION.matcher(script);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String rawParams = matcher.group(1) != null ? matcher.group(2) : matcher.group(4);
            if (name == null || NOT_FUNCTION_NAMES.contains(name)) {
                continue;
            }
            return new FunctionSignature(name, splitParams(rawParams));
        }
        return null;
    }

    private static List<String> splitParams(String rawParams) {
        List<String> params = new ArrayList<>();
        if (rawParams == null || rawParams.trim().isEmpty()) {
            return params;
        }
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char ch : rawParams.toCharArray()) {
            if (ch == '{' || ch == '[' || ch == '(') {
                depth++;
            } else if (ch == '}' || ch == ']' || ch == ')') {
                depth--;
            }
            if (ch == ',' && depth <= 0) {
                params.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            params.add(current.toString().trim());
        }
        return params;
    }

    /** 解构形参（{a, b}）没有变量名，整体入参对象即可满足 */
    private static String stripDestructure(String param) {
        String value = param == null ? "" : param.trim();
        if (value.startsWith("{") || value.startsWith("[")) {
            return "";
        }
        int eq = value.indexOf('=');
        if (eq > 0) {
            value = value.substring(0, eq).trim();
        }
        return value;
    }

    static boolean isIdentifier(String name) {
        return name != null && !name.isEmpty() && name.matches("[A-Za-z_$][\\w$]*");
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** 把 Java 侧拼装的调用语句转成 JS 字符串字面量内容 */
    private static String escapeJsStringLiteral(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static void closeQuietly(GroovyClassLoader loader) {
        try {
            loader.close();
        } catch (Exception e) {
            log.warn("编排转换脚本 ClassLoader 关闭失败", e);
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isEmpty() ? current.getClass().getSimpleName() : abbreviate(message);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }

    /** Groovy 编译产物与其 ClassLoader，缓存淘汰时一并关闭 */
    private record CompiledTransform(Class<?> clazz, GroovyClassLoader loader) {
    }

    /** 脚本里声明的函数签名 */
    record FunctionSignature(String name, List<String> params) {
    }
}
