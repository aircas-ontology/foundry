package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * ontology_meta
 *
 * @author
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@TableName("ontology_meta")
public class OntologyMeta implements Serializable {
    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 唯一标识
     */
    private String uniqueIdentifier;

    /**
     * 软删除状态位，1有效，0无效
     */
    private Integer status;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 记录修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;


    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date latestQueryTime;

    /**
     * 图标
     */
    private String icon;

    /**
     * 本体名称
     */
    private String displayName;


    /**
     * 本体描述
     */
    private String description;

    /**
     * 在代码里用的本体名称
     */
    private String apiName;


    private static final long serialVersionUID = 1L;

    /**
     * 分组ids，以“,”隔开
     */
    private String metaGroupId;

    /**
     * 父本体的唯一标识符
     */
    private String parentUniqueIdentifier;

    /**
     * 能否主动生成实体对象
     */
    private Boolean canGenerateEntity;

    /**
     * 本体空间id
     */
    private Integer ontologySpaceId;

    /**
     * 本体分类id
     */
    private Integer ontologyCategoryId;

}