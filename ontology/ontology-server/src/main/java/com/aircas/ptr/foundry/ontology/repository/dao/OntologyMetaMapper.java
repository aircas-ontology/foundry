package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyMetaMapper extends BaseMapper<OntologyMeta> {
    int deleteByPrimaryKey(Long id);

    //int insert(OntologyMeta record);

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

    /**
     * 根据status统计本体数量
     * @param status
     * @return
     */
    int getCountByStatus(Integer status);

    List<OntologyMeta> searchByKeyword(String keyword);

    List<OntologyMeta> listOntologiesByGroup(String groupId);

    int sumByGroup(String groupId);
}