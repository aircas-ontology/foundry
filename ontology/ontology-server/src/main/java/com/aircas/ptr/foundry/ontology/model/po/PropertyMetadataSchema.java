package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("property_metadata_schema")
public class PropertyMetadataSchema {

    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 本体唯一标识
     */
    private String ontologyUniqueIdentifier;

    /**
     * 元数据完整path，/分割符
     */
    private String path;

    /**
     * 父元数据id，根节点为0
     */
    private Integer parentId;


    /**
     * 为叶子结点时的枚举值列表，/分割符
     */
    private String enumValues;

    /**
     * 元数据名称
     */
    private String name;
}

