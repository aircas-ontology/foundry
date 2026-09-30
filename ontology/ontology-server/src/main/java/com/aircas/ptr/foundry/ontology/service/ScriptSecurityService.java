package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.vo.FunctionSecurityScanVO;

/**
 * 行为算子（函数）代码安全检测。
 * <p>
 * A 方案范围：保存前的静态 AST 扫描 + 保存前的独立预校验接口。
 * 不含运行期沙箱（进程/容器隔离）、不含执行超时、不含提交人角色限制。
 */
public interface ScriptSecurityService {

    /**
     * 检测函数代码。
     *
     * @param code 函数源码
     * @return 检测结果；{@code passed=false} 时调用方应拒绝保存
     */
    FunctionSecurityScanVO validateCode(String code);
}
