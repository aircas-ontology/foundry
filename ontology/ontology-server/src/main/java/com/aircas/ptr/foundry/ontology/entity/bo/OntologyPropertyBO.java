package com.aircas.ptr.foundry.ontology.entity.bo;

import lombok.Data;

import java.util.Date;

@Data
public class OntologyPropertyBO {
    /**
     * 主键自增
     */
    private Long id;

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
    private Long ontologyId;

    /**
     * 数据源的字段名称，即映射
     */
    private String datasourceColumnName;

    /**
     * 属性基础类型、时间、字符、数值
     */
    private String propertyType;

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
    private Integer visiblity;

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
    private Long datasourceId;
}
