package com.aircas.ptr.foundry.ontology.model.dto;


import com.aircas.ptr.foundry.ontology.model.enums.DatasourceEventTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 实体表结构变更事件DTO
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class EntityDatasourceSchemaChangeEventDTO {

    /**
     * 对象唯一标识
     */
    private String ontologyUniqueIdentifier;


    /**
     * 事件类型
     */
    private DatasourceEventTypeEnum type;



    /**
     * 对象来源的外部库 schema 名，仅「数据源构建对象」链路有值
     */
    private String sourceSchema;


    /**
     * 关联的数据源表
     */
    private List<EntityDatasourceDTO> datasource;

}
