package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "行为调度编辑请求")
public class ActionSchedulingUpdateParam extends ActionSchedulingCreateParam {

    @ApiModelProperty(name = "id", value = "行为调度id", required = true, example = "123")
    @NotNull(message = "id is null")
    private Long id;

}
