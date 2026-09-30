package com.aircas.ptr.foundry.ontology.script.security;

import com.aircas.ptr.foundry.ontology.script.security.model.SecurityCategory;
import com.aircas.ptr.foundry.ontology.script.security.model.SecuritySeverity;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilePhase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 规则引擎：按编译阶段分组执行规则，并汇总违规。
 * <p>
 * 两条硬性约束：
 * <ol>
 *   <li><b>单条规则异常不得吞掉</b>：规则自身抛异常等价于「这条规则没生效」，
 *       若静默继续等于放行未知脚本，故统一转换为 SCANNER 级 DANGER（fail-closed）。</li>
 *   <li><b>执行顺序确定</b>：按规则名排序，保证违规列表顺序稳定，便于测试与日志比对。</li>
 * </ol>
 */
@Slf4j
@Component
public class ScriptSecurityRuleEngine {

    private final Map<CompilePhase, List<ScriptSecurityRule>> rulesByPhase;

    public ScriptSecurityRuleEngine(List<ScriptSecurityRule> rules) {
        List<ScriptSecurityRule> effective = rules == null
                ? List.of()
                : rules.stream()
                        .filter(r -> r != null && r.enabled())
                        .sorted(Comparator.comparing(ScriptSecurityRule::name))
                        .collect(Collectors.toList());
        this.rulesByPhase = effective.stream()
                .collect(Collectors.groupingBy(ScriptSecurityRule::phase));
        log.info("script security rule engine initialized, rules={}, phases={}",
                effective.size(), rulesByPhase.keySet());
    }

    /** 当前生效的规则名，供自检与运维核对 */
    public List<String> ruleNames() {
        return rulesByPhase.values().stream()
                .flatMap(List::stream)
                .map(ScriptSecurityRule::name)
                .collect(Collectors.toList());
    }

    public List<SecurityViolation> run(ScriptSecurityContext context, CompilePhase phase) {
        List<ScriptSecurityRule> phaseRules = rulesByPhase.getOrDefault(phase, List.of());
        List<SecurityViolation> violations = new ArrayList<>();
        for (ScriptSecurityRule rule : phaseRules) {
            try {
                List<SecurityViolation> found = rule.inspect(context);
                if (found != null) {
                    violations.addAll(found);
                }
            } catch (Exception e) {
                // fail-closed：规则执行失败按「未通过」处理，绝不静默放行
                log.error("script security rule failed, rule={}", rule.name(), e);
                violations.add(SecurityViolation.of(
                        "scanner.rule_error." + rule.name(),
                        SecuritySeverity.DANGER,
                        SecurityCategory.SCANNER,
                        -1,
                        -1,
                        "安全规则执行失败，已拒绝保存",
                        null));
            }
        }
        return dedupe(violations);
    }

    /**
     * 去重：同一规则在同一位置命中多次只保留一条。
     * 脚本会被编译成 Script 类，其 run() 方法体与语句块在极端情况下可能被重复采集。
     */
    private static List<SecurityViolation> dedupe(List<SecurityViolation> violations) {
        Set<String> seen = new LinkedHashSet<>();
        List<SecurityViolation> result = new ArrayList<>();
        for (SecurityViolation v : violations) {
            if (v == null) {
                continue;
            }
            String key = v.getRuleId() + "|" + v.getLineNumber() + "|" + v.getColumnNumber();
            if (seen.add(key)) {
                result.add(v);
            }
        }
        return result;
    }
}
