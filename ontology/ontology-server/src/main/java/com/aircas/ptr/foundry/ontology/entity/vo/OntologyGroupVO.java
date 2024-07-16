package com.aircas.ptr.foundry.ontology.entity.vo;

import lombok.Data;

import java.util.Date;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:16
 */

@Data
public class OntologyGroupVO {
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

     /**
      * 所属组别id
      * @Auther：liuyang
      */
     private String groupId;
}
