package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(description = "查询实体关系请求")
public class EntityLinkQueryParam extends OntologyIdentifierParam {

    @ApiModelProperty(name = "entityId", value = "实体id", dataType = "java.lang.String", example = "abcdef")
    private String entityId;
}
