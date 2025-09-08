package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "数据源视图")
public class DatasourceTableVO {

    @ApiModelProperty(value = "数据源标识", example = "xtmb")
    private String tableName;

    @ApiModelProperty(value = "数据源描述", example = "系统目标")
    private String description;
}
