package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyAction;

import java.util.List;

public interface OntologyActionMapper {
    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insert(OntologyAction record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insertSelective(OntologyAction record);


    /**
     * @mbg.generated generated automatically, do not modify!
     */
    OntologyAction selectByApi(String api, boolean isPreview);

    List<OntologyAction> selectByOntologyIdentifier(String ontologyUniqueIdentifier);

}