package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkGroupVO;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:03
 */


public interface OntologyLinkService {
    Integer add(OntologyLinkGroupBo ontologyLinkGroupBo);

    List<OntologyLinkGroupVO> getLinkByOntologyUniqueIdentifier(String uniqueIdentifier);

    RestResult deleteLinkByOntologyUniqueIdentifier(String uniqueIdentifier);

    List<OntologyLinkGroupVO> getAll();
}
