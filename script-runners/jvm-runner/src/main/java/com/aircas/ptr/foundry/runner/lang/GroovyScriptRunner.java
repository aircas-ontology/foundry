package com.aircas.ptr.foundry.runner.lang;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groovy 脚本执行器：{@code runner.language=groovy} 时生效。
 * <p>
 * 由主应用 {@code GroovyServiceImpl} 的编译逻辑下沉而来。按 {@code codeHash} 缓存编译类（独立 ClassLoader）；
 * 统一契约 {@code handle(input)}，并对存量老签名 {@code handle(基本类型...)} 做按名/按序绑定的兼容。
 */
@Component
@ConditionalOnProperty(name = "runner.language", havingValue = "groovy", matchIfMissing = true)
public class GroovyScriptRunner implements ScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(GroovyScriptRunner.class);

    private final Map<String, CompiledScript> cache = new ConcurrentHashMap<>();

    @Override
    public String language() {
        return "groovy";
    }

    @Override
    public void validate(String code) {
        // 编译即校验：编译失败抛 ScriptCompileException
        getOrCompile(code);
    }

    @Override
    public Object execute(String code, Map<String, Object> input) {
        CompiledScript compiled = getOrCompile(code);
        try {
            Object instance = compiled.handleClass.getDeclaredConstructor().newInstance();
            Method handle = findHandleMethod(compiled.handleClass);
            return invokeHandle(handle, instance, input);
        } catch (ScriptCompileException e) {
            throw e;
        } catch (Exception e) {
            log.error("groovy execute failed", e);
            throw new ScriptCompileException("脚本执行失败：" + rootMessage(e), e);
        }
    }

    private Object invokeHandle(Method handle, Object instance, Map<String, Object> input) throws Exception {
        int count = handle.getParameterCount();
        if (count == 0) {
            return handle.invoke(instance);
        }
        if (count == 1) {
            // 统一契约：单对象入参 handle(input)
            return handle.invoke(instance, input);
        }
        // 兼容老签名：多基本类型入参，按参数名从 input 取值，取不到则按 input 顺序兜底
        Parameter[] parameters = handle.getParameters();
        Object[] args = new Object[count];
        Iterator<Object> fallback = input == null ? java.util.Collections.emptyIterator() : input.values().iterator();
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

    private CompiledScript getOrCompile(String code) {
        String codeHash = sha256(code);
        CompiledScript cached = cache.get(codeHash);
        if (cached != null) {
            return cached;
        }
        CompiledScript compiled = compile(code, codeHash);
        cache.put(codeHash, compiled);
        return compiled;
    }

    private CompiledScript compile(String code, String codeHash) {
        GroovyClassLoader loader = new GroovyClassLoader(GroovyScriptRunner.class.getClassLoader());
        GroovyCodeSource source = new GroovyCodeSource(code, "GroovyFn_" + codeHash + ".groovy", "/groovy/script");
        source.setCachable(false);
        try {
            loader.parseClass(source);
            Class<?> handleClass = findHandleClass(loader);
            if (handleClass == null) {
                closeQuietly(loader);
                throw new ScriptCompileException("函数 handle 方法不存在");
            }
            return new CompiledScript(handleClass, loader);
        } catch (ScriptCompileException e) {
            throw e;
        } catch (Exception e) {
            closeQuietly(loader);
            throw new ScriptCompileException("Groovy 编译失败：" + rootMessage(e), e);
        }
    }

    private Class<?> findHandleClass(GroovyClassLoader loader) {
        Class<?>[] loadedClasses = loader.getLoadedClasses();
        if (loadedClasses == null || loadedClasses.length == 0) {
            return null;
        }
        return Arrays.stream(loadedClasses)
                .filter(clz -> findHandleMethod(clz) != null)
                .findFirst()
                .orElse(null);
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

    private static void closeQuietly(GroovyClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.clearCache();
            loader.close();
        } catch (Exception ignore) {
            // ignore
        }
    }

    /**
     * 一个脚本对应一个独立 ClassLoader，缓存编译结果。
     */
    private static final class CompiledScript {
        private final Class<?> handleClass;
        private final GroovyClassLoader classLoader;

        private CompiledScript(Class<?> handleClass, GroovyClassLoader classLoader) {
            this.handleClass = handleClass;
            this.classLoader = classLoader;
        }
    }

}
