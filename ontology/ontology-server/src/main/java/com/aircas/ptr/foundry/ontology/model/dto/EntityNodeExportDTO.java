package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 实例节点导出结构。
 *
 * <p>properties 的键为属性 apiName，值为该实体在外部数据源中的实际取值。</p>
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class EntityNodeExportDTO {

    /**
     * 实体主键值
     */
    private Object primaryKey;

    /**
     * 实体显示名称
     */
    private String displayName;

    /**
     * 属性键值对，键为属性 apiName
     */
    private java.util.Map<String, Object> properties;
}
