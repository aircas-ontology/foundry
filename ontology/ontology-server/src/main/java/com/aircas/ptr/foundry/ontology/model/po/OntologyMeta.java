package com.aircas.ptr.foundry.ontology.model.po;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * ontology_meta
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

    /**
     * 图标
     */
    private String icon;

    /**
     * 本体名称
     */
    private String displayName;


    /**
     * 本体对应的主数据源id，这里的数据源由上层应用指导、治理好的表的访问方式
     * todo 后续删除
     */
    private String backingDatasourceId;

    /**
     * 本体对应的其他非主数据源，逗号分隔
     * todo 后续删除
     */
    private String otherDatasourceId;


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

}