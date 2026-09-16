package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "ontology property visibility param")
public class OntologyPropertyVisibilityUpdateParam extends OntologyIdentifierParam {


    @Schema(name = "propertyVisibility", description = "属性可见性列表", required = true)
    @NotEmpty(message = "propertyVisibility is empty")
    private List<PropertyVisibility> propertyVisibility;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Accessors(chain = true)
    @Schema(description = "property visibility")
    public static class PropertyVisibility {

        @Schema(name = "propertyApiName", description = "属性apiName", required = true, example = "id")
        @NotBlank(message = "propertyApiName is empty")
        private String propertyApiName;

        @Schema(name = "visibility", description = "可见性 0 不可见 1 可见", required = true, example = "1")
        @NotNull(message = "visibility is null")
        private Integer visibility;

    }

}
