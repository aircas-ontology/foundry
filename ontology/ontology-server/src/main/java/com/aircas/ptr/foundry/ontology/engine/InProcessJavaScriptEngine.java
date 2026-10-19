package com.aircas.ptr.foundry.ontology.engine;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 进程内引擎：通过 {@code javax.tools.JavaCompiler} 在主程序 JVM 内编译执行 Java 脚本，
 * 使脚本可直接 import 并引用 ontology-server 内部的类。
 * <p>
 * 与早期设计的独立 java-runner 容器方案不同，本引擎的 ClassLoader parent 指向应用 ClassLoader，
 * 编译时 classpath 包含应用全部依赖，因此脚本能访问项目内所有类。</p>
 */
@Slf4j
@Component
public class InProcessJavaScriptEngine implements ScriptEngine {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:public\\s+)?(?:final\\s+)?(?:abstract\\s+)?class\\s+(\\w+)");

    /** 编译缓存：key = functionApi + "#" + codeHash */
    private final Map<String, CompiledJava> cache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Set<FunctionLanguageEnum> supports() {
        return EnumSet.of(FunctionLanguageEnum.JAVA);
    }

    @Override
    public void validate(ScriptExecutionContext ctx) {
        getOrCompile(ctx.getFunctionApi(), ctx.getCode());
    }

    @Override
    public String execute(ScriptExecutionContext ctx) {
        CompiledJava compiled = getOrCompile(ctx.getFunctionApi(), ctx.getCode());
        Map<String, Object> input = ctx.getInputMap() == null ? new HashMap<>() : ctx.getInputMap();
        try {
            Class<?> clazz = compiled.loader.loadClass(compiled.className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method handle = findHandleMethod(clazz);
            if (handle == null) {
                throw new BusinessException("Java 脚本中未找到 handle 方法：" + ctx.getFunctionApi());
            }
            Object result = invokeHandle(handle, instance, input);
            return objectMapper.writeValueAsString(result);
        } catch (BusinessException e) {
            throw e;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Java 脚本执行异常：api={}", ctx.getFunctionApi(), cause);
            throw new BusinessException("脚本执行失败：" + cause.getMessage());
        } catch (Exception e) {
            log.error("Java 脚本执行失败：api={}", ctx.getFunctionApi(), e);
            throw new BusinessException("脚本执行失败：" + rootMessage(e));
        }
    }

    @Override
    public void invalidate(FunctionLanguageEnum language, String functionApi) {
        // 清除该函数所有版本的编译缓存
        cache.keySet().removeIf(key -> key.startsWith(functionApi + "#"));
        log.debug("invalidate Java 编译缓存：api={}", functionApi);
    }

    // ===================== 编译核心逻辑 =====================

    private CompiledJava getOrCompile(String functionApi, String code) {
        String codeHash = sha256(code);
        String cacheKey = functionApi + "#" + codeHash;
        CompiledJava cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        CompiledJava compiled = compile(code);
        cache.put(cacheKey, compiled);
        return compiled;
    }

    private CompiledJava compile(String code) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new BusinessException("当前运行时不是 JDK，无法编译 Java 脚本（需 JDK 17 启动）");
        }
        String className = detectClassName(code);
        String simpleName = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, ByteArrayOutputStream> classBytes = new HashMap<>();
        try (StandardJavaFileManager stdFm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
             MemoryFileManager fm = new MemoryFileManager(stdFm, classBytes)) {

            JavaFileObject source = new StringSource(simpleName, code);
            // 传入应用 classpath，使脚本能 import 项目内部类
            List<String> options = new ArrayList<>();
            options.add("-classpath");
            options.add(getClasspath());
            options.add("-parameters");

            JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diagnostics, options, null, List.of(source));
            boolean success = Boolean.TRUE.equals(task.call());
            if (!success) {
                StringBuilder sb = new StringBuilder();
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        sb.append(d.getMessage(null)).append("; ");
                    }
                }
                throw new BusinessException("Java 编译失败：" + sb);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("Java 编译失败：" + rootMessage(e));
        }

        // ClassLoader parent 使用当前线程的 ContextClassLoader（即 Spring Boot 应用 ClassLoader），
        // 使编译产物类可访问应用全部类
        MemoryClassLoader loader = new MemoryClassLoader(classBytes);
        try {
            Class<?> clazz = loader.loadClass(className);
            if (findHandleMethod(clazz) == null) {
                throw new BusinessException("Java 脚本中未找到 handle 方法");
            }
        } catch (ClassNotFoundException e) {
            throw new BusinessException("编译产物类未找到：" + className);
        }
        log.debug("Java 编译完成：className={}", className);
        return new CompiledJava(className, loader);
    }

    /**
     * 获取应用 classpath。优先用系统属性 java.class.path（开发环境），
     * 若为 fat jar 则回退到 ClassLoader 的 URL 列表。
     */
    private String getClasspath() {
        String cp = System.getProperty("java.class.path");
        if (cp != null && !cp.isBlank()) {
            return cp;
        }
        // fat jar 场景：从 ClassLoader 拼接
        return cp == null ? "." : cp;
    }

    private String detectClassName(String code) {
        Matcher classMatcher = CLASS_PATTERN.matcher(code);
        if (!classMatcher.find()) {
            throw new BusinessException("未找到 class 定义，Java 脚本须将 handle 包在类中");
        }
        String simpleName = classMatcher.group(1);
        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(code);
        if (pkgMatcher.find()) {
            return pkgMatcher.group(1) + "." + simpleName;
        }
        return simpleName;
    }

    // ===================== handle 调用 =====================

    private Method findHandleMethod(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> "handle".equals(m.getName()))
                .findFirst()
                .orElse(null);
    }

    private Object invokeHandle(Method handle, Object instance, Map<String, Object> input) throws Exception {
        int count = handle.getParameterCount();
        if (count == 0) {
            return handle.invoke(instance);
        }
        if (count == 1) {
            return handle.invoke(instance, input);
        }
        // 多参数：按形参名从 input 取值
        Parameter[] parameters = handle.getParameters();
        Object[] args = new Object[count];
        Iterator<Object> fallback = input == null ? Collections.emptyIterator() : input.values().iterator();
        for (int i = 0; i < count; i++) {
            String name = parameters[i].getName();
            if (input != null && input.containsKey(name)) {
                args[i] = input.get(name);
            } else if (fallback.hasNext()) {
                args[i] = fallback.next();
            }
        }
        return handle.invoke(instance, args);
    }

    // ===================== 工具方法 =====================

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    private static String sha256(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(code.hashCode());
        }
    }

    // ===================== 内部类 =====================

    private static final class CompiledJava {
        final String className;
        final MemoryClassLoader loader;

        CompiledJava(String className, MemoryClassLoader loader) {
            this.className = className;
            this.loader = loader;
        }
    }

    /**
     * 源码字符串的 JavaFileObject。
     */
    private static final class StringSource extends SimpleJavaFileObject {
        private final String code;

        StringSource(String simpleName, String code) {
            super(URI.create("string:///" + simpleName.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /**
     * 捕获编译产物字节码的文件管理器。
     */
    private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> classBytes;

        MemoryFileManager(StandardJavaFileManager fileManager, Map<String, ByteArrayOutputStream> classBytes) {
            super(fileManager);
            this.classBytes = classBytes;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            classBytes.put(className, bos);
            return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    return bos;
                }
            };
        }
    }

    /**
     * 从内存字节码加载类的 ClassLoader。
     * parent 使用当前线程 ContextClassLoader（Spring Boot 应用 ClassLoader），
     * 使脚本类可直接引用应用内的类。
     */
    private static final class MemoryClassLoader extends ClassLoader {
        private final Map<String, ByteArrayOutputStream> classBytes;

        MemoryClassLoader(Map<String, ByteArrayOutputStream> classBytes) {
            // 关键：parent = 应用 ClassLoader，脚本能访问主程序所有类
            super(Thread.currentThread().getContextClassLoader());
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            ByteArrayOutputStream bos = classBytes.get(name);
            if (bos == null) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = bos.toByteArray();
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

}
