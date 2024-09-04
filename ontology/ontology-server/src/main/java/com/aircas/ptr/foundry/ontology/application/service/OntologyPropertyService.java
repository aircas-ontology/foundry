package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO;

import java.util.List;

public interface OntologyPropertyService {

    Integer add(OntologyPropertyBO ontologyPropertyBO);

    Integer batchAdd(List<OntologyPropertyBO> ontologyPropertyBO);

    Integer batchUpdate(List<OntologyPropertyBO> ontologyPropertyBO);

    Integer delete(String uniqueIdentifier);

    Integer update(OntologyPropertyBO ontologyPropertyBO);

    List<OntologyPropertyVO> selectByOntologyUniqueIdentifier(String uniqueIdentifier);

    List<OntologyPropertyVO> getAllProperty(int justPrimary);

    OntologyPropertyVO selectByUniqueIdentifier(String uniqueIdentifier);

    List<OntologyPropertyVO> selectByOntologyApi(String api);
}
