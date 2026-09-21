package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 空间导出的中间承载结构：一次装配得到本体列表与空间级函数列表。
 *
 * <p>供 {@code exportOntologiesWithFunctions} 返回，使本体装配与函数收集共享同一次
 * space/metas 加载与函数详情缓存，避免重复查询。</p>
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologySpaceExportData {

    /**
     * 空间下全部本体导出对象
     */
    private List<OntologyCreateDTO> ontologies;

    /**
     * 空间级函数导出对象
     */
    private List<OntologyFunctionDTO> functions;
}
