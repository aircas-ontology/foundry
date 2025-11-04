package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.OntologyGroup;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyGroupMapper extends BaseMapper<OntologyGroup> {
    int deleteByPrimaryKey(Long id);

    int insert(OntologyGroup record);

    int insertSelective(OntologyGroup record);

    OntologyGroup selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(OntologyGroup record);

    int updateByPrimaryKey(OntologyGroup record);

    int selectByGroupName(String groupName);

    int deleteByIds(List<Long> ids);

    List<OntologyGroup> selectAll();

    List<OntologyGroup> searchByKeyword(String keyword);
}