package com.aircas.ptr.foundry.ontology.model.vo;


import lombok.Data;

import java.util.List;

@Data
public class ObjectWithLinkedInfoVO {

    ObjectOneInfoVO objectValue;

    List<LinkedValueVo> links;
}
