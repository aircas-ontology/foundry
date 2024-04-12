package com.aircas.ptr.foundry.ontology.entity.vo;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class ParameterMetadataVO {

    private String name;
    private String type;
    private String description;

    public ParameterMetadataVO(String name, String type, String description) {
        this.name = name;
        this.type = type;
        this.description = description;
    }
}
