package com.aircas.ptr.foundry.ontology.model.po;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ontology_group
 * @author 
 */
@Data
@TableName("ontology_group")
public class OntologyGroup implements Serializable {
    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
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

    /**
     * 所属组别id
     * @Auther：liuyang
     */
    private String groupId;

    private static final long serialVersionUID = 1L;
}