package com.aircas.ptr.foundry.ontology.entity.vo;


import io.swagger.annotations.ApiModel;
import lombok.Data;

/**
 * @author Lenovo
 */
@Data
@ApiModel(description = "参数对象")
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
