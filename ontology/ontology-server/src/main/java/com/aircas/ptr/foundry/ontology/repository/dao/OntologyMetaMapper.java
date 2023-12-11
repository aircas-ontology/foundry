package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyMeta;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OntologyMetaMapper {
    int deleteByPrimaryKey(Long id);

    int insert(OntologyMeta record);

    int insertSelective(OntologyMeta record);

    OntologyMeta selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(OntologyMeta record);

    int updateByPrimaryKey(OntologyMeta record);
}