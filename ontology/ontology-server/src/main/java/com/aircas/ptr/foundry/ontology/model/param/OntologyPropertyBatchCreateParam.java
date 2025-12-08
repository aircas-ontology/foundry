package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "本体属性批量创建请求")
public class OntologyPropertyBatchCreateParam {

    @ApiModelProperty(name = "properties", value = "本体批量属性", required = true)
    @NotEmpty(message = "properties is empty")
    private List<OntologyPropertyCreateParam> properties;
}
