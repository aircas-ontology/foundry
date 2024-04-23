package com.aircas.ptr.foundry.model.po;

import lombok.Data;

import java.util.Date;


@Data
public class OntologyFunctionMappingOut {
    /**
     * Column: id
     */
    private Long id;

    /**
     * Column: ontology_function_id
     */
    private Long ontologyFunctionId;

    /**
     * Column: property_unique_identifier
     */
    private String propertyUniqueIdentifier;

    /**
     * Column: create_time
     */
    private Date createTime;

    /**
     * Column: update_time
     */
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}