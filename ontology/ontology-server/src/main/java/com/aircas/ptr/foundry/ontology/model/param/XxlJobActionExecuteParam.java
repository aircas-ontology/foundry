package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "本体行为执行请求")
public class XxlJobActionExecuteParam extends OntologyActionExecuteParam {


    @ApiModelProperty(name = "scheduleId", value = "行为id", example = "123")
    private Long scheduleId;


}
