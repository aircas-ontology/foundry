package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "行为参数VO")
public class ActionMappingInVO {

    @ApiModelProperty(value = "参数名称", required = true, example = "mbbh")
    private String parameterName;

    @ApiModelProperty(value = "参数类型", required = true, example = "mbbh")
    private String parameterType;

    @ApiModelProperty(value = "参数对应的本体属性ID", required = true, example = "fdsfa-gfdsagf-dfs")
    private String propertyUniqueIdentifier;

    @ApiModelProperty(value = "参数对应的本体属性名称", required = true, example = "名字")
    private String propertyName;

    @ApiModelProperty(value = "参数对应的本体名称", required = true, example = "测试")
    private String ontologyName;

    @ApiModelProperty(value = "参数对应的本体id", required = true, example = "fdsfa-gfdsagf-dfs")
    private String ontologyUniqueIdentifier;
}
