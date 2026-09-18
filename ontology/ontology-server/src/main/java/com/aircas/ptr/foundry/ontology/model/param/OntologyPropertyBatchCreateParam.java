package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "本体属性批量创建请求")
public class OntologyPropertyBatchCreateParam {

    @Schema(name = "properties", description = "本体批量属性", required = true)
    @NotEmpty(message = "properties is empty")
    private List<OntologyPropertyCreateParam> properties;
}
