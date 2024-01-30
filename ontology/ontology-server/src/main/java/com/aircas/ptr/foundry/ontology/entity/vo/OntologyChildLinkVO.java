package com.aircas.ptr.foundry.ontology.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;



@Data
public class OntologyChildLinkVO {
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
}
