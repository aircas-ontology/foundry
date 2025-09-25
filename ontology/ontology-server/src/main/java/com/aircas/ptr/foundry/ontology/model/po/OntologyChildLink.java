package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ontology_child_link")
public class OntologyChildLink implements Serializable {

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


