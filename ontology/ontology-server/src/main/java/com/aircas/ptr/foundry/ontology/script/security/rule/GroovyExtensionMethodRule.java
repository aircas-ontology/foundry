package com.aircas.ptr.foundry.ontology.script.security.rule;

import com.aircas.ptr.foundry.ontology.script.security.RuleMatchers;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityContext;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityRule;
import com.aircas.ptr.foundry.ontology.script.security.model.AstCallRef;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityCategory;
import com.aircas.ptr.foundry.ontology.script.security.model.SecuritySeverity;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * C6 Groovy GDK 扩展方法。
 * <p>
 * {@code 'whoami'.execute()} 在 AST 里只是一个普通的 MethodCallExpression，
 * 接收者是 String 常量——既没有危险 import，也没有危险类型，类型系统上看不出任何异常。
 * 已实测：SecureASTCustomizer 的 disallowedReceiversClasses 拦不住它。
 * 因此必须按「接收者类型#方法名」单独匹配。
 */
@Component
public class GroovyExtensionMethodRule implements ScriptSecurityRule {

    @Override
    public String name() {
        return "GroovyExtensionMethodRule";
    }

    @Override
    public List<SecurityViolation> inspect(ScriptSecurityContext context) {
        List<SecurityViolation> violations = new ArrayList<>();
        List<String> patterns = context.getProperties().getGroovyExtensionMethods();
        for (AstCallRef call : context.getInventory().getMethodCalls()) {
            if (!RuleMatchers.matchesMethod(call, patterns)) {
                continue;
            }
            violations.add(SecurityViolation.of(
                    "gdk." + call.getReceiverSimpleName() + "#" + call.getMethodName(),
                    SecuritySeverity.DANGER,
                    categoryOf(call),
                    call.getLineNumber(),
                    call.getColumnNumber(),
                    "禁止使用会产生外部副作用的 GDK 扩展方法："
                            + call.getReceiverSimpleName() + "#" + call.getMethodName(),
                    call.getSnippet()));
        }
        return violations;
    }

    private static SecurityCategory categoryOf(AstCallRef call) {
        String receiver = call.getReceiverType() != null ? call.getReceiverType() : call.getReceiverSimpleName();
        return RuleMatchers.categoryOfType(receiver);
    }
}
