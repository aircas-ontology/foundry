package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyActionMappingIn;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyActionMappingInMapper extends tk.mybatis.mapper.common.Mapper<OntologyActionMappingIn> {

    List<OntologyActionMappingIn> selectByOntologyFunctionId(Long ontologyFunctionId);

    List<OntologyActionMappingIn> selectByOntologyFunctionIds(List<Long> ontologyFunctionIds);
}