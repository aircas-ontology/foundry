package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyGroupBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyGroupVO;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:02
 */


public interface OntologyGroupService {
    Integer add(OntologyGroupBO ontologyGroupBO);

    Integer delete(Long id);

    Integer update(OntologyGroupBO ontologyGroupBO);

    OntologyGroupVO getOntologyGroupById(Long id);
}
