package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "行为调度编辑请求")
public class ActionSchedulingUpdateParam {

    @ApiModelProperty(name = "id", value = "行为调度id", required = true, example = "123")
    @NotNull(message = "id is null")
    private Long id;

    @ApiModelProperty(name = "actionApi", value = "行为api", required = true, example = "shipLocation")
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @ApiModelProperty(name = "name", value = "行为调度name", required = true, example = "name")
    @NotBlank(message = "name is empty")
    private String name;

    @ApiModelProperty(name = "description", value = "行为调度描述", required = true, example = "description")
    private String description;

    @ApiModelProperty(name = "task", value = "编辑定时行为调度")
    @Valid
    private ActionHandleTaskParam task;

    @ApiModelProperty(name = "rule", value = "编辑规则行为调度")
    @Valid
    private ActionHandleRuleParam rule;
}
