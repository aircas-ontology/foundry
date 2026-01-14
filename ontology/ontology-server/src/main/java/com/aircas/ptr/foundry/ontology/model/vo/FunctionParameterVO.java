package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamTypeEnum;
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
@ApiModel(value = "函数信息")
public class FunctionParameterVO {

    @ApiModelProperty(name = "paramId",value = "参数id")
    private Long paramId;

    @ApiModelProperty(name = "paramName",value = "参数名称")
    private String paramName;

    @ApiModelProperty(name = "paramType",value = "参数类型")
    private FunctionParamTypeEnum paramType;

    @ApiModelProperty(name = "category",value = "参数输入输出类别")
    private FunctionParamCategoryEnum category;

    @ApiModelProperty(name = "paramOrder",value = "参数顺序")
    private Integer paramOrder;

    @ApiModelProperty(name = "paramOrder",value = "参数schema")
    private String paramSchema;

    @ApiModelProperty(name = "description",value = "参数描述")
    private String description;
}
