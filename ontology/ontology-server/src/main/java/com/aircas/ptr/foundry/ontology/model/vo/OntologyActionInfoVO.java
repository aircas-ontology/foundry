package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "本体行为信息")
public class OntologyActionInfoVO {

    @ApiModelProperty(name = "actionId", value = "行为id", example = "123456")
    private String actionId;

    @ApiModelProperty(name = "ontologyUniqIdentifier", value = "本体id", example = "123456")
    private String ontologyUniqIdentifier;

    @ApiModelProperty(name = "apiName", value = "api名称", example = "satellite")
    private String apiName;

    @ApiModelProperty(name = "functionId", value = "本体下函数id", example = "1")
    private String functionId;

    @ApiModelProperty(name = "functionApi", value = "本体下函数api", example = "getInfo")
    private String functionApi;

    @ApiModelProperty(name = "description", value = "行为描述", example = "这是一个行为")
    private String description;

    @ApiModelProperty(name = "displayName", value = "行为显示名称", example = "调用函数")
    private String displayName;

    @ApiModelProperty(name = "ontologyLink", value = "行为关联的关系id")
    private OntologyLinkInfoVO ontologyLink;

    @ApiModelProperty(name = "mappingIns", value = "行为参数列表", example = "[{\"parameterName\":\"mbbh\",\"propertyUniqueIdentifier\":\"545649a4-8bba-4d0c-b265-362e85fd4fe1\"}]")
    private List<ActionMappingInVO> mappingIns;
}
