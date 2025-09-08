package com.aircas.ptr.foundry.ontology.model.vo;

import lombok.Data;

import java.util.List;


@Data
public class ObjectOneInfoVO {

    String primaryKey;

    String displayName;

    List<PropertyValueVO> properties;

}
