package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "ontology update request param")
public class OntologyUpdateParam extends OntologyIdentifierParam {

    /**
     * 图标
     */
    @ApiModelProperty(name = "icon", value = "本体图标", dataType = "java.lang.String", example = "飞机图标base64")
    @NotBlank(message = "icon is empty")
    private String icon;

    /**
     * 本体名称
     */
    @ApiModelProperty(name = "displayName", value = "本体名称", dataType = "java.lang.String", example = "飞机")
    @NotBlank(message = "displayName is empty")
    private String displayName;


    /**
     * 本体描述
     */
    @ApiModelProperty(name = "description", value = "本体描述", dataType = "java.lang.String", example = "这是一架我方战斗机")
    private String description;

    /**
     * 本体描述
     */
    @ApiModelProperty(name = "groupIds", value = "分组ids", example = "[123,456]")
    @NotEmpty(message = "groupIds is empty")
    private List<String> groupIds;

}

