package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "实体查询请求")
public class EntityActionExecuteParam extends EntityQueryParam {


    @ApiModelProperty(name = "actionApi", value = "actionApi", example = "compute")
    @NotBlank(message = "actionApi is null")
    private String actionApi;
}
