package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyFunction;

import java.util.List;

public interface OntologyFunctionMapper {
    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insert(OntologyFunction record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insertSelective(OntologyFunction record);


    /**
     * @mbg.generated generated automatically, do not modify!
     */
    OntologyFunction selectByOntologyIdentifierAndApi(String ontologyUniqueIdentifier, String api, boolean isPreview);

    List<OntologyFunction> selectByOntologyIdentifier(String ontologyUniqueIdentifier);

}