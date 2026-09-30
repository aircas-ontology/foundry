package com.aircas.ptr.foundry.ontology.script.security.rule;

import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityContext;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityRule;
import com.aircas.ptr.foundry.ontology.script.security.model.AstCallRef;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityCategory;
import com.aircas.ptr.foundry.ontology.script.security.model.SecuritySeverity;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * C7 反射调用链。
 * <p>
 * 典型攻击：{@code Class.forName("java.lang.Runtime").getMethod("exec", String).invoke(null, "id")}。
 * 单独看每一环都只是普通方法调用，且动态派发下接收者类型可能解析不出来，
 * 单靠「类#方法」黑名单并不可靠。本规则按<b>调用链形态</b>判定：
 * 「产反射对象的调用」后面紧跟「用反射对象的调用」。
 * <p>
 * 判定只依赖 AST 结构（receiverMethodName），不依赖类型解析结果，因此动态派发同样有效。
 */
@Component
public class ReflectionChainRule implements ScriptSecurityRule {

    /** 产出 Class/Method/Field/Constructor 等反射对象的方法 */
    private static final Set<String> REFLECTION_PRODUCERS = Set.of(
            "forName", "getMethod", "getDeclaredMethod", "getMethods", "getDeclaredMethods",
            "getField", "getDeclaredField", "getFields", "getDeclaredFields",
            "getConstructor", "getDeclaredConstructor", "getConstructors", "getDeclaredConstructors",
            "loadClass", "newInstance");

    /** 消费反射对象、真正触发副作用的方法 */
    private static final Set<String> REFLECTION_CONSUMERS = Set.of(
            "invoke", "setAccessible", "newInstance", "set", "get",
            "getMethod", "getDeclaredMethod", "getField", "getDeclaredField",
            "getConstructor", "getDeclaredConstructor");

    @Override
    public String name() {
        return "ReflectionChainRule";
    }

    @Override
    public List<SecurityViolation> inspect(ScriptSecurityContext context) {
        List<SecurityViolation> violations = new ArrayList<>();
        for (AstCallRef call : context.getInventory().getMethodCalls()) {
            String receiverMethod = call.getReceiverMethodName();
            if (receiverMethod == null || call.getMethodName() == null) {
                continue;
            }
            if (!REFLECTION_PRODUCERS.contains(receiverMethod)) {
                continue;
            }
            if (!REFLECTION_CONSUMERS.contains(call.getMethodName())) {
                continue;
            }
            violations.add(SecurityViolation.of(
                    "reflection.chain." + receiverMethod + "->" + call.getMethodName(),
                    SecuritySeverity.DANGER,
                    SecurityCategory.REFLECTION,
                    call.getLineNumber(),
                    call.getColumnNumber(),
                    "禁止使用反射调用链：" + receiverMethod + "(...)." + call.getMethodName() + "(...)",
                    call.getSnippet()));
        }
        return violations;
    }
}
