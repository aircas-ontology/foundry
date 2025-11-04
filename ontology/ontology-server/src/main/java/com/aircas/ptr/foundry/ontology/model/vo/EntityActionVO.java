package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "实体行为")
public class EntityActionVO {

    @ApiModelProperty(name = "actionApi", value = "行为api name", example = "getInfo")
    private String actionApi;

    @ApiModelProperty(name = "functionApi", value = "函数api name", example = "getInfo")
    private String functionApi;

    @ApiModelProperty(name = "description", value = "行为描述", example = "这是一个行为")
    private String description;

    @ApiModelProperty(name = "displayName", value = "行为显示名称", example = "轨迹")
    private String displayName;

}
