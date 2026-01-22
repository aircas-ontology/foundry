package com.aircas.ptr.foundry.ontology.model.param;


import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@ApiModel(description = "函数执行结果回调请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class FunctionCallbackParam {

    @ApiModelProperty(name = "taskId", value = "任务id")
    @NotBlank(message = "taskId is empty")
    private String taskId;


    @ApiModelProperty(name = "result", value = "函数执行结果")
    @NotNull(message = "result is null")
    private JsonNode result;

}
