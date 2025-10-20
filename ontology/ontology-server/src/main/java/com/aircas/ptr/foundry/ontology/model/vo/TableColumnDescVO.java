package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "表描述信息VO")
public class TableColumnDescVO {

    @ApiModelProperty(name = "columnName",value = "列名")
    private String columnName;

    @ApiModelProperty(name = "description",value = "列描述信息")
    private String description;

    @ApiModelProperty(name = "type",value = "列数据类型")
    private String type;

    @ApiModelProperty(name = "isPrimaryKey",value = "是否为主键")
    private Boolean isPrimaryKey;
}
