package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyProperty;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyPropertyMapper {

    int deleteByPrimaryKey(Long id);

    int insert(OntologyProperty record);

    int insertSelective(OntologyProperty record);

    OntologyProperty selectByPrimaryKey(Long id);

    List<OntologyProperty> selectByOntologyUniqueIdentifier(String uniqueIdentifier);

    List<OntologyProperty> selectByUniqueIdentifier(String uniqueIdentifier);

    int updateSelective(OntologyProperty ontologyProperty);

}