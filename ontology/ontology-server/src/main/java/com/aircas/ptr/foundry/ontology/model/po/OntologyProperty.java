package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
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
    private Long id;


    /**
     * 主键自增
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
     * 可见性、正常、隐藏、突出显示
     */
    private Integer visibility;

    /**
     * 实验状态、激活、测试中、废弃
     */
    private Integer experimentalStatus;

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
     * 是否为其他数据源关联键
     */
    private Integer isAssociateKey;

    /**
     * 关联的主数据源的列名
     */
    private String associateDatasourceColumnName;

    /**
     * 数据源ID
     */
    private String datasourceId;

    private static final long serialVersionUID = 1L;

}