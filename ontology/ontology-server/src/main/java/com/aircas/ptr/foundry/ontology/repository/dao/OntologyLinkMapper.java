package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyLink;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OntologyLinkMapper {
    int deleteByPrimaryKey(Long id);

    int insert(OntologyLink record);

    int insertSelective(OntologyLink record);

    OntologyLink selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(OntologyLink record);

    int updateByPrimaryKey(OntologyLink record);
}