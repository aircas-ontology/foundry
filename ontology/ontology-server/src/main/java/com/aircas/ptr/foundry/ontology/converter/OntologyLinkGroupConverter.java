package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.common.util.DateUtils;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyLinkGroupDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;

public class OntologyLinkGroupConverter {


    public static EsOntologyLinkGroupDTO convert(OntologyLinkGroup ontologyMeta) {
        if (ontologyMeta == null) {
            return null;
        }
        EsOntologyLinkGroupDTO dto = new EsOntologyLinkGroupDTO();
        dto.setId(ontologyMeta.getId());
        dto.setName(ontologyMeta.getName());
        dto.setOntologySpaceId(ontologyMeta.getOntologySpaceId() == null
                ? null : ontologyMeta.getOntologySpaceId().longValue());
        dto.setOntologyUniqueIdentifierFrom(ontologyMeta.getOntologyUniqueIdentifierFrom());
        dto.setOntologyUniqueIdentifierTo(ontologyMeta.getOntologyUniqueIdentifierTo());
        dto.setStatus(ontologyMeta.getStatus());
        dto.setType(ontologyMeta.getType() == null ? null : ontologyMeta.getType().name());
        dto.setUniqueIdentifier(ontologyMeta.getUniqueIdentifier());
        dto.setCreateTime(DateUtils.toLocalDateTime(ontologyMeta.getCreateTime()));
        dto.setUpdateTime(DateUtils.toLocalDateTime(ontologyMeta.getUpdateTime()));
        return dto;
    }
}
