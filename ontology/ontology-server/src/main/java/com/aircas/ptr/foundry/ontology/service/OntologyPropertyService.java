package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface OntologyPropertyService extends IService<OntologyProperty>  {

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
