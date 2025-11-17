package com.aircas.ptr.foundry.ontology.model.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@Accessors(chain = true)
@ApiModel(value = "函数详细信息VO")
public class FunctionDetailVO extends FunctionInfoVO {


    @ApiModelProperty(name = "params", value = "函数参数")
    private List<FunctionParameterVO> params;

    @ApiModelProperty(name = "referenceName", value = "函数全限定名称")
    private String referenceName;

    @ApiModelProperty(name = "code", value = "函数代码：只有自定义函数会存在")
    private String code;

}
