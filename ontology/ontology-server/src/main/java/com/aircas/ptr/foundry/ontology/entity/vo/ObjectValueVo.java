package com.aircas.ptr.foundry.ontology.entity.vo;

import lombok.Data;

import java.util.List;


@Data
public class ObjectValueVo {

    String primaryKey;

    String displayName;

    List<PropertyValueVO> properties;

}
