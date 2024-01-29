package com.aircas.ptr.foundry.model.po;

import lombok.Data;

import java.util.Date;

@Data
public class OntologyChildLink {

    /**
     * 主键自增
     */
    private Long id;

    /**
     * 记录创建时间
     */
    private Date createTime;

    /**
     * 记录修改时间
     */
    private Date updateTime;


    private String displayName;

    /**
     * 可见性，1正常、2隐藏、3突出显示
     */
    private Integer visibility;

    private String apiName;

    private static final long serialVersionUID = 1L;
}


