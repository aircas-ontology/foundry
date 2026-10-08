package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 脚本算子（函数）安全检测配置。
 * <p>
 * 前缀 {@code ontology.script.security}；key 写在主配置 application.yml，环境差异值在 application-*.yml 覆盖。
 * 所有清单类配置均可在配置文件中覆盖，默认值即当前黑名单。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ontology.script.security")
public class ScriptSecurityProperties {

    /** 总开关；关闭时直接放行（仅建议临时排障使用） */
    private boolean enabled = true;

    /** DANGER 是否拦截 */
    private boolean blockOnViolation = true;

    /** WARN 是否升级为拦截 */
    private boolean blockOnWarn = true;

    /** 脚本最大字节数，防超大脚本导致编译期资源耗尽 */
    private int maxScriptSize = 65536;

    /** AST 最大嵌套深度 */
    private int maxAstDepth = 400;

    /** 单次扫描超时（毫秒），超时按 fail-closed 拒绝 */
    private long scanTimeoutMs = 2000;

    /**
     * 扫描在容器共用的 {@code taskExecutor} 上执行（CODING_CONVENTIONS §12：不另建线程池）。
     * 该池有界且为 AbortPolicy，池满时提交会被拒绝，扫描器按 fail-closed 转为拒绝保存。
     */

    /** 启动时自检；自检不通过则记录 error 日志（用于发现规则静默失效） */
    private boolean selfCheckOnStartup = true;

    /** 平台内部包前缀：命中这些前缀的类默认禁止被调用 */
    private List<String> internalPackagePrefixes = new ArrayList<>(Arrays.asList(
            "com.aircas.ptr.foundry"
    ));

    /**
     * 平台内部包的放行名单。
     * <p>
     * 必须包含 {@code ...ontology.model.vo}：GroovyServiceImpl 要求 handle 返回类型为 FunctionResultVO。
     */
    private List<String> internalPackageAllowlist = new ArrayList<>(Arrays.asList(
            "com.aircas.ptr.foundry.ontology.model.vo",
            "com.aircas.ptr.foundry.ontology.model.common",
            "com.aircas.ptr.foundry.common.constant"
    ));

    /** 危险类型（import / 全限定名引用即拦）。以点结尾表示包前缀。 */
    private List<String> dangerousImports = new ArrayList<>(Arrays.asList(
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Process",
            "java.lang.ProcessHandle",
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.RandomAccessFile",
            "java.io.ObjectInputStream",
            "java.nio.file.Files",
            "java.nio.file.Paths",
            "java.nio.file.Path",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.DatagramSocket",
            "java.net.URL",
            "java.net.HttpURLConnection",
            "java.net.URLConnection",
            "java.lang.reflect.Method",
            "java.lang.reflect.Field",
            "java.lang.reflect.Constructor",
            "java.lang.reflect.Proxy",
            "java.lang.invoke.MethodHandles",
            "java.lang.invoke.MethodHandle",
            "java.lang.ClassLoader",
            "java.lang.invoke.",
            "java.lang.reflect.",
            "groovy.lang.GroovyClassLoader",
            "groovy.lang.GroovyShell",
            "groovy.util.Eval",
            "groovy.util.GroovyScriptEngine",
            "javax.script.ScriptEngine",
            "javax.script.ScriptEngineManager",
            "javax.naming.",
            "javax.management.",
            "org.apache.commons.io.FileUtils",
            "groovyx.net.http.",
            "sun.",
            "com.sun."
    ));

    /**
     * 危险方法调用。格式为 {@code Owner#method} 或 {@code method}。
     * <ul>
     *   <li>{@code Owner#method}：Owner 可为全限定名或简单类名，按接收者类型/名称匹配</li>
     *   <li>{@code method}：仅按方法名匹配，仅用于几乎不会被正常业务使用的强特征方法</li>
     * </ul>
     */
    private List<String> dangerousMethods = new ArrayList<>(Arrays.asList(
            "Runtime#exec",
            "Runtime#load",
            "Runtime#loadLibrary",
            "Runtime#halt",
            "ProcessBuilder#start",
            "ProcessBuilder#command",
            "System#exit",
            "System#halt",
            "System#getProperty",
            "System#getenv",
            "System#setProperty",
            "System#load",
            "System#loadLibrary",
            "Class#forName",
            "Class#newInstance",
            "Class#getDeclaredMethod",
            "Class#getDeclaredField",
            "Class#getMethod",
            "Class#getField",
            "Method#invoke",
            "Field#setAccessible",
            "Method#setAccessible",
            "Constructor#newInstance",
            "AccessibleObject#setAccessible",
            "ClassLoader#loadClass",
            "ClassLoader#defineClass",
            "GroovyShell#evaluate",
            "GroovyShell#parse",
            "Eval#me",
            "Unsafe#allocateInstance",
            "Unsafe#defineClass",
            "getRuntime",
            "forName",
            "setAccessible",
            "loadClass",
            "defineClass",
            "getenv",
            "halt",
            "newInstance"
    ));

    /** 危险构造器调用（new Xxx(...)） */
    private List<String> dangerousConstructors = new ArrayList<>(Arrays.asList(
            "java.lang.ProcessBuilder",
            "java.lang.Process",
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.RandomAccessFile",
            "java.io.ObjectInputStream",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.DatagramSocket",
            "java.net.URL",
            "java.lang.ClassLoader",
            "groovy.lang.GroovyClassLoader",
            "groovy.lang.GroovyShell",
            "groovy.util.GroovyScriptEngine",
            "javax.script.ScriptEngineManager"
    ));

    /** 危险注解：这些注解会在编译期产生副作用（依赖下载、代码执行） */
    private List<String> dangerousAnnotations = new ArrayList<>(Arrays.asList(
            "groovy.lang.Grab",
            "groovy.lang.GrabConfig",
            "groovy.lang.GrabResolver",
            "groovy.lang.GrabExclude",
            "groovy.transform.ASTTest"
    ));

    /** Groovy GDK 扩展方法：不产生 import 也不产生显式构造调用，只能按「接收者类型#方法名」匹配 */
    private List<String> groovyExtensionMethods = new ArrayList<>(Arrays.asList(
            "String#execute",
            "String#toURL",
            "String#toFile",
            "GString#execute",
            "File#getText",
            "File#getBytes",
            "File#setText",
            "File#text",
            "File#bytes",
            "URL#getText",
            "URL#getBytes",
            "URL#text",
            "URL#openConnection",
            "URL#openStream"
    ));

    /** 资源耗尽类：仅 WARN，主要用于提示，真实缓解依赖运行时超时与有界线程池 */
    private List<String> resourceTypes = new ArrayList<>(Arrays.asList(
            "java.lang.Thread",
            "java.util.concurrent.ThreadPoolExecutor",
            "java.util.concurrent.Executors",
            "java.util.Timer",
            "java.util.concurrent.ScheduledThreadPoolExecutor"
    ));
}
