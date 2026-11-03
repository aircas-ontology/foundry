package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 实例节点导出结构。
 *
 * <p>properties 存放主表属性（标量键值对）；
 * relatedTables 按存储分组名存放关联表数据，每组为行对象列表，每行是 apiName → 值 的 map。</p>
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
     * 主表属性键值对，键为属性 apiName
     */
    private Map<String, Object> properties;

    /**
     * 关联表数据：storageGroup → 行对象列表。
     * 每行为一个 Map（apiName → 值），表示子表的一行完整记录。
     */
    private Map<String, List<Map<String, Object>>> relatedTables;
}
