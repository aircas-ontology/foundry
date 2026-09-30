package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.vo.FunctionSecurityScanVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionSecurityViolationVO;
import com.aircas.ptr.foundry.ontology.script.security.ScriptSecurityScanner;
import com.aircas.ptr.foundry.ontology.script.security.model.ScriptScanResult;
import com.aircas.ptr.foundry.ontology.script.security.model.SecurityViolation;
import com.aircas.ptr.foundry.ontology.service.ScriptSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 安全检测服务实现。内部模型到 VO 的转换在此完成（不在 Controller 中做）。
 */
@Service
@RequiredArgsConstructor
public class ScriptSecurityServiceImpl implements ScriptSecurityService {

    private final ScriptSecurityScanner scanner;

    @Override
    public FunctionSecurityScanVO validateCode(String code) {
        ScriptScanResult result = scanner.scan(code);
        return toVO(result);
    }

    private FunctionSecurityScanVO toVO(ScriptScanResult result) {
        List<FunctionSecurityViolationVO> violations = result.getViolations() == null
                ? List.of()
                : result.getViolations().stream().map(this::toViolationVO).collect(Collectors.toList());
        return FunctionSecurityScanVO.builder()
                .passed(result.isPassed())
                .highestSeverity(result.getHighestSeverity() == null ? null : result.getHighestSeverity().name())
                .summary(result.getSummary())
                .scanDurationMs(result.getScanDurationMs())
                .violations(violations)
                .build();
    }

    private FunctionSecurityViolationVO toViolationVO(SecurityViolation v) {
        return FunctionSecurityViolationVO.builder()
                .ruleId(v.getRuleId())
                .severity(v.getSeverity() == null ? null : v.getSeverity().name())
                .category(v.getCategory() == null ? null : v.getCategory().name())
                .lineNumber(v.getLineNumber())
                .columnNumber(v.getColumnNumber())
                .message(v.getMessage())
                .snippet(v.getSnippet())
                .build();
    }
}
