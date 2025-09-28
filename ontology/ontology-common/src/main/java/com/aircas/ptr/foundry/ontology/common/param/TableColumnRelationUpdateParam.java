package com.aircas.ptr.foundry.ontology.common.param;


import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "修改表关联健")
public class TableColumnRelationUpdateParam {

    private String entityTable;

    private String entityTableKey;

    private String entityPropertyTable;

    private String entityPropertyTableKey;

}
