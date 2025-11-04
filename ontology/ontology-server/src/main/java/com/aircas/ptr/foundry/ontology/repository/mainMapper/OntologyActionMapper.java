package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.view.OntologyActionView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OntologyActionMapper extends BaseMapper<OntologyAction> {

    OntologyAction selectByApi(String api);

    List<OntologyAction> selectByOntologyIdentifier(String ontologyUniqueIdentifier);

    List<OntologyActionView> selectActionViewByOntologyIdentifier(String ontologyUniqueIdentifier);

}