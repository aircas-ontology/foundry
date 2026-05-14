package com.aircas.ptr.foundry.ontology.model.dto;


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

    private List<OntologyPropertyDTO> properties;

    private List<OntologyRelationDTO> relations;

    private List<OntologyFunctionDTO> functions;

    private List<OntologyActionDTO> actions;
}
