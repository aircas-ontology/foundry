package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "实体属性赋值参数")
public class EntityPropertyValueParam {

    @NotBlank(message = "propertyApiName is empty")
    @Schema(name = "propertyApiName", description = "属性apiName", example = "name", required = true)
    private String propertyApiName;


    @NotNull(message = "propertyValue is null")
    @Schema(name = "propertyValue", description = "属性值", example = "name", required = true)
    private Object propertyValue;


}
