package com.aircas.ptr.foundry.ontology.model.vo;

import lombok.Data;

import java.util.Date;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:16
 */

@Data
public class OntologyLinkVO {
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
      * 开始本体id
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
      * 本体间关系的名称
      */
     private String displayName;

     /**
      * 本体间关系的复数名称
      */
     private String pluralDisplayName;

     /**
      * 可见性，1正常、2隐藏、3突出显示
      */
     private Integer visibility;

     /**
      * 实验状态，1激活、2测试中、3废弃
      */
     private Integer experimentalStatus;

     /**
      * 在代码里用的本体名称
      */
     private Integer apiName;

}
