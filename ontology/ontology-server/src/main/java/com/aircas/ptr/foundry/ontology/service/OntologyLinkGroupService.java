package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:03
 */


public interface OntologyLinkGroupService  extends IService<OntologyLinkGroup> {

    void createLink(OntologyLinkCreateParam linkCreateParam);

    List<OntologyLinkInfoVO> getLinksByGroupId(String groupId);

    List<OntologyLinkInfoVO> getLinksByOntologyUniqueIdentifier(String ontologyUniqueIdentifier);

    Integer add(OntologyLinkGroupBo ontologyLinkGroupBo);

    RestResult deleteLinkByOntologyUniqueIdentifier(String uniqueIdentifier);

    List<OntologyLinkGroupVO> getAll();

    List<OntologyLinkGroupVO> getLinkByOntologies(List<OntologyMetaVO> metaVOs);

    OntologyLinkGraphVO getLinkGraphByOntologyUniqueIdentifier(String oId);

    List<OntologyLinkGroup> selectByOntologyUniqueIdentifierFrom(String ontologyUniqueIdentifier);

    List<OntologyLinkGroup> selectByOntologyUniqueIdentifierTo(String ontologyUniqueIdentifier);

    OntologyLinkGroup selectByUniqueIdentifier(String uniqueIdentifier);

    int getCountByStatus(int status);
}
