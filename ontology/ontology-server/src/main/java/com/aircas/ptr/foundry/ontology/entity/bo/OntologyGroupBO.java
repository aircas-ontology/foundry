package com.aircas.ptr.foundry.ontology.entity.bo;

import lombok.Data;

import java.util.Date;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:14
 */

@Data
public class OntologyGroupBO {
     /**
      * 主键自增
      */
     private Long id;


     /**
      * 本体分组名称
      */
     private String groupName;

}
