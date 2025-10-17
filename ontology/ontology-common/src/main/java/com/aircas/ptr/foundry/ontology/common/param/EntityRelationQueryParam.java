package com.aircas.ptr.foundry.ontology.common.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Accessors(chain = true)
public class EntityRelationQueryParam {

    private String tableName;

    private Object primaryKeyValue;
}
