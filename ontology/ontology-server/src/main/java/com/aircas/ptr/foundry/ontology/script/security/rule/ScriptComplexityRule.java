package com.aircas.ptr.foundry.ontology.script.security.rule;

import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityContext;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityRule;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityCategory;
import com.aircas.ptr.foundry.ontology.script.security.model.SecuritySeverity;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * C9 结构复杂度：AST 嵌套深度。
 * <p>
 * 目的不是「审查代码风格」，而是给解析器兜底：深嵌套会让访问器递归与后续编译阶段
 * 消耗显著的时间和栈空间，属于可被构造的拒绝服务输入。
 * <p>
 * 脚本<b>体积</b>由扫描器在解析前先行拦截（避免为超大脚本付出解析成本），不在此处重复判定。
 */
@Component
public class ScriptComplexityRule implements ScriptSecurityRule {

    private static final int HARD_DEPTH_LIMIT = 2000;

    @Override
    public String name() {
        return "ScriptComplexityRule";
    }

    @Override
    public List<SecurityViolation> inspect(ScriptSecurityContext context) {
        List<SecurityViolation> violations = new ArrayList<>();
        int maxDepth = context.getInventory().getMaxDepth();
        int configured = context.getProperties().getMaxAstDepth();
        int limit = configured <= 0 ? HARD_DEPTH_LIMIT : configured;
        if (maxDepth > limit) {
            violations.add(SecurityViolation.of(
                    "size.ast_too_deep",
                    SecuritySeverity.DANGER,
                    SecurityCategory.SIZE,
                    -1,
                    -1,
                    "脚本嵌套层级过深（" + maxDepth + " > " + limit + "），请拆分逻辑",
                    null));
        }
        return violations;
    }
}
