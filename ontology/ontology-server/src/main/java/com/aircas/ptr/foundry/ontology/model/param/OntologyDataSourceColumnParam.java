package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.PostgresDataTypeEnum;
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
public class OntologyDataSourceColumnParam {

    @ApiModelProperty(value = "列名", required = true, example = "id")
    private String columnName;

    @ApiModelProperty(value = "类型，仅只支持postgresql", required = true, example = "bigint")
    private PostgresDataTypeEnum columnType;

    @ApiModelProperty(value = "描述", required = true, example = "名称")
    private String description;

    @ApiModelProperty(value = "在代码里用的属性名称", required = true, example = "name")
    private String apiName;
}
