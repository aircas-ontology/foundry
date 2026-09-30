package com.aircas.ptr.foundry.ontology.script.security.rule;

import com.aircas.ptr.foundry.ontology.script.security.RuleMatchers;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityContext;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityRule;
import com.aircas.ptr.foundry.ontology.script.security.model.AstTypeRef;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityCategory;
import com.aircas.ptr.foundry.ontology.script.security.model.SecuritySeverity;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * C3 危险构造器调用。
 * <p>
 * 本规则可识别<b>全限定名</b>写法（new java.lang.ProcessBuilder(...)），
 * 这是 SecureASTCustomizer 的 disallowedImports 挡不住的场景（已实测）。
 */
@Component
public class DangerousConstructorRule implements ScriptSecurityRule {

    @Override
    public String name() {
        return "DangerousConstructorRule";
    }

    @Override
    public List<SecurityViolation> inspect(ScriptSecurityContext context) {
        List<SecurityViolation> violations = new ArrayList<>();
        List<String> patterns = context.getProperties().getDangerousConstructors();
        for (AstTypeRef ref : context.getInventory().getConstructorCalls()) {
            if (RuleMatchers.matchesTypeRef(ref, patterns)) {
                SecurityCategory category = RuleMatchers.categoryOfType(ref.getTypeName());
                violations.add(SecurityViolation.of(
                        "ctor." + ref.getTypeName(),
                        SecuritySeverity.DANGER,
                        category,
                        ref.getLineNumber(),
                        ref.getColumnNumber(),
                        "禁止构造危险类型：" + ref.getTypeName(),
                        ref.getSnippet()));
            }
        }
        return violations;
    }
}
