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
public class OntologyActionDTO {


    private String actionApi;

    private String description;

    private String displayName;

    private String functionApi;

    private Integer relationIndex;

    private List<ActionMappingIn> mappingIns;


    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Accessors(chain = true)
    public static class ActionMappingIn {

        private String functionParamName;

        private String ontologyName;

        private String propertyName;

    }

}
