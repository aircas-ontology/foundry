package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(description = "本体关联的其他数据源")
public class OntologyAssociateDataSourceParam {

    @ApiModelProperty(value = "列参数", required = true)
    private List<OntologyDataSourceColumnParam> columnParamList;
}
