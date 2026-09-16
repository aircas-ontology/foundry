package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Range;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "实体生成请求")
public class EntityGenerateParam {

    @NotBlank(message = "ontologyIdentifier is empty")
    @OntologyIdVerify
    @Schema(name = "ontologyIdentifier", description = "本体id", example = "abcdef", required = true)
    private String ontologyIdentifier;


    @NotNull(message = "count is null")
    @Range(min = 1, max = 100, message = "count must be between 1 and 100")
    @Schema(name = "count", description = "数量", example = "10", required = true)
    private Integer count;

    @Size(min = 1, message = "propertyValues is empty")
    @Schema(name = "propertyValues", description = "实体属性初始值", required = true)
    private List<EntityPropertyValueParam> propertyValues;


}
