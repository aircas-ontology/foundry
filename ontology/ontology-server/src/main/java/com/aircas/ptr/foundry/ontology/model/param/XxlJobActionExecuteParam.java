package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "本体行为执行请求")
public class XxlJobActionExecuteParam extends OntologyActionExecuteParam {


    @Schema(name = "scheduleId", description = "行为id", example = "123")
    private Long scheduleId;


}
