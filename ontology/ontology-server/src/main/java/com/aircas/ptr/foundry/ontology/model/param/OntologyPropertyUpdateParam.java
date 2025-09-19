package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(description = "属性更新请求")
public class OntologyPropertyUpdateParam extends IdentifierParam {


    @ApiModelProperty(name = "primaryDataSourceKey", value = "关联的主数据源表的列名：修改关联健", example = "id")
    private String primaryDataSourceKey;

    /**
     * 属性名称
     */
    @ApiModelProperty(name = "displayName", value = "属性名称", example = "id")
    private String displayName;

    /**
     * 属性描述
     */
    @ApiModelProperty(name = "description", value = "属性描述", example = "id")
    private String description;


}
