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
 * C1 危险类型引用：import / static import / star import 命中黑名单即拦截。
 * <p>
 * 注意：Groovy 默认隐式导入 java.lang/java.util/java.io/java.net/groovy.lang/groovy.util，
 * 因此 new Socket(...) 这类写法不会产生 import，本规则覆盖不到，需依赖构造器与方法规则。
 */
@Component
public class DangerousImportRule implements ScriptSecurityRule {

    @Override
    public String name() {
        return "DangerousImportRule";
    }

    @Override
    public List<SecurityViolation> inspect(ScriptSecurityContext context) {
        List<SecurityViolation> violations = new ArrayList<>();
        List<String> patterns = context.getProperties().getDangerousImports();
        for (AstTypeRef ref : context.getInventory().getImports()) {
            if (RuleMatchers.matchesTypeRef(ref, patterns)) {
                SecurityCategory category = RuleMatchers.categoryOfType(ref.getTypeName());
                violations.add(SecurityViolation.of(
                        "import." + ref.getTypeName(),
                        SecuritySeverity.DANGER,
                        category,
                        ref.getLineNumber(),
                        ref.getColumnNumber(),
                        "禁止引用危险类型：" + ref.getTypeName() + (ref.isStarImport() ? "（通配导入）" : ""),
                        ref.getSnippet()));
            }
        }
        return violations;
    }
}
