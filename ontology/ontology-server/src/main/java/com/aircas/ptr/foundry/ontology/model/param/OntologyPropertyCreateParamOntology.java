package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "属性创建请求")
public class OntologyPropertyCreateParamOntology extends OntologyIdentifierParam {

    @ApiModelProperty(name = "existDataSources", value = "已存在的数据源添加列")
    private List<OntologyDataSourceColumnParam> existDataSources;


    @ApiModelProperty(name = "newDataSources", value = "新的其他数据源")
    private List<OntologyAssociateDataSourceParam> newDataSources;

}
