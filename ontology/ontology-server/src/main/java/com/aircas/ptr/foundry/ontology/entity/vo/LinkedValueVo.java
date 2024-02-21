package com.aircas.ptr.foundry.ontology.entity.vo;

import lombok.Data;

import java.util.List;


@Data
public class LinkedValueVo {

    private String name; // 关系的名字

    //可能查询出很多个object
    private List<ObjectValueVo> joinedResults;
}
