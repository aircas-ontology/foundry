package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ApiModel(description = "本体属性可见性VO")
public class OntologyPropertyVisibilityVO {


    @ApiModelProperty(name = "propertyApiName", value = "属性apiName", required = true, example = "id")
    private String propertyApiName;

    @ApiModelProperty(name = "propertyDisplayName", value = "属性display name", required = true, example = "id")
    private String propertyDisplayName;

    @ApiModelProperty(name = "visibility", value = "可见性 0 不可见 1 可见", required = true, example = "1")
    private Integer visibility;
}
