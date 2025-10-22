package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "属性创建请求")
public class OntologyPropertyCreateParam extends OntologyIdentifierParam {


    @ApiModelProperty(name = "newAssociateDataSources", value = "新的其他数据源数据", dataType = "List<OntologyAssociateDataSourceParam>")
    @Valid
    private List<OntologyDatasourceParam> newAssociateDataSources;

    @ApiModelProperty(name = "existDataSources", value = "已存在数据源的新增属性列表", dataType = "List<OntologyDataSourceColumnParam>")
    @Valid
    private List<OntologyDataSourceColumnParam> existDataSources;

}
