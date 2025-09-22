package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @author yangj
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "新增行为请求")
public class ActionCreateParam extends OntologyIdentifierParam {

    @ApiModelProperty(value = "api名称", example = "satellite")
    @NotBlank(message = "apiName is empty")
    private String apiName;

    @ApiModelProperty(value = "本体下函数api", example = "satellite")
    @NotBlank(message = "functionId is empty")
    private String functionId;

    @ApiModelProperty(value = "行为描述", example = "这是一个行为")
    @NotBlank(message = "description is empty")
    private String description;

    @ApiModelProperty(value = "行为显示名称", example = "调用函数")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @ApiModelProperty(value = "行为关联的关系id",required = false)
    private String ontologyLinkId;

    @ApiModelProperty(value = "行为参数列表", example = "[{\"parameterName\":\"mbbh\",\"propertyUniqueIdentifier\":\"545649a4-8bba-4d0c-b265-362e85fd4fe1\"}]")
    private List<ActionMappingInAddParam> mappingIns;
}
