package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.common.util.DateUtils;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;

public class OntologyPropertyConverter {
    public static EsOntologyPropertyDTO convert(OntologyProperty ontologyProperty) {
        if (ontologyProperty == null) {
            return null;
        }
        EsOntologyPropertyDTO dto = new EsOntologyPropertyDTO();
        dto.setId(ontologyProperty.getId());
        dto.setApiName(ontologyProperty.getApiName());
        dto.setDescription(ontologyProperty.getDescription());
        dto.setDisplayName(ontologyProperty.getDisplayName());
        dto.setIsPrimaryKey(ontologyProperty.getIsPrimaryKey() != null && ontologyProperty.getIsPrimaryKey() == 1);
        dto.setIsTitleKey(ontologyProperty.getIsTitleKey() != null && ontologyProperty.getIsTitleKey() == 1);
        dto.setOntologyUniqueIdentifier(ontologyProperty.getOntologyUniqueIdentifier());
        dto.setPropertyType(ontologyProperty.getPropertyType() == null
                ? null : ontologyProperty.getPropertyType().getValue());
        dto.setStatus(ontologyProperty.getStatus());
        dto.setUniqueIdentifier(ontologyProperty.getUniqueIdentifier());
        dto.setCreateTime(DateUtils.toLocalDateTime(ontologyProperty.getCreateTime()));
        dto.setUpdateTime(DateUtils.toLocalDateTime(ontologyProperty.getUpdateTime()));
        return dto;
    }
}
