package com.aircas.ptr.foundry.ontology.entity.bo;

import lombok.Data;

import java.util.Date;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:15
 */

@Data
public class OntologyLinkBO {
     /**
      * 主键自增
      */
     private Long id;

     /**
      * 开始本体id
      */
     private Long ontologyIdFrom;

     /**
      * 结束本体id
      */
     private Long ontologyIdTo;

     /**
      * 开始本体的某个属性，作为连接键
      */
     private Long propertyIdFrom;

     /**
      * 结束本体的某个属性，作为连接键
      */
     private Long propertyIdTo;

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
