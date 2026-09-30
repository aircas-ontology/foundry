package com.aircas.ptr.foundry.ontology.script.security.rule;

import com.aircas.ptr.foundry.ontology.script.security.RuleMatchers;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityContext;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityRule;
import com.aircas.ptr.foundry.ontology.script.security.model.AstTypeRef;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityCategory;
import com.aircas.ptr.foundry.ontology.script.security.model.SecuritySeverity;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import org.codehaus.groovy.control.CompilePhase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * C4 危险注解：这些注解会在<b>编译期</b>产生副作用（依赖下载、代码执行），
 * 而保存动作本身就会编译脚本，因此必须在第一阶段（CONVERSION）就拦掉。
 */
@Component
public class DangerousAnnotationRule implements ScriptSecurityRule {

    @Override
    public String name() {
        return "DangerousAnnotationRule";
    }

    /**
     * 必须在 CONVERSION 阶段执行：@ASTTest 会在语义分析阶段执行其中代码，
     * 若等到语义分析后再判定，代码已经被执行过了。
     */
    @Override
    public CompilePhase phase() {
        return CompilePhase.CONVERSION;
    }

    @Override
    public List<SecurityViolation> inspect(ScriptSecurityContext context) {
        List<SecurityViolation> violations = new ArrayList<>();
        List<String> patterns = context.getProperties().getDangerousAnnotations();
        // CONVERSION 阶段注解尚未解析，ClassNode 名称只有简单名（如 Grab 而非 groovy.lang.Grab），
        // 因此除全限定名外必须按简单名兜底匹配，否则 @Grab 会在第一阶段漏检。
        Set<String> simpleNames = simpleNamesOf(patterns);
        for (AstTypeRef ref : context.getInventory().getAnnotations()) {
            if (RuleMatchers.matchesTypeRef(ref, patterns) || simpleNames.contains(simpleName(ref.getTypeName()))) {
                violations.add(SecurityViolation.of(
                        "annotation." + ref.getTypeName(),
                        SecuritySeverity.DANGER,
                        SecurityCategory.ANNOTATION,
                        ref.getLineNumber(),
                        ref.getColumnNumber(),
                        "禁止使用会在编译期产生副作用的注解：" + ref.getTypeName(),
                        ref.getSnippet()));
            }
        }
        return violations;
    }

    private static Set<String> simpleNamesOf(List<String> patterns) {
        Set<String> names = new HashSet<>();
        if (patterns == null) {
            return names;
        }
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isEmpty()) {
                names.add(simpleName(pattern));
            }
        }
        return names;
    }

    private static String simpleName(String typeName) {
        if (typeName == null) {
            return null;
        }
        int idx = typeName.lastIndexOf('.');
        return idx < 0 ? typeName : typeName.substring(idx + 1);
    }
}
