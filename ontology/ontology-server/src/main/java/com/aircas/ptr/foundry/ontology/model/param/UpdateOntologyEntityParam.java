package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateOntologyEntityParam {

    @ApiModelProperty(value = "本体编号", example = "xxxx")
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(value = "更新的属性键值", example = "{\"mbmc\":\"xxx\",\"jcgw\":123}")
    private Map<String,Object> updateData;


    @ApiModelProperty(value = "更新的条件", example = "{\"mbbh\":\"xxx\"}")
    private Map<String,Object> updateWhere;
}
