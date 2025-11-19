package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.param.ActionLinkMappingParam;
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

    @ApiModelProperty(value = "行为关系映射")
    private ActionLinkMappingParam linkMapping;

    @ApiModelProperty(name = "mappingIns", value = "行为与函数参数映射")
    private List<ActionParamMappingVO> mappingIns;

}
