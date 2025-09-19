package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@Accessors(chain = true)
@ApiModel(value = "函数信息")
public class FunctionInfoVO {


    @ApiModelProperty(name = "functionId",value = "函数id")
    private String functionId;

    @ApiModelProperty(name = "api",value = "函数名称")
    private String functionName;

    @ApiModelProperty(name = "description",value = "描述")
    private String description;

    @ApiModelProperty(name = "objectTypes",value = "本体列表id")
    private List<String> objectTypes;

    @ApiModelProperty(name = "code",value = "函数代码")
    private String code;

}
