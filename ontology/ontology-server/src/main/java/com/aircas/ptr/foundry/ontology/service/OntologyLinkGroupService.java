package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkDirectionEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:03
 */


public interface OntologyLinkGroupService extends IService<OntologyLinkGroup> {

    void createLink(OntologyLinkCreateParam linkCreateParam);

    List<OntologyMetaInfoVO> getLinkedOntology(String ontologyUniqueIdentifier);

    OntologyLinkInfoVO getLinkByUniqueIdentifier(String uniqueIdentifier);

    List<OntologyLinkInfoVO> getLinksByGroupId(String groupId);

    List<OntologyLinkInfoVO> getLinksByOntologyUniqueIdentifier(String ontologyUniqueIdentifier, OntologyLinkDirectionEnum direction);

    void deleteLinkByLinkUniqueIdentifier(String linkUniqIdentifier);

}
