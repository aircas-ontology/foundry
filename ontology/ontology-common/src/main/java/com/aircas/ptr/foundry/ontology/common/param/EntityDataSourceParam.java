package com.aircas.ptr.foundry.ontology.common.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "实体数据源")
public class EntityDataSourceParam {

    @ApiModelProperty(name="columnParamList", value = "列参数", required = true)
    @NotEmpty(message = "columnParamList is empty")
    private List<EntityDataSourceColumnParam> columnParamList;

}
