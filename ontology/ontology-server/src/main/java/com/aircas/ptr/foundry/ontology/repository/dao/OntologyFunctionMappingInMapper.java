package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyFunctionMappingIn;

public interface OntologyFunctionMappingInMapper {
    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int deleteByPrimaryKey(Long id);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insert(OntologyFunctionMappingIn record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insertSelective(OntologyFunctionMappingIn record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    OntologyFunctionMappingIn selectByPrimaryKey(Long id);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int updateByPrimaryKeySelective(OntologyFunctionMappingIn record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int updateByPrimaryKey(OntologyFunctionMappingIn record);
}