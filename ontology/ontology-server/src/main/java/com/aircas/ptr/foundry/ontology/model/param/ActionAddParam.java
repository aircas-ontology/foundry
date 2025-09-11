package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author yangj
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "行为新增模型")
public class ActionAddParam {

    @ApiModelProperty(value = "id", example = "1264894134988943")
    private Long id;

    @ApiModelProperty(value = "行为api，用来调用", example = "cal")
    private String api;

    @ApiModelProperty(value = "函数api", example = "satellite")
    private String functionApi;

    @ApiModelProperty(value = "本体id", example = "fdsa-dfsaf-gdsagfd")
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(value = "行为描述", example = "这是一个行为")
    private String description;

    @ApiModelProperty(value = "行为显示名称", example = "调用函数")
    private String displayName;

    @ApiModelProperty(value = "行为参数列表", example = "[{\"parameterName\":\"mbbh\",\"propertyUniqueIdentifier\":\"545649a4-8bba-4d0c-b265-362e85fd4fe1\"}]")
    private List<ActionMappingInAddParam> mappingIns;
}
