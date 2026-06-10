package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.common.constant.PostgresDataTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 数据源列DTO
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class EntityDatasourceColumnDTO {

    /**
     * 该列是否为数据源表的主键
     */
    private Boolean isPrimaryKey;

    /**
     * 是否为与主数据源表关联的列名
     */
    private Boolean isAssociateKey;


    /**
     * 关联的主数据源表的列名，存在于主数据源表的列中
     */
    private String associateColumnName;

    /**
     * 列名称
     */
    private String columnName;

    /**
     * 列数据类型
     */
    private PostgresDataTypeEnum datasourceColumnType;

    /**
     * 列描述
     */
    private String description;


}
