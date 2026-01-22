package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologyRelationDTO {

    private String name;
    private String ontologyUniqueIdentifierFrom;
    private String ontologyUniqueIdentifierTo;
    private OntologyLinkTypeEnum type;
}
