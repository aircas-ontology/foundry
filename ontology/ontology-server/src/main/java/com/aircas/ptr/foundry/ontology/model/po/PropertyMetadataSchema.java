package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.MetadataValueTypeEnum;
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
     * 对象唯一标识
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
     * 取值类型：INPUT 输入值 / ENUM 枚举值 / RANGE 范围值
     */
    private MetadataValueTypeEnum valueType;

    /**
     * 取值内容（字符串）：ENUM 为逗号分隔候选项，RANGE 形如 "min,max"，INPUT 可空
     */
    private String value;

    /**
     * 元数据名称
     */
    private String name;
}

