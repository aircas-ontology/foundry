package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyMetaMapper {
    int deleteByPrimaryKey(Long id);

    int insert(OntologyMeta record);

    int insertSelective(OntologyMeta record);

    OntologyMeta selectByPrimaryKey(Long id);

    OntologyMeta selectByUniqueIdentifier(String uniqueIdentifier);

    List<OntologyMeta> selectByUniqueIdentifiers(List<String> list);

    OntologyMeta selectByApi(String api);

    int updateByPrimaryKeySelective(OntologyMeta record);

    int updateByPrimaryKey(OntologyMeta record);

    int selectByDisplayName(String displayName);

    int deleteByUniqueIdentifier(String uniqueIdentifier);

    List<OntologyMeta> selectAllOntologies();
}