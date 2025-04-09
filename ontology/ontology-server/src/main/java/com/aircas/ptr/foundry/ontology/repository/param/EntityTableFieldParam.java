package com.aircas.ptr.foundry.ontology.repository.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @className: EntityTableFeildParam
 * @author: yangj
 * @date: 2025/4/8 18:32
 * @version: 1.0
 * @description: 表的字段参数
 */
@ApiModel(description = "表的字段参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntityTableFieldParam {

    @ApiModelProperty(value = "字段注释", example = "This is a sample field comment")
    private String fieldComment;

    @ApiModelProperty(value = "字段名称", example = "sample_field")
    private String fieldName;

    @ApiModelProperty(value = "字段类型", example = "String")
    private String fieldType;

    @ApiModelProperty(value = "是否可为空", example = "false")
    private boolean nullable;

    @ApiModelProperty(value = "是否为主键", example = "false")
    private boolean isPrimaryKey;
}
