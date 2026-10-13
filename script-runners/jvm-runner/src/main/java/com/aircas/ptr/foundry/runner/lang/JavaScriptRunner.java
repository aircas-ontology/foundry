package com.aircas.ptr.foundry.runner.lang;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 脚本执行器：{@code runner.language=java} 时生效。
 * <p>
 * 通过 {@link javax.tools.JavaCompiler} 内存编译用户 Java 源码（需完整 JDK），用独立 {@link ClassLoader} 加载，
 * 反射调用 {@code handle(input)}。按 {@code codeHash} 缓存编译产物。统一契约为单对象入参。
 */
@Component
@ConditionalOnProperty(name = "runner.language", havingValue = "java")
public class JavaScriptRunner implements ScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(JavaScriptRunner.class);

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:public\\s+)?(?:final\\s+)?(?:abstract\\s+)?class\\s+(\\w+)");

    private final Map<String, CompiledJava> cache = new ConcurrentHashMap<>();

    @Override
    public String language() {
        return "java";
    }

    @Override
    public void validate(String code) {
        getOrCompile(code);
    }

    @Override
    public Object execute(String code, Map<String, Object> input) {
        CompiledJava compiled = getOrCompile(code);
        try {
            Class<?> clazz = compiled.loader.loadClass(compiled.className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method handle = findHandleMethod(clazz);
            return invokeHandle(handle, instance, input);
        } catch (ScriptCompileException e) {
            throw e;
        } catch (Exception e) {
            log.error("java execute failed", e);
            throw new ScriptCompileException("脚本执行失败：" + rootMessage(e), e);
        }
    }

    private Object invokeHandle(Method handle, Object instance, Map<String, Object> input) throws Exception {
        int count = handle.getParameterCount();
        if (count == 0) {
            return handle.invoke(instance);
        }
        if (count == 1) {
            return handle.invoke(instance, input);
        }
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

    private CompiledJava getOrCompile(String code) {
        String codeHash = sha256(code);
        CompiledJava cached = cache.get(codeHash);
        if (cached != null) {
            return cached;
        }
        CompiledJava compiled = compile(code, codeHash);
        cache.put(codeHash, compiled);
        return compiled;
    }

    private CompiledJava compile(String code, String codeHash) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ScriptCompileException("当前运行时不是 JDK，无法编译 Java 脚本");
        }
        String className = detectClassName(code);
        String simpleName = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, ByteArrayOutputStream> classBytes = new HashMap<>();
        try (StandardJavaFileManager stdFm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
             MemoryFileManager fm = new MemoryFileManager(stdFm, classBytes)) {

            JavaFileObject source = new StringSource(simpleName, code);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diagnostics,
                    List.of("-parameters"), null, List.of(source));
            boolean success = Boolean.TRUE.equals(task.call());
            if (!success) {
                StringBuilder sb = new StringBuilder();
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        sb.append(d.getMessage(null)).append("; ");
                    }
                }
                throw new ScriptCompileException("Java 编译失败：" + sb);
            }
        } catch (ScriptCompileException e) {
            throw e;
        } catch (IOException e) {
            throw new ScriptCompileException("Java 编译失败：" + rootMessage(e), e);
        }

        MemoryClassLoader loader = new MemoryClassLoader(classBytes);
        try {
            Class<?> clazz = loader.loadClass(className);
            if (findHandleMethod(clazz) == null) {
                throw new ScriptCompileException("函数 handle 方法不存在");
            }
        } catch (ClassNotFoundException e) {
            throw new ScriptCompileException("编译产物类未找到：" + className, e);
        }
        log.debug("java compiled, className={}, codeHash={}", className, codeHash);
        return new CompiledJava(className, loader);
    }

    /**
     * 探测顶层类的全限定名（package + 第一个 class 名）。
     */
    private String detectClassName(String code) {
        Matcher classMatcher = CLASS_PATTERN.matcher(code);
        if (!classMatcher.find()) {
            throw new ScriptCompileException("未找到 class 定义，Java 脚本须将 handle 包在类中");
        }
        String simpleName = classMatcher.group(1);
        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(code);
        if (pkgMatcher.find()) {
            return pkgMatcher.group(1) + "." + simpleName;
        }
        return simpleName;
    }

    private Method findHandleMethod(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> "handle".equals(m.getName()))
                .findFirst()
                .orElse(null);
    }

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

    private static final class CompiledJava {
        private final String className;
        private final MemoryClassLoader loader;

        private CompiledJava(String className, MemoryClassLoader loader) {
            this.className = className;
            this.loader = loader;
        }
    }

    /**
     * 源码字符串的 JavaFileObject。
     */
    private static final class StringSource extends SimpleJavaFileObject {
        private final String code;

        private StringSource(String simpleName, String code) {
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

        private MemoryFileManager(StandardJavaFileManager fileManager, Map<String, ByteArrayOutputStream> classBytes) {
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
     * 从内存字节码定义类的 ClassLoader（与主应用 ClassLoader 隔离）。
     */
    private static final class MemoryClassLoader extends ClassLoader {
        private final Map<String, ByteArrayOutputStream> classBytes;

        private MemoryClassLoader(Map<String, ByteArrayOutputStream> classBytes) {
            super(MemoryClassLoader.class.getClassLoader());
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
