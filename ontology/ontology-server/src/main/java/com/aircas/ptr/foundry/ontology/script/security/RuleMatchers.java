package com.aircas.ptr.foundry.ontology.script.security;

import com.aircas.ptr.foundry.ontology.script.security.model.AstCallRef;
import com.aircas.ptr.foundry.ontology.script.security.model.AstTypeRef;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityCategory;

import java.util.List;

/**
 * 规则匹配工具。
 */
public final class RuleMatchers {

    private RuleMatchers() {
    }

    /**
     * 类型匹配：配置项以 {@code .} 结尾表示包前缀，否则要求全限定名相等。
     */
    public static boolean matchesType(String typeName, List<String> patterns) {
        if (typeName == null || patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            if (pattern.endsWith(".")) {
                if (typeName.startsWith(pattern)) {
                    return true;
                }
            } else if (pattern.equals(typeName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesTypeRef(AstTypeRef ref, List<String> patterns) {
        return ref != null && matchesType(ref.getTypeName(), patterns);
    }

    /**
     * 包前缀匹配：配置项按<b>前缀</b>理解，不要求写成 {@code xxx.}。
     * <p>
     * 这一点很关键：{@code matchesType} 对不以点结尾的配置按「全名相等」处理，
     * 若误用于包前缀，{@code com.aircas.ptr.foundry} 将永远匹配不到
     * {@code com.aircas.ptr.foundry.common.util.CommandUtil}，规则会静默失效。
     * 同时保留精确匹配，便于单独放行某个具体类。
     */
    public static boolean matchesPackagePrefix(String typeName, List<String> prefixes) {
        if (typeName == null || prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isEmpty()) {
                continue;
            }
            if (typeName.equals(prefix)) {
                return true;
            }
            String normalized = prefix.endsWith(".") ? prefix : prefix + ".";
            if (typeName.startsWith(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 方法匹配：配置形如 {@code Owner#method} 或 {@code method}。
     * <p>
     * Owner 支持全限定名、简单类名，以及未解析时的接收者源码文本（如 Runtime）。
     */
    public static boolean matchesMethod(AstCallRef call, List<String> patterns) {
        if (call == null || patterns == null || call.getMethodName() == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            int sep = pattern.indexOf('#');
            if (sep < 0) {
                if (pattern.equals(call.getMethodName())) {
                    return true;
                }
                continue;
            }
            String owner = pattern.substring(0, sep);
            String method = pattern.substring(sep + 1);
            if (!method.equals(call.getMethodName())) {
                continue;
            }
            if (ownerMatches(owner, call)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownerMatches(String owner, AstCallRef call) {
        String receiverType = call.getReceiverType();
        String simpleName = call.getReceiverSimpleName();
        if (receiverType != null && (owner.equals(receiverType) || owner.endsWith("."))) {
            if (owner.endsWith(".")) {
                return receiverType.startsWith(owner);
            }
            return true;
        }
        return owner.equals(simpleName);
    }

    /**
     * 按类型名推断威胁分类，用于给前端/日志一个可读的归类。
     */
    public static SecurityCategory categoryOfType(String typeName) {
        if (typeName == null) {
            return SecurityCategory.INTERNAL;
        }
        if (typeName.startsWith("java.io.") || typeName.startsWith("java.nio.")) {
            return SecurityCategory.FILE;
        }
        if (typeName.startsWith("java.net.") || typeName.startsWith("groovyx.net.http")) {
            return SecurityCategory.NETWORK;
        }
        if (typeName.startsWith("java.lang.reflect") || typeName.startsWith("java.lang.invoke")) {
            return SecurityCategory.REFLECTION;
        }
        if (typeName.contains("ClassLoader") || typeName.startsWith("javax.script")
                || typeName.startsWith("javax.naming") || typeName.startsWith("javax.management")
                || typeName.startsWith("groovy.lang.Groovy") || typeName.startsWith("groovy.util.Eval")
                || typeName.startsWith("groovy.util.GroovyScriptEngine")) {
            return SecurityCategory.CLASSLOADER;
        }
        if (typeName.startsWith("sun.") || typeName.startsWith("com.sun.")) {
            return SecurityCategory.JVM;
        }
        if (typeName.contains("Runtime") || typeName.contains("Process")) {
            return SecurityCategory.COMMAND;
        }
        return SecurityCategory.INTERNAL;
    }
}
