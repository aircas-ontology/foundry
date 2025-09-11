package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.common.constant.OntologyPropertyTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "本体关联的其他数据源")
public class OntologyAssociateDataSourceParam {

    @ApiModelProperty(value = "表名", required = true, example = "xtmb")
    private String tableName;

    @ApiModelProperty(value = "tableName中与主数据源关联的列名，必须存在于选择的列参数中", required = true, example = "id")
    private String associateKey;

    @ApiModelProperty(value = "关联的主数据源表的列名，必须存在于主数据源表的列中", required = true, example = "id")
    private String primaryDataSourceKey;

    @ApiModelProperty(value = "数据源作为属性时的类别：静态属性static、动态属性dynamic", required = true, example = "static")
    private OntologyPropertyTypeEnum propertyType;

    @ApiModelProperty(value = "列参数", required = true)
    private List<OntologyDataSourceColumnParam> columnParamList;
}
