package com.aircas.ptr.foundry.ontology.common.param;


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
@ApiModel(description = "实体属性新增请求")
public class EntityColumnCreateParam {

    @ApiModelProperty(name = "primaryTableName", value = "主数据表名称")
    private String primaryTableName;

    @ApiModelProperty(name = "existDatasourceColumns", value = "已存在的表添加的新列")
    private List<EntityDataSourceColumnParam> existDatasourceColumns;

    @ApiModelProperty(name = "newDatasource", value = "新的数据源")
    private List<EntityDataSourceParam> newDatasource;

}
