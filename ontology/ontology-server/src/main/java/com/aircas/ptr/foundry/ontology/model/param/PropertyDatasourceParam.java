package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(description = "属性数据源请求")
public class PropertyDatasourceParam {

    @ApiModelProperty(name = "datasourceId", value = "数据源表名", required = true, example = "xtmb")
    private String datasourceId;

    @ApiModelProperty(name = "datasourceColumnName", value = "数据源列名", required = true, example = "id")
    private String datasourceColumnName;
}
