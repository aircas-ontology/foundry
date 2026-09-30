package com.aircas.ptr.foundry.ontology.script.security;

import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import org.codehaus.groovy.control.CompilePhase;

import java.util.List;

/**
 * 脚本安全检测规则。
 * <p>
 * 规则是无状态的，只依赖 {@link ScriptSecurityContext} 中已解析好的清单与配置，
 * 因此可独立单测，也便于后续增删条目。
 */
public interface ScriptSecurityRule {

    /** 规则名称（用于日志与自检，非违规 ID） */
    String name();

    /** 规则是否正常启用；返回 false 时引擎跳过（用于自检发现配置缺失） */
    default boolean enabled() {
        return true;
    }

    /**
     * 规则依赖的编译阶段。
     * <p>
     * 默认 SEMANTIC_ANALYSIS（类型已解析，匹配最准）。声明为 CONVERSION 的规则只能读取注解，
     * 其价值是：在编译器推进到语义分析之前先拦掉 {@code @ASTTest} 这类<b>编译期执行代码</b>的注解。
     */
    default CompilePhase phase() {
        return CompilePhase.SEMANTIC_ANALYSIS;
    }

    List<SecurityViolation> inspect(ScriptSecurityContext context);
}
