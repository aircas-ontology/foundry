package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkGroupVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:03
 */


public interface OntologyLinkGroupService {
    Integer add(OntologyLinkGroupBo ontologyLinkGroupBo);

    List<OntologyLinkGroupVO> getLinkByOntologyUniqueIdentifier(String uniqueIdentifier);

    RestResult deleteLinkByOntologyUniqueIdentifier(String uniqueIdentifier);

    List<OntologyLinkGroupVO> getAll();

    List<OntologyLinkGroupVO> getLinkByOntologies(List<OntologyMetaVO> metaVOs);

    OntologyLinkGraphVO getLinkGraphByOntologyUniqueIdentifier(String oId);

    List<OntologyLinkGroup> selectByOntologyUniqueIdentifierFrom(String ontologyUniqueIdentifier);

    List<OntologyLinkGroup> selectByOntologyUniqueIdentifierTo(String ontologyUniqueIdentifier);

    OntologyLinkGroup selectByUniqueIdentifier(String uniqueIdentifier);
}
