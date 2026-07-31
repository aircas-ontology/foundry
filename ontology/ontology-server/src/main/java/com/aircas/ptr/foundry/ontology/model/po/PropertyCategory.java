package com.aircas.ptr.foundry.ontology.model.po;


import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("property_category")
public class PropertyCategory {

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 父分类节点，根节点为0
     */
    private Integer parentId;

    /**
     * 分类路径
     */
    private String path;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 本体唯一标识
     */
    private String ontologyUniqueIdentifier;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

}

