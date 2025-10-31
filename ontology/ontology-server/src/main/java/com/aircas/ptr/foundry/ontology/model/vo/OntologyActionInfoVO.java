package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "本体行为信息")
public class OntologyActionInfoVO {


    @ApiModelProperty(name = "ontologyUniqIdentifier", value = "本体id", example = "123456")
    private String ontologyUniqIdentifier;

    @ApiModelProperty(name = "ontologyName", value = "本体name", example = "本体1")
    private String ontologyName;

    @ApiModelProperty(name = "actionApi", value = "api名称", example = "satellite")
    private String actionApi;

    @ApiModelProperty(name = "description", value = "行为描述", example = "这是一个行为")
    private String description;

    @ApiModelProperty(name = "displayName", value = "行为显示名称", example = "调用函数")
    private String displayName;


}
