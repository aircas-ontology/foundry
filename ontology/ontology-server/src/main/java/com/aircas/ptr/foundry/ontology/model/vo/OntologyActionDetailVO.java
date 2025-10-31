package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "本体行为详情")
public class OntologyActionDetailVO extends OntologyActionInfoVO {


    @ApiModelProperty(name = "functionApi", value = "本体下函数api", example = "getInfo")
    private String functionApi;

    @ApiModelProperty(name = "ontologyLink", value = "行为关联的关系id")
    private OntologyLinkInfoVO ontologyLink;

    @ApiModelProperty(name = "mappingIns", value = "行为参数列表", example = "[{\"parameterName\":\"mbbh\",\"propertyUniqueIdentifier\":\"545649a4-8bba-4d0c-b265-362e85fd4fe1\"}]")
    private List<ActionMappingInVO> mappingIns;

}
