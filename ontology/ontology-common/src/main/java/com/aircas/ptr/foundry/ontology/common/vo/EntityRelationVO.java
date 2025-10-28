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

    private String nodeTableNameFrom;

    private String nodeTableNameTo;

    private Object nodePrimaryKeyFrom;

    private Object nodePrimaryKeyTo;

    private String nodeIdFrom;

    private String nodeIdTo;

    private String nodeNameFrom;

    private String nodeNameTo;
}
