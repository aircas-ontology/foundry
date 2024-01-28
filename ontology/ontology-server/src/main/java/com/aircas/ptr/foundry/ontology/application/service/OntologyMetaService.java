package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 16:15
 */

public interface OntologyMetaService {
     OntologyMetaVO getOntologyById(Long id);

     OntologyMetaVO getOntologyByUniqueIdentifier(String uniqueIdentifier);

     Integer add(OntologyMetaBO ontologyMetaBO);

     Integer delete(List<Long> ids);

     Integer update(OntologyMetaBO ontologyMetaBO);
}
