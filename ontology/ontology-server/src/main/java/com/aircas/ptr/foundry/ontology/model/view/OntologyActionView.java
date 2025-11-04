package com.aircas.ptr.foundry.ontology.model.view;

import com.aircas.ptr.foundry.ontology.model.po.OntologyActionMappingIn;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class OntologyActionView {

    // action info

    private Long actionId;

    private String actionApi;

    /**
     * Column: function_api
     */
    private String functionApi;

    /**
     * Column: ontology_unique_identifier
     */
    private String ontologyUniqueIdentifier;


    private String description;

    /**
     * 名称
     */
    private String displayName;

    private Integer status;

    private Long ontologyLinkGroupId;


    // action mapping
    private List<OntologyActionMappingIn> mappingIn;
}
