package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 16:15
 */

public interface OntologyMetaService {
     OntologyMetaVO getOntologyById(Long id);

     Integer add(OntologyMetaBO ontologyMetaBO);

     Integer delete(Integer id);

     Integer update(OntologyMetaBO ontologyMetaBO);
}
