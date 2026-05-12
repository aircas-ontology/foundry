package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
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
@ApiModel(description = "实体节点和关系补全参数")
public class EntityNodeAndRelationsCompleteParam {


    private String ontologyUniqueIdentifier;


    private Map<String,Object> entityPropertyMap;

}
