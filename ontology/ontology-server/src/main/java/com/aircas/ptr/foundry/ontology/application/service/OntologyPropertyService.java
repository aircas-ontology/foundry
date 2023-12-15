package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO;

import java.util.List;

public interface OntologyPropertyService {

    Integer add(OntologyPropertyBO ontologyPropertyBO);

    Integer delete(Long id);

    Integer update(OntologyPropertyBO ontologyPropertyBO);

    List<OntologyPropertyVO> selectByOntologyId(Long id);

    OntologyPropertyVO selectById(Long id);
}
