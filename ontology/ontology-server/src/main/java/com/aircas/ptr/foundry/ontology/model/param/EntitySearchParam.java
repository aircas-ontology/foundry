package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "实体查询请求")
public class EntitySearchParam {

    @Schema(name = "ontologyUniqueIdentifier", description = "本体id", example = "123")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;

    private String propertyName;

    private Object propertyValue;

    @Schema(name = "pageNum")
    private Integer pageNum = 1;

    @Schema(name = "pageSize")
    private Integer pageSize = 10;


}
