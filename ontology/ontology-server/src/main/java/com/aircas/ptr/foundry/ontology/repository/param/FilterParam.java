package com.aircas.ptr.foundry.ontology.repository.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "查询条件参数")
public class FilterParam {

    @ApiModelProperty(value = "查询键", required = true, example = "mbbh")
    private String filterKey;

    @ApiModelProperty(value = "查询值", required = true, example = "7")
    private String filterValue;
}
