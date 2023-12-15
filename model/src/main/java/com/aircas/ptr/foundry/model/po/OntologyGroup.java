package com.aircas.ptr.foundry.model.po;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * ontology_group
 * @author 
 */
@Data
public class OntologyGroup implements Serializable {
    /**
     * 主键自增
     */
    private Long id;

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
     * 本体分组名称
     */
    private String groupName;

    private static final long serialVersionUID = 1L;
}