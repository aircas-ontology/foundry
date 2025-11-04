package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.OntologyActionMappingIn;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyActionMappingInMapper extends BaseMapper<OntologyActionMappingIn> {

    List<OntologyActionMappingIn> selectById(Long actionId);

    List<OntologyActionMappingIn> selectByIds(List<Long> actionIds);
}