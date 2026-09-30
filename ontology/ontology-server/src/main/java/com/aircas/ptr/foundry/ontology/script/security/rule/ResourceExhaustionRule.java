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
 * C8 资源耗尽风险：自建线程 / 线程池 / 定时器。
 * <p>
 * 默认仅 WARN。A 方案不含运行时隔离（不限制 CPU/内存/线程），
 * 这里的判定只是「提示」——真正的缓解必须靠执行超时与有界线程池（B 方案）。
 * 由 {@code blockOnWarn} 决定是否升级为拦截，默认升级。
 */
@Component
public class ResourceExhaustionRule implements ScriptSecurityRule {

    @Override
    public String name() {
        return "ResourceExhaustionRule";
    }

    @Override
    public List<SecurityViolation> inspect(ScriptSecurityContext context) {
        List<SecurityViolation> violations = new ArrayList<>();
        List<String> patterns = context.getProperties().getResourceTypes();
        for (AstTypeRef ref : context.getInventory().getImports()) {
            collect(violations, ref, "引用", patterns);
        }
        for (AstTypeRef ref : context.getInventory().getConstructorCalls()) {
            collect(violations, ref, "构造", patterns);
        }
        return violations;
    }

    private void collect(List<SecurityViolation> violations, AstTypeRef ref, String action, List<String> patterns) {
        if (!RuleMatchers.matchesTypeRef(ref, patterns)) {
            return;
        }
        violations.add(SecurityViolation.of(
                "resource." + ref.getTypeName(),
                SecuritySeverity.WARN,
                SecurityCategory.RESOURCE,
                ref.getLineNumber(),
                ref.getColumnNumber(),
                "脚本内" + action + "线程/定时器类，存在资源耗尽风险：" + ref.getTypeName(),
                ref.getSnippet()));
    }
}
