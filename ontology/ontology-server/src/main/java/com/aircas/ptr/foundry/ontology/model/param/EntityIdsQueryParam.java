package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "根据实体id查询")
public class EntityIdsQueryParam {

    @Schema(name = "ontologyUniqueIdentifier", description = "本体id", example = "123")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;

    @Schema(name = "entityPrimaryKeys", description = "实体主键值列表", example = "[123,111]")
    private List<Object> entityPrimaryKeys;

}
