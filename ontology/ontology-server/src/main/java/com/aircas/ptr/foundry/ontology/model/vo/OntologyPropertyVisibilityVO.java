package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "本体属性可见性VO")
public class OntologyPropertyVisibilityVO {


    @Schema(name = "propertyApiName", description = "属性apiName", required = true, example = "id")
    private String propertyApiName;

    @Schema(name = "propertyDisplayName", description = "属性display name", required = true, example = "id")
    private String propertyDisplayName;

    @Schema(name = "visibility", description = "可见性 0 不可见 1 可见", required = true, example = "1")
    private Integer visibility;
}
