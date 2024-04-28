package com.aircas.ptr.foundry.model.po;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * ontology_property
 * @author 
 */
@Data
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
    private Date createTime;

    /**
     * 记录修改时间
     */
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
    private OntologyDataType propertyType;

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
     * 数据源ID
     */
    private String datasourceId;

    private static final long serialVersionUID = 1L;
}