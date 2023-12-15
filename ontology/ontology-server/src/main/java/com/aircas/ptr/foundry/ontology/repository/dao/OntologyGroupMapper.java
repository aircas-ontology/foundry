package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyGroup;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OntologyGroupMapper {
    int deleteByPrimaryKey(Long id);

    int insert(OntologyGroup record);

    int insertSelective(OntologyGroup record);

    OntologyGroup selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(OntologyGroup record);

    int updateByPrimaryKey(OntologyGroup record);
}