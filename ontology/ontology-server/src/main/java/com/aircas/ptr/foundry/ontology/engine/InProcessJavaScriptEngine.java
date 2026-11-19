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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
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
            String classpath = getClasspath();
            List<String> options = new ArrayList<>();
            options.add("-classpath");
            options.add(classpath);
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
                logClasspathDiagnostics(classpath);
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

    private volatile String cachedClasspath;

    private String getClasspath() {
        String cached = cachedClasspath;
        if (cached == null) {
            synchronized (this) {
                if (cachedClasspath == null) {
                    cachedClasspath = buildClasspath();
                }
                cached = cachedClasspath;
            }
        }
        return cached;
    }

    /**
     * 拼 javac 用的应用 classpath，使脚本能 import 主程序自己的类。
     * <p>只取 {@code java.class.path} 不够：IDE「缩短命令行＝JAR manifest / classpath file」与 {@code java -jar}
     * 启动下该属性只剩一两项，脚本会报 {@code package com.aircas... does not exist}。
     * 这里三路合并：系统属性（条目极少时才展开中间 jar 的 manifest Class-Path）+ 本类 CodeSource + ClassLoader 链上的 URL。</p>
     * <p>Spring Boot fat jar 部署时上面三路都拿不到真正的类入口（类与依赖嵌套在 {@code BOOT-INF} 下，
     * javac 读不了嵌套 jar），再对每个候选 jar 检测 fat jar 布局，命中则解压后补进 classpath。</p>
     */
    private String buildClasspath() {
        Set<String> entries = new LinkedHashSet<>();
        List<String> fromProperty = splitPath(System.getProperty("java.class.path"));
        entries.addAll(fromProperty);
        // 只在属性看起来被「缩短」成少数条目时才读清单，否则要为每个依赖 jar 打开一次 JarFile
        if (fromProperty.size() <= 3) {
            for (String entry : fromProperty) {
                addManifestClassPath(entries, entry);
            }
        }
        addCodeSource(entries, InProcessJavaScriptEngine.class);
        ClassLoader current = getClass().getClassLoader();
        while (current != null) {
            if (current instanceof URLClassLoader urlClassLoader) {
                for (URL url : urlClassLoader.getURLs()) {
                    addLocation(entries, url);
                }
            }
            current = current.getParent();
        }
        // 遍历副本：展开结果会往 entries 里追加解压目录与依赖 jar
        for (String entry : new ArrayList<>(entries)) {
            addFatJarClasspath(entries, entry);
        }
        return String.join(File.pathSeparator, entries);
    }

    /** Spring Boot fat jar 内工程类与三方依赖的存放前缀；javac 只能读普通目录与普通 jar，需要先解压。 */
    private static final String FAT_JAR_CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String FAT_JAR_LIB_PREFIX = "BOOT-INF/lib/";

    /**
     * fat jar（{@code java -jar app.jar} 部署形态）展开：{@code BOOT-INF/classes} 与 {@code BOOT-INF/lib/*.jar}
     * 是嵌套在 app.jar 里的条目，javac 无法直接读取，这里解压到临时目录后把 classes 目录与各依赖 jar 补进 classpath。
     * <p>只在运行形态确实是 fat jar 时触发：IDE 从 target/classes 跑、普通 jar 部署时 classpath 不变。
     * 解压失败只告警，退回原 classpath（脚本编译仍会报原来的错，但不影响其他函数）。</p>
     */
    private void addFatJarClasspath(Set<String> entries, String entry) {
        File file = new File(entry);
        if (!entry.toLowerCase().endsWith(".jar") || !file.isFile()) {
            return;
        }
        try (JarFile jar = new JarFile(file)) {
            if (!isFatJar(jar)) {
                return;
            }
            Path root = Files.createTempDirectory("foundry-javac-cp-");
            Path classesDir = Files.createDirectories(root.resolve("classes"));
            Path libDir = Files.createDirectories(root.resolve("lib"));
            int libCount = extractFatJar(jar, classesDir, libDir);
            entries.add(classesDir.toString());
            try (var libs = Files.list(libDir)) {
                libs.forEach(p -> entries.add(p.toString()));
            }
            registerCleanup(root);
            log.info("Java 脚本 classpath 检测到 fat jar：{}，已解压工程类与 {} 个依赖 jar 到 {}",
                    file.getAbsolutePath(), libCount, root);
        } catch (IOException e) {
            log.warn("展开 fat jar 失败，Java 脚本按原 classpath 编译：{}（{}）", entry, e.getMessage());
        }
    }

    private static boolean isFatJar(JarFile jar) {
        Enumeration<JarEntry> examination = jar.entries();
        while (examination.hasMoreElements()) {
            String name = examination.nextElement().getName();
            if (name.startsWith(FAT_JAR_CLASSES_PREFIX) || name.startsWith(FAT_JAR_LIB_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /** 解压 {@code BOOT-INF/classes} 与 {@code BOOT-INF/lib/*.jar}，返回依赖 jar 数量。 */
    private static int extractFatJar(JarFile jar, Path classesDir, Path libDir) throws IOException {
        int libCount = 0;
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory()) {
                continue;
            }
            if (name.startsWith(FAT_JAR_CLASSES_PREFIX)) {
                copyJarEntry(jar, entry, classesDir, name.substring(FAT_JAR_CLASSES_PREFIX.length()));
            } else if (name.startsWith(FAT_JAR_LIB_PREFIX) && name.endsWith(".jar")
                    && name.indexOf('/', FAT_JAR_LIB_PREFIX.length()) < 0) {
                // 只拷 lib 直属的 jar；再往下的目录是嵌套 jar 内部结构，不单独展开
                copyJarEntry(jar, entry, libDir, name.substring(FAT_JAR_LIB_PREFIX.length()));
                libCount++;
            }
        }
        return libCount;
    }

    private static void copyJarEntry(JarFile jar, JarEntry entry, Path targetRoot, String relative) throws IOException {
        Path target = targetRoot.resolve(relative).normalize();
        if (!target.startsWith(targetRoot)) {
            // zip-slip 防御：条目名带 ../ 越出解压目录时丢弃
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream in = jar.getInputStream(entry)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 临时解压目录随 JVM 退出清理；Windows 上句柄未释放时删除失败直接忽略。 */
    private static void registerCleanup(Path root) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // 退出阶段尽力清理，删不掉不阻塞 JVM 结束
                    }
                });
            } catch (IOException ignored) {
                // 目录可能已被手工清理
            }
        }, "foundry-javac-cp-cleanup"));
    }

    /**
     * 编译失败自检：输出应用自身类的运行时来源与 javac 实际拿到的 classpath。
     * <p>来源是 {@code file:.../target/classes} 而 classpath 里没有它 ⇒ classpath 取漏了；
     * 来源是 fat jar 内嵌套路径（{@code jar:file:...!/BOOT-INF/classes!/}）⇒ 应已由
     * {@link #addFatJarClasspath} 解压补入，若仍失败先查 {@code 展开 fat jar 失败} 告警。</p>
     */
    private void logClasspathDiagnostics(String classpath) {
        int count = classpath.isBlank() ? 0 : classpath.split(Pattern.quote(File.pathSeparator)).length;
        log.warn("Java 脚本编译失败自检：本类运行时来源={}，classpath 条目数={}，classpath={}",
                InProcessJavaScriptEngine.class.getResource("InProcessJavaScriptEngine.class"), count, classpath);
    }

    private static List<String> splitPath(String path) {
        List<String> items = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return items;
        }
        for (String entry : path.split(Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                items.add(entry.trim());
            }
        }
        return items;
    }

    /** 展开中间 jar 的 manifest Class-Path（相对路径按该 jar 所在目录解析）。 */
    private static void addManifestClassPath(Set<String> entries, String entry) {
        if (entry == null || !entry.toLowerCase().endsWith(".jar")) {
            return;
        }
        File jar = new File(entry);
        if (!jar.isFile()) {
            return;
        }
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            String classPath = manifest == null
                    ? null : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (classPath == null || classPath.isBlank()) {
                return;
            }
            File baseDir = jar.getParentFile();
            for (String relative : classPath.split("\\s+")) {
                if (relative.isBlank()) {
                    continue;
                }
                File resolved = toLocalFile(URLDecoder.decode(relative, StandardCharsets.UTF_8), baseDir);
                if (resolved != null && resolved.exists()) {
                    entries.add(resolved.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            log.debug("读取 classpath 项 manifest 失败：{}（{}）", entry, e.getMessage());
        }
    }

    /**
     * manifest Class-Path 的值是 URL 写法：可能是 {@code file:/D:/x}、{@code /D:/x}，也可能是相对该中间 jar 的路径。
     * Windows 下 {@code new File("/D:/x")} 会被当成「当前盘根目录」而非 D 盘，所以要先剥掉盘符前的斜杠。
     */
    private static File toLocalFile(String raw, File baseDir) {
        String path = raw.startsWith("file:") ? raw.substring("file:".length()) : raw;
        if (path.length() > 2 && path.charAt(0) == '/' && Character.isLetter(path.charAt(1)) && path.charAt(2) == ':') {
            path = path.substring(1);
        }
        if (path.isBlank()) {
            return null;
        }
        File direct = new File(path);
        if (direct.isAbsolute() || baseDir == null) {
            return direct;
        }
        return new File(baseDir, path);
    }

    private static void addCodeSource(Set<String> entries, Class<?> clazz) {
        try {
            ProtectionDomain domain = clazz.getProtectionDomain();
            addLocation(entries, domain == null ? null : domain.getCodeSource());
        } catch (SecurityException | IllegalArgumentException e) {
            log.debug("取本类 CodeSource 失败：{}", e.getMessage());
        }
    }

    private static void addLocation(Set<String> entries, CodeSource source) {
        if (source != null) {
            addLocation(entries, source.getLocation());
        }
    }

    /** 只收 {@code file:} 协议且磁盘上真实存在的条目；{@code jar:} 等嵌套协议 javac 读不了。 */
    private static void addLocation(Set<String> entries, URL url) {
        if (url == null || !"file".equalsIgnoreCase(url.getProtocol())) {
            return;
        }
        try {
            File file = new File(url.toURI());
            if (file.exists()) {
                entries.add(file.getAbsolutePath());
            }
        } catch (Exception e) {
            String decoded = URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8);
            if (new File(decoded).exists()) {
                entries.add(decoded);
            }
        }
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
