package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyFunctionMappingOut;

public interface OntologyFunctionMappingOutMapper {
    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int deleteByPrimaryKey(Long id);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insert(OntologyFunctionMappingOut record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insertSelective(OntologyFunctionMappingOut record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    OntologyFunctionMappingOut selectByPrimaryKey(Long id);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int updateByPrimaryKeySelective(OntologyFunctionMappingOut record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int updateByPrimaryKey(OntologyFunctionMappingOut record);
}