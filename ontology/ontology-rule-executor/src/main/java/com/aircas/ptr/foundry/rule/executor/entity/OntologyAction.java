package com.aircas.ptr.foundry.rule.executor.entity;

import lombok.Data;

import java.util.List;


@Data
public class OntologyAction extends com.aircas.ptr.foundry.model.po.OntologyAction {

    //mapping的列表
    private List<OntologyActionMappingIn> mappingIns;

}
