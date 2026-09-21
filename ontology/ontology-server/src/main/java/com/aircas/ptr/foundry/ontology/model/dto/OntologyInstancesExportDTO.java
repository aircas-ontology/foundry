package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 本体实例数据结构，作为 {@link OntologyCreateDTO#getInstances()} 段。
 *
 * <p>导出时从数据湖物理表装配；导入时若带有该段则回写数据湖（id 由数据库重新生成）。</p>
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologyInstancesExportDTO {

    /**
     * 实例节点列表
     */
    private List<EntityNodeExportDTO> nodes;
}
