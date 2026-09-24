package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.common.util.DateUtils;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyMetaDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;

public class OntologyMetaConverter {
    public static EsOntologyMetaDTO convert(OntologyMeta ontologyMeta) {
        if (ontologyMeta == null) {
            return null;
        }
        EsOntologyMetaDTO dto = new EsOntologyMetaDTO();
        dto.setId(ontologyMeta.getId());
        dto.setApiName(ontologyMeta.getApiName());
        dto.setDescription(ontologyMeta.getDescription());
        dto.setDisplayName(ontologyMeta.getDisplayName());
        dto.setOntologySpaceId(ontologyMeta.getOntologySpaceId() == null
                ? null : ontologyMeta.getOntologySpaceId().longValue());
        dto.setStatus(ontologyMeta.getStatus());
        dto.setUniqueIdentifier(ontologyMeta.getUniqueIdentifier());
        dto.setCreateTime(DateUtils.toLocalDateTime(ontologyMeta.getCreateTime()));
        dto.setUpdateTime(DateUtils.toLocalDateTime(ontologyMeta.getUpdateTime()));
        return dto;
    }
}
