package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@ApiModel(description = "本体空间创建请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySpaceCreateParam {

    @ApiModelProperty(name = "icon", value = "空间图标url")
    private String iconUrl;

    @ApiModelProperty(name = "displayName", value = "空间名称", example = "xxx战场", required = true)
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @ApiModelProperty(name = "description", value = "空间描述", example = "这是空间描述")
    private String description;

    @ApiModelProperty(name = "apiName", value = "空间api名称", example = "space_a", required = true)
    @NotBlank(message = "apiName is empty")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "空间api名称格式不合法")
    private String apiName;
}
