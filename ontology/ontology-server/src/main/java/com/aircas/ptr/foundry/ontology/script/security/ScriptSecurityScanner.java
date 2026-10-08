package com.aircas.ptr.foundry.ontology.script.security;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.config.ScriptSecurityProperties;
import com.aircas.ptr.foundry.ontology.script.security.model.ScriptAstInventory;
import com.aircas.ptr.foundry.ontology.script.security.model.ScriptScanResult;
import com.aircas.ptr.foundry.ontology.script.security.model.SecuritySeverity;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.CompilePhase;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 脚本安全扫描器：串联「体积校验 → 注解阶段 → 语义分析阶段 → 汇总」。
 * <p>
 * 为什么必须两段编译：保存动作本身会编译脚本，而 {@code @ASTTest} 会在语义分析阶段
 * <b>执行其中代码</b>。若一次性编译到语义分析再判注解，代码已经跑完了。
 * 因此先在 CONVERSION 阶段筛掉危险注解，确认安全后才推进编译。
 * <p>
 * 为什么要有超时：脚本是攻击者可控输入，解析器理论上可能陷入病态耗时。
 * JDK 17 已移除 SecurityManager（JEP 411），没有 JVM 级沙箱可用，
 * 只能在时间维度上兜底，且超时一律按<b>拒绝</b>处理。
 */
@Slf4j
@Component
public class ScriptSecurityScanner {

    private final ScriptAstParser parser;
    private final ScriptSecurityRuleEngine engine;
    private final ScriptSecurityProperties properties;

    /**
     * 容器共用的 {@code taskExecutor}（由 {@code ThreadPoolConfig} 声明），不在业务类里自建线程
     * 。该池有界且为 AbortPolicy，池满时提交会被拒绝，此处按 fail-closed 处理。
     */
    private final ThreadPoolTaskExecutor scanExecutor;

    public ScriptSecurityScanner(ScriptAstParser parser,
                                 ScriptSecurityRuleEngine engine,
                                 ScriptSecurityProperties properties,
                                 @Qualifier("taskExecutor") ThreadPoolTaskExecutor scanExecutor) {
        this.parser = parser;
        this.engine = engine;
        this.properties = properties;
        this.scanExecutor = scanExecutor;
    }

    /**
     * 扫描脚本。任何异常路径都返回「不通过」或抛出 BusinessException，绝不返回「通过」。
     */
    public ScriptScanResult scan(String code) {
        long start = System.currentTimeMillis();
        if (!properties.isEnabled()) {
            log.warn("script security scan is disabled by configuration, script accepted without scan");
            return ScriptScanResult.passed(elapsed(start));
        }
        if (code == null || code.isBlank()) {
            return ScriptScanResult.passed(elapsed(start));
        }
        // 体积校验放在解析之前，避免为超大脚本付出编译成本
        int size = code.getBytes(StandardCharsets.UTF_8).length;
        int maxSize = properties.getMaxScriptSize();
        if (maxSize > 0 && size > maxSize) {
            log.warn("script security scan: script too large, size={}, limit={}", size, maxSize);
            return ScriptScanResult.reject("脚本体积超过限制（" + size + " > " + maxSize + " 字节）");
        }

        long timeout = properties.getScanTimeoutMs() > 0 ? properties.getScanTimeoutMs() : 2000L;
        Future<ScriptScanResult> future;
        try {
            future = scanExecutor.submit(() -> doScan(code, start));
        } catch (RejectedExecutionException e) {
            // 池与队列均满：fail-closed，不静默放行
            log.error("script security scan rejected by executor, reject", e);
            return ScriptScanResult.reject("脚本安全检测繁忙，已拒绝保存，请稍后重试");
        }
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // fail-closed：解析超时按拒绝处理。注意 Groovy 编译器不响应中断，
            // 该线程可能仍在运行，但本次请求已确定拒绝。
            log.error("script security scan timed out after {}ms, reject", timeout);
            return ScriptScanResult.reject("脚本安全检测超时，已拒绝保存");
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof BusinessException) {
                // 语法错误等需要把明确原因透传给调用方
                throw (BusinessException) cause;
            }
            log.error("script security scan failed unexpectedly, reject", cause);
            return ScriptScanResult.reject("脚本安全检测失败，已拒绝保存");
        }
    }

    private ScriptScanResult doScan(String code, long start) {
        // 第一段：CONVERSION，只做注解判定
        ModuleNode conversionModule = parser.parse(code, CompilePhase.CONVERSION);
        ScriptAstInventory annotationInventory = new ScriptAstInventory();
        parser.collectAnnotations(conversionModule).forEach(annotationInventory::addAnnotation);
        List<SecurityViolation> annotationViolations =
                engine.run(new ScriptSecurityContext(code, annotationInventory, properties), CompilePhase.CONVERSION);
        if (isBlocking(annotationViolations)) {
            return build(annotationViolations, start);
        }

        // 第二段：SEMANTIC_ANALYSIS，类型已解析，做完整判定
        ModuleNode semanticModule = parser.parse(code, CompilePhase.SEMANTIC_ANALYSIS);
        ScriptAstInventory inventory = parser.collectInventory(semanticModule);
        List<SecurityViolation> violations =
                engine.run(new ScriptSecurityContext(code, inventory, properties), CompilePhase.SEMANTIC_ANALYSIS);

        List<SecurityViolation> all = new ArrayList<>(annotationViolations);
        all.addAll(violations);
        return build(all, start);
    }

    private ScriptScanResult build(List<SecurityViolation> violations, long start) {
        ScriptScanResult result = ScriptScanResult.of(
                violations,
                properties.isBlockOnViolation(),
                properties.isBlockOnWarn(),
                elapsed(start));
        if (!result.isPassed()) {
            // 只记规则与位置，绝不写脚本正文（CODING_CONVENTIONS §13）
            log.warn("script security scan rejected, summary={}, violations={}",
                    result.getSummary(), result.getViolations().size());
        }
        return result;
    }

    private boolean isBlocking(List<SecurityViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return false;
        }
        for (SecurityViolation v : violations) {
            if (v.getSeverity() == SecuritySeverity.DANGER) {
                return properties.isBlockOnViolation();
            }
            if (v.getSeverity() == SecuritySeverity.WARN) {
                return properties.isBlockOnWarn();
            }
        }
        return false;
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
