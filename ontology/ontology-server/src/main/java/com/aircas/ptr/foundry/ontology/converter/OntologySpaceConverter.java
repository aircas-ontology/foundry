package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.common.util.DateUtils;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologySpaceDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;

public class OntologySpaceConverter {
    public static EsOntologySpaceDTO convert(OntologySpace ontologySpace) {
        if (ontologySpace == null) {
            return null;
        }
        EsOntologySpaceDTO dto = new EsOntologySpaceDTO();
        dto.setId(ontologySpace.getId() == null ? null : ontologySpace.getId().longValue());
        dto.setApiName(ontologySpace.getApiName());
        dto.setDescription(ontologySpace.getDescription());
        dto.setDisplayName(ontologySpace.getDisplayName());
        dto.setCreateTime(DateUtils.toLocalDateTime(ontologySpace.getCreateTime()));
        dto.setUpdateTime(DateUtils.toLocalDateTime(ontologySpace.getUpdateTime()));
        return dto;
    }
}
