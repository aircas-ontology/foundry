package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实体节点和关系删除参数")
public class EntityNodeAndRelationsDeleteParam {

    @Schema(name = "ontologyUniqueIdentifier", description = "本体id", example = "123")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;

    @Schema(name = "entityPrimaryKey", description = "实体主键值", example = "1")
    private Object entityPrimaryKey;
}
