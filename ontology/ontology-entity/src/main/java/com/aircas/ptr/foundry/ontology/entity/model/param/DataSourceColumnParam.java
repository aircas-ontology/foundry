package com.aircas.ptr.foundry.ontology.entity.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(description = "ontology datasource column param")
public class DataSourceColumnParam {

    @ApiModelProperty(name = "columnName", value = "列名", required = true, example = "id")
    private String columnName;

    @ApiModelProperty(name = "columnType", value = "列类型", required = true, example = "bigint")
    private String columnType;

    @ApiModelProperty(name = "description", value = "列描述", required = true, example = "名称")
    private String description;
}
