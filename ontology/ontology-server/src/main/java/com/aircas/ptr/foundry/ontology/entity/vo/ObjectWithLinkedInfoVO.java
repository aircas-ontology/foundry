package com.aircas.ptr.foundry.ontology.entity.vo;


import lombok.Data;

import java.util.List;

@Data
public class ObjectWithLinkedInfoVO {

    ObjectValueVo objectValue;

    List<LinkedValueVo> links;
}
