package com.aircas.ptr.foundry.ontology.script.security.rule;

import com.aircas.ptr.foundry.ontology.script.security.RuleMatchers;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityContext;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityRule;
import com.aircas.ptr.foundry.ontology.script.security.model.AstCallRef;
import com.aircas.ptr.foundry.ontology.script.security.model.AstTypeRef;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityCategory;
import com.aircas.ptr.foundry.ontology.script.security.model.SecuritySeverity;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * C5 平台内部类调用（P0-1）。
 * <p>
 * 脚本的 GroovyClassLoader 父加载器就是应用类加载器，因此脚本可以直接 import 平台类。
 * 已实测可达的高危入口：
 * <ul>
 *   <li>{@code CommandUtil.executeCommand} —— 内部包装 {@code Runtime.getRuntime().exec}</li>
 *   <li>{@code BeanUtil.getBean} —— 持有静态 ApplicationContext，可取得 DataSource / JdbcTemplate / ArangoDB</li>
 *   <li>{@code HttpUtil} —— okhttp 客户端</li>
 * </ul>
 * 逐个拉黑类名无法收敛，因此采用「前缀默认禁止 + 显式放行名单」。
 * 放行名单必须包含 {@code model.vo}：GroovyServiceImpl 要求 handle 返回 FunctionResultVO。
 */
@Component
public class InternalPackageRule implements ScriptSecurityRule {

    @Override
    public String name() {
        return "InternalPackageRule";
    }

    @Override
    public List<SecurityViolation> inspect(ScriptSecurityContext context) {
        List<SecurityViolation> violations = new ArrayList<>();
        List<String> prefixes = context.getProperties().getInternalPackagePrefixes();
        List<String> allowlist = context.getProperties().getInternalPackageAllowlist();

        // C5a 类型引用：import / 构造器
        for (AstTypeRef ref : context.getInventory().getImports()) {
            checkType(violations, ref, "引用", prefixes, allowlist);
        }
        for (AstTypeRef ref : context.getInventory().getConstructorCalls()) {
            checkType(violations, ref, "构造", prefixes, allowlist);
        }
        // C5b 调用：实例方法与静态方法
        for (AstCallRef call : context.getInventory().getMethodCalls()) {
            checkCall(violations, call, prefixes, allowlist);
        }
        for (AstCallRef call : context.getInventory().getStaticMethodCalls()) {
            checkCall(violations, call, prefixes, allowlist);
        }
        return violations;
    }

    private void checkType(List<SecurityViolation> violations, AstTypeRef ref, String action,
                           List<String> prefixes, List<String> allowlist) {
        String typeName = ref.getTypeName();
        if (!isInternal(typeName, prefixes) || isAllowed(typeName, allowlist)) {
            return;
        }
        violations.add(SecurityViolation.of(
                "internal.type." + typeName,
                SecuritySeverity.DANGER,
                SecurityCategory.INTERNAL,
                ref.getLineNumber(),
                ref.getColumnNumber(),
                "禁止" + action + "平台内部类：" + typeName,
                ref.getSnippet()));
    }

    private void checkCall(List<SecurityViolation> violations, AstCallRef call,
                           List<String> prefixes, List<String> allowlist) {
        String receiver = call.getReceiverType();
        if (!isInternal(receiver, prefixes) || isAllowed(receiver, allowlist)) {
            return;
        }
        violations.add(SecurityViolation.of(
                "internal.call." + receiver + "#" + call.getMethodName(),
                SecuritySeverity.DANGER,
                SecurityCategory.INTERNAL,
                call.getLineNumber(),
                call.getColumnNumber(),
                "禁止调用平台内部类方法：" + receiver + "#" + call.getMethodName(),
                call.getSnippet()));
    }

    /**
     * 前缀按「包前缀」语义匹配，不是全名相等：
     * 写成 com.aircas.ptr.foundry 就必须能命中其下所有子包，否则规则等于没生效。
     */
    private static boolean isInternal(String typeName, List<String> prefixes) {
        return RuleMatchers.matchesPackagePrefix(typeName, prefixes);
    }

    private static boolean isAllowed(String typeName, List<String> allowlist) {
        return RuleMatchers.matchesPackagePrefix(typeName, allowlist);
    }
}
