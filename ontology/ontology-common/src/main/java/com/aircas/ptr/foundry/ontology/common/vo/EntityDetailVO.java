package com.aircas.ptr.foundry.ontology.common.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
public class EntityDetailVO {

    private String tableName;

    private String datasourceId;

    private List<String> propertyName;

    private List<List<Object>> propertyValues;
}
