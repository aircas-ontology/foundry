package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ApiModel(description = "根据实体id查询")
public class EntityIdsQueryParam {

    @ApiModelProperty(name = "ontologyUniqueIdentifier", value = "本体id", example = "123")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(name = "entityPrimaryKeys", value = "实体主键值列表", example = "[123,111]")
    private List<Object> entityPrimaryKeys;

}
