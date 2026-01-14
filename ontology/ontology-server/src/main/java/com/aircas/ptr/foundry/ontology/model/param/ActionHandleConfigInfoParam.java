package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
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
@ApiModel(description = "行为执行配置参数")
public class ActionHandleConfigInfoParam extends OntologyIdentifierParam {

    @ApiModelProperty(name = "actionApi", value = "行为api", required = true, example = "shipLocation")
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @ApiModelProperty(name = "name", value = "行为调度name", required = true, example = "name")
    @NotBlank(message = "name is empty")
    private String name;

    @ApiModelProperty(name = "description", value = "行为调度描述", required = true, example = "description")
    private String description;

    @ApiModelProperty(name = "type", value = "行为调度类型", required = true, example = "RULE")
    @NotNull(message = "type is empty")
    private ActionSchedulingTypeEnum type;

}
