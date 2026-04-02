package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "实体行为执行请求")
public class EntityActionExecuteParam extends EntityQueryParam {


    @ApiModelProperty(name = "actionApi", value = "actionApi", example = "compute")
    @NotBlank(message = "actionApi is null")
    private String actionApi;


    @ApiModelProperty(name = "linkedOntologyUniqueIdentifier", value = "关联本体id", example = "123")
    private String linkedOntologyUniqueIdentifier;

    @ApiModelProperty(name = "linkedEntityPrimaryKey", value = "关联本体id下某实体主键值", example = "123")
    private Object linkedEntityPrimaryKey;
}
