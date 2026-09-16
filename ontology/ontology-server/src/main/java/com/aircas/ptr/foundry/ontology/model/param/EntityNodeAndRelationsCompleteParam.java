package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实体节点和关系补全参数")
public class EntityNodeAndRelationsCompleteParam {


    private String ontologyUniqueIdentifier;


    private Map<String,Object> entityPropertyMap;

}
