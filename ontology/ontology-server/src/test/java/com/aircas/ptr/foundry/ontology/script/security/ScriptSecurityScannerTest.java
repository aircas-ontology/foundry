package com.aircas.ptr.foundry.ontology.script.security;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.config.ScriptSecurityProperties;
import com.aircas.ptr.foundry.ontology.script.security.model.ScriptScanResult;
import com.aircas.ptr.foundry.ontology.script.security.rule.DangerousAnnotationRule;
import com.aircas.ptr.foundry.ontology.script.security.rule.DangerousConstructorRule;
import com.aircas.ptr.foundry.ontology.script.security.rule.DangerousImportRule;
import com.aircas.ptr.foundry.ontology.script.security.rule.DangerousMethodCallRule;
import com.aircas.ptr.foundry.ontology.script.security.rule.GroovyExtensionMethodRule;
import com.aircas.ptr.foundry.ontology.script.security.rule.InternalPackageRule;
import com.aircas.ptr.foundry.ontology.script.security.rule.ReflectionChainRule;
import com.aircas.ptr.foundry.ontology.script.security.rule.ResourceExhaustionRule;
import com.aircas.ptr.foundry.ontology.script.security.rule.ScriptComplexityRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全检测样本矩阵验证。
 * <p>
 * 不加载 Spring 上下文：规则引擎与扫描器都可以手工装配，跑得快且不受环境配置影响。
 * 这里断言的是「能力」，不是「实现细节」——恶意样本必须被拦、正常样本必须放行。
 */
class ScriptSecurityScannerTest {

    private ScriptSecurityScanner scanner;

    /** 与生产一致：扫描器不自建线程，线程池由外部注入 */
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("script-security-test-");
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        scanner = null;
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    /** 每条样本对应一种已实证的绕过手法 */
    private static final String[][] MALICIOUS = {
            {"runtime_exec", "class Calc { def handle(Map p) { return Runtime.getRuntime().exec(\"id\") } }"},
            {"string_execute_gdk", "class Calc { def handle(Map p) { return \"id\".execute() } }"},
            {"fqn_process_builder", "class Calc { def handle(Map p) { return new java.lang.ProcessBuilder([\"sh\",\"-c\",\"id\"]).start() } }"},
            {"import_process_builder", "import java.lang.ProcessBuilder\n"
                    + "class Calc { def handle(Map p) { return new ProcessBuilder([\"id\"]).start() } }"},
            {"reflection_chain", "class Calc { def handle(Map p) { return Class.forName(\"java.lang.Runtime\").getMethod(\"exec\", String.class).invoke(null, \"id\") } }"},
            {"grab_annotation", "class Calc {\n"
                    + "  @Grab('org.apache.commons:commons-lang3:3.12.0')\n"
                    + "  def handle(Map p) { return 1 }\n"
                    + "}"},
            {"internal_command_util", "class Calc { def handle(Map p) { com.aircas.ptr.foundry.common.util.CommandUtil.executeCommand(\"id\"); return 1 } }"},
            {"internal_bean_util", "import com.aircas.ptr.foundry.common.util.BeanUtil\n"
                    + "class Calc { def handle(Map p) { return BeanUtil.getBean(\"dataSource\") } }"},
            {"file_read", "class Calc { def handle(Map p) { return new java.io.File(\"/etc/passwd\") } }"},
            {"socket_connect", "class Calc { def handle(Map p) { return new java.net.Socket(\"10.0.0.1\", 9999) } }"},
            {"url_fetch", "class Calc { def handle(Map p) { return new java.net.URL(\"http://evil.example.com\").getText() } }"},
            {"system_exit", "class Calc { def handle(Map p) { System.exit(1); return 1 } }"},
            {"system_getenv", "class Calc { def handle(Map p) { return System.getenv(\"PATH\") } }"},
            {"groovy_shell_eval", "class Calc { def handle(Map p) { return new groovy.lang.GroovyShell().evaluate(\"1+1\") } }"},
            {"class_loader", "class Calc { def handle(Map p) { return Class.forName(\"java.lang.Runtime\") } }"},
            {"thread_creation", "class Calc { def handle(Map p) { new java.lang.Thread({ -> Thread.sleep(999999) }).start(); return 1 } }"}
    };

    /** 正常业务脚本：必须放行，用于发现误报 */
    private static final String[][] BENIGN = {
            {"list_sum", "class Calc { def handle(Map p) { def xs = [1, 2, 3]\n return xs.sum() } }"},
            {"string_ops", "class Calc { def handle(Map p) { return ((String) p.get(\"name\")).toUpperCase() + \"!\" } }"},
            {"arithmetic", "class Calc { def handle(Map p) { def a = p.get(\"a\") as Integer\n return a * 2 + 1 } }"},
            {"vo_allowlist", "import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO\n"
                    + "class Calc { def handle(Map p) { return FunctionResultVO.builder().description(\"ok\").build() } }"},
            {"json_build", "import groovy.json.JsonOutput\n"
                    + "class Calc { def handle(Map p) { return JsonOutput.toJson([name: p.get(\"name\")]) } }"},
            {"collect_each", "class Calc { def handle(Map p) { return p.values().collect { it?.toString() }.join(\",\") } }"},
            // 真实脚本形态：GroovyServiceImpl 要求 handle 返回 FunctionResultVO，
            // 因此用户脚本必然 import 并调用平台 VO，这两条都必须放行，否则等于全量误报
            {"real_shape_visibility", "import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO\n"
                    + "class VisibilityHandler {\n"
                    + "  FunctionResultVO handle(Double lat, Double lon) {\n"
                    + "    def visible = lat > 0 && lon > 0\n"
                    + "    return FunctionResultVO.builder().description(visible ? \"visible\" : \"hidden\").data(visible).build()\n"
                    + "  }\n"
                    + "}"},
            {"real_shape_with_jdk", "import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO\n"
                    + "import java.time.LocalDateTime\n"
                    + "class OrbitHandler {\n"
                    + "  FunctionResultVO handle(Double x) {\n"
                    + "    def now = LocalDateTime.now()\n"
                    + "    return FunctionResultVO.builder().data(Math.sin(x)).description(now.toString()).build()\n"
                    + "  }\n"
                    + "}"}
    };

    @Test
    void maliciousScriptsMustBeBlocked() {
        List<String> missed = new ArrayList<>();
        for (String[] sample : MALICIOUS) {
            ScriptScanResult result = scanner().scan(sample[1]);
            if (result.isPassed()) {
                missed.add(sample[0]);
            } else {
                System.out.println("[BLOCKED] " + sample[0] + " -> " + result.getViolations().stream()
                        .map(v -> v.getRuleId()).collect(java.util.stream.Collectors.joining(",")));
            }
        }
        assertTrue(missed.isEmpty(), "以下恶意样本未被拦截: " + missed);
    }

    @Test
    void benignScriptsMustPass() {
        List<String> falsePositives = new ArrayList<>();
        for (String[] sample : BENIGN) {
            ScriptScanResult result = scanner().scan(sample[1]);
            if (!result.isPassed()) {
                falsePositives.add(sample[0] + " -> " + result.getViolations().stream()
                        .map(v -> v.getRuleId()).collect(java.util.stream.Collectors.joining(",")));
            }
        }
        assertTrue(falsePositives.isEmpty(), "以下正常样本被误报: " + falsePositives);
    }

    @Test
    void specificRulesMustFireWithExpectedId() {
        assertRuleFired("class Calc { def handle(Map p) { return new java.io.File(\"/etc/passwd\") } }",
                "ctor.java.io.File");
        assertRuleFired("class Calc { def handle(Map p) { return new java.net.Socket(\"h\", 1) } }",
                "ctor.java.net.Socket");
        assertRuleFired("class Calc { def handle(Map p) { return \"id\".execute() } }",
                "gdk.String#execute");
        assertRuleFired("class Calc { def handle(Map p) { return Class.forName(\"x\").getMethod(\"y\").invoke(null) } }",
                "reflection.chain.forName->getMethod");
        assertRuleFired("class Calc {\n  @Grab('a:b:1')\n  def handle(Map p) { return 1 }\n}",
                "annotation.Grab");
        assertRuleFired("class Calc { def handle(Map p) { com.aircas.ptr.foundry.common.util.CommandUtil.executeCommand(\"id\"); return 1 } }",
                "internal.call.com.aircas.ptr.foundry.common.util.CommandUtil#executeCommand");
    }

    @Test
    void syntaxErrorMustBeRejectedWithClearMessage() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> scanner().scan("class Calc { def handle(Map p) { return (( } }"));
        assertTrue(ex.getMessage().contains("解析失败"), "实际消息: " + ex.getMessage());
    }

    @Test
    void blankCodePasses() {
        assertTrue(scanner().scan(null).isPassed());
        assertTrue(scanner().scan("").isPassed());
        assertTrue(scanner().scan("   \n  ").isPassed());
    }

    @Test
    void oversizedScriptIsRejectedBeforeParsing() {
        ScriptSecurityProperties properties = new ScriptSecurityProperties();
        properties.setMaxScriptSize(64);
        String big = "class Calc { def handle(Map p) { return '" + "x".repeat(200) + "' } }";
        ScriptScanResult result = newScanner(properties).scan(big);
        assertFalse(result.isPassed());
        assertTrue(result.getSummary().contains("体积"), "实际摘要: " + result.getSummary());
    }

    @Test
    void warnOnlyScriptIsBlockedByDefault() {
        // 线程创建只判 WARN，但 blockOnWarn 默认为 true，因此仍然拦截
        ScriptScanResult result = scanner().scan(
                "class Calc { def handle(Map p) { return new java.util.Timer() } }");
        assertFalse(result.isPassed());
        assertNotNull(result.getViolations());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.getRuleId().startsWith("resource.")));
    }

    @Test
    void disabledScannerPassesEverything() {
        ScriptSecurityProperties properties = new ScriptSecurityProperties();
        properties.setEnabled(false);
        assertTrue(newScanner(properties)
                .scan("class Calc { def handle(Map p) { return Runtime.getRuntime().exec(\"id\") } }")
                .isPassed());
    }

    /** 复用同一个扫描器，避免每个样本重复构造规则引擎 */
    private ScriptSecurityScanner scanner() {
        if (scanner == null) {
            scanner = newScanner(new ScriptSecurityProperties());
        }
        return scanner;
    }

    /** 与生产一致：扫描器不自建线程，线程池由外部注入 */
    private ScriptSecurityScanner newScanner(ScriptSecurityProperties properties) {
        ScriptSecurityRuleEngine engine = new ScriptSecurityRuleEngine(List.of(
                new DangerousImportRule(),
                new DangerousMethodCallRule(),
                new DangerousConstructorRule(),
                new DangerousAnnotationRule(),
                new InternalPackageRule(),
                new GroovyExtensionMethodRule(),
                new ReflectionChainRule(),
                new ResourceExhaustionRule(),
                new ScriptComplexityRule()));
        return new ScriptSecurityScanner(new ScriptAstParser(), engine, properties, executor);
    }

    private void assertRuleFired(String code, String expectedRuleId) {
        ScriptScanResult result = scanner().scan(code);
        boolean fired = result.getViolations().stream()
                .anyMatch(v -> expectedRuleId.equals(v.getRuleId()));
        assertTrue(fired, "期望命中规则 " + expectedRuleId + "，实际命中: "
                + result.getViolations().stream().map(v -> v.getRuleId())
                .collect(java.util.stream.Collectors.joining(",")));
    }
}
