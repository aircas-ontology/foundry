package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "函数执行结果回调请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class FunctionCallbackParam {


    @Schema(name = "result", description = "函数执行结果")
    @NotNull(message = "result is null")
    private FunctionResultVO result;

}
