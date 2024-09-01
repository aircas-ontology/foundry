package com.aircas.ptr.foundry.ontology.entity.vo;


import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Lenovo
 */
@Data
public class ParameterMetadataVO {

    private String name;
    private String type;
    private String description;
    private String api;

    public ParameterMetadataVO(String name, String type, String description, String api) {
        this(name, type, description);
        this.api = api;
    }

    public ParameterMetadataVO(String name, String type, String description) {
        this.name = name;
        this.type = type;
        this.description = description;
    }
}
