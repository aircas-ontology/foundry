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

    @ApiModelProperty(name = "columnName",value = "列名", required = true, example = "id")
    private String columnName;

    @ApiModelProperty(name="columnType", value = "列类型，仅只支持postgresql", required = true, example = "bigint")
    private PostgresDataTypeEnum columnType;

    @ApiModelProperty(name="description", value = "列描述", required = true, example = "名称")
    private String description;

    @ApiModelProperty(name = "displayName", value = "属性展示名称", dataType = "java.lang.String", example = "飞机", required = true)
    private String displayName;

    @ApiModelProperty(name="apiName",value = "在代码里用的属性名称", required = true, example = "name")
    private String apiName;
}
