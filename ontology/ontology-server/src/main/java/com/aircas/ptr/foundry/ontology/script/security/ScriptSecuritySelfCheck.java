package com.aircas.ptr.foundry.ontology.script.security;

import com.aircas.ptr.foundry.ontology.config.ScriptSecurityProperties;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionSecurityScanVO;
import com.aircas.ptr.foundry.ontology.service.ScriptSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动自检。
 * <p>
 * 存在的理由：安全检测最大的风险不是误报，而是<b>静默失效</b>——
 * 规则因为配置缺失、依赖注入失败、API 变更等原因不再命中，而系统照常放行，
 * 从外部看一切正常。因此启动后用一批已知恶意样本断言「必须被拦住」，
 * 同时用一批正常样本断言「必须放行」。
 * <p>
 * 自检失败只记 error 日志、不阻断启动：安全能力失效时阻断业务启动会造成自伤式故障，
 * 但该日志必须接入告警（标记 SELF_CHECK_FAILED）。
 */
@Slf4j
@Component
@Order(1000)
@RequiredArgsConstructor
public class ScriptSecuritySelfCheck implements ApplicationRunner {

    private final ScriptSecurityService scriptSecurityService;
    private final ScriptSecurityProperties properties;

    /** 必须被拦截的样本：每一条都对应一个已实证的绕过手法 */
    private static final List<String[]> MUST_BLOCK = List.of(
            new String[]{"runtime_exec", "def handle(Map params) { return Runtime.getRuntime().exec(\"id\") }"},
            new String[]{"gdk_execute", "def handle(Map params) { return \"id\".execute() }"},
            new String[]{"fqn_process_builder", "def handle(Map params) { return new java.lang.ProcessBuilder([\"sh\",\"-c\",\"id\"]).start() }"},
            new String[]{"reflection_chain", "def handle(Map params) { return Class.forName(\"java.lang.Runtime\").getMethod(\"exec\", String.class).invoke(null, \"id\") }"},
            new String[]{"grab_annotation", "class Calc {\n"
                    + "  @Grab('org.apache.commons:commons-lang3:3.12.0')\n"
                    + "  def handle(Map params) { return 1 }\n"
                    + "}"},
            new String[]{"internal_command_util", "def handle(Map params) { com.aircas.ptr.foundry.common.util.CommandUtil.executeCommand(\"id\"); return 1 }"},
            new String[]{"file_constructor", "def handle(Map params) { return new java.io.File(\"/etc/passwd\") }"},
            new String[]{"socket_constructor", "def handle(Map params) { return new java.net.Socket(\"10.0.0.1\", 9999) }"}
    );

    /** 必须放行的样本：用于发现误报 */
    private static final List<String[]> MUST_PASS = List.of(
            new String[]{"benign_sum", "def handle(Map params) { def xs = [1, 2, 3]\n return xs.sum() }"},
            new String[]{"benign_string_op", "def handle(Map params) { return (params.name as String).toUpperCase() + \"!\" }"},
            new String[]{"benign_vo_allowlist", "import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO\n"
                    + "def handle(Map params) { return FunctionResultVO.builder().description(\"ok\").build() }"}
    );

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSelfCheckOnStartup()) {
            log.warn("script security self-check is disabled by configuration");
            return;
        }
        List<String> missed = new ArrayList<>();
        for (String[] sample : MUST_BLOCK) {
            FunctionSecurityScanVO result = scriptSecurityService.validateCode(sample[1]);
            if (result.isPassed()) {
                missed.add(sample[0]);
            }
        }
        List<String> falsePositives = new ArrayList<>();
        for (String[] sample : MUST_PASS) {
            FunctionSecurityScanVO result = scriptSecurityService.validateCode(sample[1]);
            if (!result.isPassed()) {
                falsePositives.add(sample[0] + " -> " + result.getSummary());
            }
        }

        if (missed.isEmpty() && falsePositives.isEmpty()) {
            log.info("script security self-check passed, maliciousSamples={}, benignSamples={}",
                    MUST_BLOCK.size(), MUST_PASS.size());
            return;
        }
        // 这两类都是「安全能力不可用」的信号，必须告警
        log.error("SELF_CHECK_FAILED script security detection is not working as expected, "
                        + "missedMaliciousSamples={}, falsePositiveSamples={}",
                missed, falsePositives);
    }
}
