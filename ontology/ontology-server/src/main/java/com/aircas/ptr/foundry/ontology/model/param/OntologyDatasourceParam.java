package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.common.constant.OntologyPropertyCategoryEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.PrimaryKeyVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "OntologyDatasourceParam")
public class OntologyDatasourceParam {

    @ApiModelProperty(name = "category", value = "数据源关联的一组属性的类别，STATIC/DYNAMIC", example = "STATIC")
    @NotNull(message = "category is null")
    private OntologyPropertyCategoryEnum category;

    @ApiModelProperty(name = "type", value = "数据源关联的一组属性的自定义标签", example = "基本属性")
    @NotBlank(message = "tag is blank")
    private String tag;

    @ApiModelProperty(name ="columnParamList",value = "列参数", required = true)
    @PrimaryKeyVerify
    @Valid
    private List<OntologyDataSourceColumnParam> columnParamList;
}
