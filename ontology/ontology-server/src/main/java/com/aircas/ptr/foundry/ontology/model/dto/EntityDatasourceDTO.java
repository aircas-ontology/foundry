package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 数据源表DTO
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class EntityDatasourceDTO {

    /**
     * 表结构 格式：db.schema.table
     */
    private String tableName;

    /**
     * 实体表对应的外部库原始表名（溯源用，仅「数据源构建对象」链路有值）
     */
    private String sourceTableName;


    /**
     * 是否为主属性数据眼表（主属性表有且只有一个，其他关联属性表可以有多个）
     */
    private Boolean isMainDatasource;

    /**
     * 数据源列的详情
     */
    private List<EntityDatasourceColumnDTO> columns;


}
