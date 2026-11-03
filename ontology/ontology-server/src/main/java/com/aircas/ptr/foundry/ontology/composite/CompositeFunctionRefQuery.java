package com.aircas.ptr.foundry.ontology.composite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 函数编排中被引用函数的查询条件。
 * <p>函数算子节点原本只按 {@code functionId}（即函数 api）解析；为支持按名称+版本引用，
 * 这里把 {@code functionId} 与 {@code functionVersion} 一起传给解析器，解析器优先按 api 精确匹配，
 * 未命中时再按显示名+版本匹配。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompositeFunctionRefQuery {

    /** 函数标识：通常为 api；按 api 未命中时，可回退按 displayName 解析 */
    private String functionId;

    /** 函数版本（可选），与 functionId 配合使用 */
    private String functionVersion;

    public static CompositeFunctionRefQuery of(String functionId, String functionVersion) {
        return new CompositeFunctionRefQuery(functionId, functionVersion);
    }
}
