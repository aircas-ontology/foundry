package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
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
public class OntologySpaceCreateDTO {

    private OntologySpaceDTO ontologySpace;

    private OntologyCategoryCreateParam ontologyCategory;

    private List<OntologyCreateDTO> ontologies;

}
