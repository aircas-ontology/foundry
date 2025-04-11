package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyProperty;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyPropertyMapper {

    int deleteByUniqueIdentifier(String uniqueIdentifier);

    int insert(OntologyProperty record);

    int insertSelective(OntologyProperty record);

    OntologyProperty selectByPrimaryKey(Long id);

    OntologyProperty selectByApiName(String ontologyUniqueIdentifier,String apiName);

    List<OntologyProperty> selectByOntologyUniqueIdentifier(String uniqueIdentifier);

    List<OntologyProperty> selectByUniqueIdentifier(String uniqueIdentifier);


    List<OntologyProperty> getAllProperty(int justPrimary);

    int updateSelective(OntologyProperty ontologyProperty);

}