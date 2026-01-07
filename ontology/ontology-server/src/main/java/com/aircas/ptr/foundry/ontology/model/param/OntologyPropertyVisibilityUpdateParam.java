package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "ontology property visibility param")
public class OntologyPropertyVisibilityUpdateParam extends OntologyIdentifierParam {


    @ApiModelProperty(name = "propertyVisibility", value = "属性可见性列表", required = true)
    @NotEmpty(message = "propertyVisibility is empty")
    private List<PropertyVisibility> propertyVisibility;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Accessors(chain = true)
    @ApiModel(description = "property visibility")
    public static class PropertyVisibility {

        @ApiModelProperty(name = "propertyApiName", value = "属性apiName", required = true, example = "id")
        @NotBlank(message = "propertyApiName is empty")
        private String propertyApiName;

        @ApiModelProperty(name = "visibility", value = "可见性 0 不可见 1 可见", required = true, example = "1")
        @NotNull(message = "visibility is null")
        private Integer visibility;

    }

}
