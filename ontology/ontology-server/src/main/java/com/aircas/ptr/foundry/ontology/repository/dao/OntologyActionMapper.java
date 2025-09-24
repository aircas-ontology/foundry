package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyAction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyActionMapper extends BaseMapper<OntologyAction> {

    OntologyAction selectByApi(String api);

    List<OntologyAction> selectByOntologyIdentifier(String ontologyUniqueIdentifier);

}