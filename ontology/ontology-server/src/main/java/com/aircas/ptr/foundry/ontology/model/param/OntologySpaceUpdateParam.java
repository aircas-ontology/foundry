package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@ApiModel(description = "本体空间修改请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySpaceUpdateParam {

    @ApiModelProperty(name = "icon", value = "空间图标url")
    private String iconUrl;

    @ApiModelProperty(name = "displayName", value = "空间名称", example = "xxx战场", required = true)
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @ApiModelProperty(name = "description", value = "空间描述", example = "这是空间描述")
    private String description;

    @ApiModelProperty(name = "spaceId", value = "空间id", example = "1", required = true)
    @NotNull(message = "spaceId is null")
    private Integer spaceId;
}
