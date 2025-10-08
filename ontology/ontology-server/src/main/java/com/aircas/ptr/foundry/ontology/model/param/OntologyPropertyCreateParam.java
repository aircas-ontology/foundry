package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "属性创建请求")
public class OntologyPropertyCreateParam extends OntologyIdentifierParam {

    @ApiModelProperty(name = "columnParamList", value = "属性列表", required = true)
    @NotEmpty(message = "columnParamList is empty")
    @Valid
    private List<OntologyDataSourceColumnParam> columnParamList;


}
