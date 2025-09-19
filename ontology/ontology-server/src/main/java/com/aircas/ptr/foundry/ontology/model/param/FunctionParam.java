package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "函数请求")
public class FunctionParam extends OntologyIdentifierParam{

    @ApiModelProperty(value = "函数api", required = true, example = "getDesc")
    @NotBlank(message = "functionName is empty")
    private String functionName;

    @ApiModelProperty(value = "函数描述", example = "这是一个函数")
    @NotBlank(message = "description is empty")
    private String description;

    @ApiModelProperty(value = "函数涉及的本体", example = "xtmb")
    @NotEmpty(message = "objectTypes is empty")
    private List<String> objectTypes;

    @ApiModelProperty(value = "函数涉及的本体", example = "xtmb")
    @NotBlank(message = "code is empty")
    private String code;
}
