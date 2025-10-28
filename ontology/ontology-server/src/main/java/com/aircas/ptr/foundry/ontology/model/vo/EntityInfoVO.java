package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(value = "实体信息数据")
public class EntityInfoVO {

    @ApiModelProperty(name = "primaryKey",value = "实体主键id")
    private Object primaryKey;

    @ApiModelProperty(name = "displayName",value = "实体显示名称")
    private String displayName;

    @ApiModelProperty(name = "properties",value = "实体属性值")
    private List<EntityPropertyVO> properties;
}
