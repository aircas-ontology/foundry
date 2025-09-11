package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.common.constant.PostgresDataTypeEnum;
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
@ApiModel(description = "本体主数据源")
public class OntologyPrimaryDataSourceParam {

    @ApiModelProperty(value = "表名", required = true, example = "xtmb")
    private String tableName;

    @ApiModelProperty(value = "主键的列名，必须存在于选择的列参数中", required = true, example = "id")
    private String primaryKey;

    @ApiModelProperty(value = "名称健的列名", required = true, example = "name")
    private String titleKey;

    @ApiModelProperty(value = "列参数", required = true)
    private List<OntologyDataSourceColumnParam> columnParamList;

}
