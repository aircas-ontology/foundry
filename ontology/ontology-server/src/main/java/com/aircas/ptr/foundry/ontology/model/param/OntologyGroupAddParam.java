package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@AllArgsConstructor
@ApiModel(description = "本体分组参数")
public class OntologyGroupAddParam {

    @ApiModelProperty(name = "groupName", value = "分组名称", required = true, example = "远海远域")
    @NotBlank(message = "groupName is empty")
    private String groupName;

    @ApiModelProperty(name = "description", value = "分组描述", required = false, example = "用于远海远域场景使用")
    private String description;
}
