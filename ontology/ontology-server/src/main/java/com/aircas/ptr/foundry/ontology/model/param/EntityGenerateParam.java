package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "实体生成请求")
public class EntityGenerateParam {

    @NotBlank(message = "ontologyIdentifier is empty")
    @OntologyIdVerify
    @ApiModelProperty(name = "ontologyIdentifier", value = "本体id", dataType = "java.lang.String", example = "abcdef", required = true)
    private String ontologyIdentifier;


    @NotNull(message = "count is null")
    @Range(min = 1, max = 100, message = "count must be between 1 and 100")
    @ApiModelProperty(name = "count", value = "数量", example = "10", required = true)
    private Integer count;

    @Size(min = 1, message = "propertyValues is empty")
    @ApiModelProperty(name = "propertyValues", value = "实体属性初始值", required = true)
    private List<EntityPropertyValueParam> propertyValues;


}
