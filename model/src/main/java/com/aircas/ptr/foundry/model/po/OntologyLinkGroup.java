package com.aircas.ptr.foundry.model.po;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;


@Data
public class OntologyLinkGroup implements Serializable {
    /**
     * 主键自增
     */
    private Long id;

    /**
     * link的unique identifier
     */
    private String uniqueIdentifier;

    /**
     * link的名称
     */
    private String name;

    /**
     * 软删除状态位，1有效，0无效
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
     * 开始本体unique identifier
     */
    private String ontologyUniqueIdentifierFrom;

    /**
     * 结束本体id
     */
    private String ontologyUniqueIdentifierTo;

    /**
     * 开始本体的某个属性，作为连接键
     */
    private String propertyUniqueIdentifierFrom;

    /**
     * 结束本体的某个属性，作为连接键
     */
    private String propertyUniqueIdentifierTo;

    /**
     * 实验状态，1激活、2测试中、3废弃
     */
    private Integer experimentalStatus;

    /**
     * 1: 1对1
     * 2: 1对多
     * 3: 多对1
     * 4: 多对多
     */

    private Integer mapping;

    private int fowardChildLinkId;

    private int backwardChildLinkId;

    private static final long serialVersionUID = 1L;
}
