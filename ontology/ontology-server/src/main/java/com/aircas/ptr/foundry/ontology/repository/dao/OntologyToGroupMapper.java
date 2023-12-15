package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyToGroup;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OntologyToGroupMapper {
    int deleteByPrimaryKey(Long id);

    int insert(OntologyToGroup record);

    int insertSelective(OntologyToGroup record);

    OntologyToGroup selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(OntologyToGroup record);

    int updateByPrimaryKey(OntologyToGroup record);
}