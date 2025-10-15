package com.aircas.ptr.foundry.ontology.common.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
public class EntityRelationVO {


    private String type;

    private Object nodePrimaryKeyFrom;

    private Object nodePrimaryKeyTo;

    private String nodeNameFrom;

    private String nodeNameTo;
}
