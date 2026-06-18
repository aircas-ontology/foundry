package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyPropertyPrimaryCategoryEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * ontology_property
 *
 * @author
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("ontology_property")
public class OntologyProperty implements Serializable {
    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;


    /**
     * uniq id
     */
    private String uniqueIdentifier;

    /**
     * 软删除标志位，1有效 0无效
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
     * 本体id
     */
    private String ontologyUniqueIdentifier;

    /**
     * 数据源的字段名称，即映射
     */
    private String datasourceColumnName;

    /**
     * 属性基础类型、时间、字符、数值
     */
    private OntologyDataTypeEnum propertyType;

    /**
     * 属性名称
     */
    private String displayName;

    /**
     * 属性描述
     */
    private String description;


    /**
     * 在代码里用的属性名称、驼峰式
     */
    private String apiName;

    /**
     * 是否为主键，1是0否
     */
    private Integer isPrimaryKey;

    /**
     * 是否为名称键，将该属性作为本体的显示名称，1是0否
     */
    private Integer isTitleKey;


    /**
     * 数据源ID
     */
    private String datasourceId;

    /**
     * 属性标签
     */
    private String tag;

    /**
     * 属性一级分类
     */
    private OntologyPropertyPrimaryCategoryEnum primaryCategory;


    /**
     * 属性二级分类
     */
    private String secondaryCategory;


    /**
     * 属性在实体中可见性
     */
    private Integer visibility;

    /**
     * 属性默认值
     */
    private String defaultValue;

    /**
     * 属性存储分组
     */
    private String storageGroup;

    /**
     * schema
     */
    private String datasourceSchema;

    /**
     * 数据库
     */
    private String datasourceDb;


    private static final long serialVersionUID = 1L;

}