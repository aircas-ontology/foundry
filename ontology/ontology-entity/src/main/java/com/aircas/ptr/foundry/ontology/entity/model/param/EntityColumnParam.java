package com.aircas.ptr.foundry.ontology.entity.model.param;


import com.aircas.ptr.foundry.common.constant.PostgresDataTypeEnum;
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
@ApiModel(description = "实体记录创建请求")
public class EntityColumnParam {


    @ApiModelProperty(name = "columnName", value = "列名", required = true, example = "id")
    private String columnName;

    @ApiModelProperty(name = "columnValue", value = "列值", required = true, example = "1")
    private Object columnValue;

    @ApiModelProperty(name = "isPrimaryKey", value = "是否为主键", required = true, example = "true")
    private Boolean isPrimaryKey;

}
