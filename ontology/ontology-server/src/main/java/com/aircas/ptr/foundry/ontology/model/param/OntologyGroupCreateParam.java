package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@ApiModel(description = "创建本体分组")
public class OntologyGroupCreateParam {

    @ApiModelProperty(name = "groupName", value = "分组名称", required = true, example = "远海远域")
    @NotBlank(message = "groupName is empty")
    private String groupName;

    @ApiModelProperty(name = "description", value = "分组描述", required = true, example = "用于远海远域场景使用")
    @NotBlank(message = "description is empty")
    private String description;

    @ApiModelProperty(name = "icon", value = "分组图标url", required = false)
    private String iconUrl;
}
