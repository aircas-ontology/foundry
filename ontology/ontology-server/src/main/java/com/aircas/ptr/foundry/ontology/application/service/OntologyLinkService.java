package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkVO;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:03
 */


public interface OntologyLinkService {
    Integer add(OntologyLinkGroupBo ontologyLinkGroupBo);

    Integer delete(List<Long> ids);

    Integer update(OntologyLinkBO ontologyLinkBO);

    OntologyLinkVO getOntologyLinkById(Long id);
}
