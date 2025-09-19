package com.aircas.ptr.foundry.ontology.entity.model.param;

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
@ApiModel(description = "实体创建请求")
public class EntityCreateParam {

    @ApiModelProperty(name = "primaryDataSource", value = "实体表对应主数据源",  required = true)
    private PrimaryDataSourceParam primaryDataSource;

    @ApiModelProperty(name = "associateDataSources", value = "实体表关联的其他数据源")
    private List<AssociateDataSourceParam> associateDataSources;

}
