package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyAction;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyActionMapper extends tk.mybatis.mapper.common.Mapper<OntologyAction> {

    OntologyAction selectByApi(String api, boolean isPreview);

    List<OntologyAction> selectByOntologyIdentifier(String ontologyUniqueIdentifier);

}