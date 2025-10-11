package com.aircas.ptr.foundry.ontology.common.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class EntityDetailQueryParam {

    @NotBlank(message = "primaryTableName is empty")
    private String primaryTableName;

    @NotNull(message = "primaryKeyValue is null")
    private Object primaryKeyValue;

    private List<EntityAssociateDatasourceParam> associateDatasource;
}
