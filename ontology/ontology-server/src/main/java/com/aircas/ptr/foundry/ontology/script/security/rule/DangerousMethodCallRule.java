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
 * C2 危险方法调用。
 * <p>
 * 这是覆盖面的主力规则：Groovy 隐式导入 java.lang/java.util/java.io/java.net，
 * {@code Runtime.getRuntime().exec("id")} 这类写法不产生任何 import，
 * 只有方法级匹配能拦住（已用 SecureASTCustomizer 实测其 disallowedReceivers 可被绕过）。
 */
@Component
public class DangerousMethodCallRule implements ScriptSecurityRule {

    @Override
    public String name() {
        return "DangerousMethodCallRule";
    }

    @Override
    public List<SecurityViolation> inspect(ScriptSecurityContext context) {
        List<SecurityViolation> violations = new ArrayList<>();
        List<String> patterns = context.getProperties().getDangerousMethods();
        for (AstCallRef call : context.getInventory().getMethodCalls()) {
            collect(violations, call, patterns);
        }
        for (AstCallRef call : context.getInventory().getStaticMethodCalls()) {
            collect(violations, call, patterns);
        }
        return violations;
    }

    private void collect(List<SecurityViolation> violations, AstCallRef call, List<String> patterns) {
        if (!RuleMatchers.matchesMethod(call, patterns)) {
            return;
        }
        violations.add(SecurityViolation.of(
                ruleIdOf(call),
                SecuritySeverity.DANGER,
                categoryOf(call),
                call.getLineNumber(),
                call.getColumnNumber(),
                "禁止调用危险方法：" + displayOf(call),
                call.getSnippet()));
    }

    private static String ruleIdOf(AstCallRef call) {
        String owner = call.getReceiverSimpleName();
        return owner == null ? "call." + call.getMethodName() : "call." + owner + "#" + call.getMethodName();
    }

    private static String displayOf(AstCallRef call) {
        String owner = call.getReceiverSimpleName();
        return owner == null ? call.getMethodName() : owner + "." + call.getMethodName();
    }

    private static SecurityCategory categoryOf(AstCallRef call) {
        if (call.getReceiverType() != null) {
            return RuleMatchers.categoryOfType(call.getReceiverType());
        }
        return RuleMatchers.categoryOfType(call.getReceiverSimpleName());
    }
}
