package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 本体实例数据导出结构，作为导出专有段挂在 {@link OntologyCreateDTO#getInstances()} 上。
 *
 * <p>导入侧当前不消费该段。</p>
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
