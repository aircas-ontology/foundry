package com.aircas.ptr.foundry.ontology.model.dto;


import com.aircas.ptr.foundry.ontology.model.param.PropertyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyMetadataSchemaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyMetadataSchemaUpdateParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologyCreateDTO {

    private OntologyMetaDataDTO metadata;

    private PropertyCategoryCreateParam propertyCategory;

    private PropertyMetadataSchemaCreateParam propertySchema;

    private List<OntologyPropertyDTO> properties;

    private List<OntologyRelationDTO> relations;

    private List<OntologyFunctionDTO> functions;

    private List<OntologyActionDTO> actions;

    /**
     * 本体实例数据（全量）。导出时填充；导入时若带有该段则写回数据湖物理表。
     */
    private OntologyInstancesExportDTO instances;
}
