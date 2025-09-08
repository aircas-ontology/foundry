package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.model.request.OntologyMetaAddParam;
import com.github.pagehelper.PageInfo;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 16:15
 */

public interface OntologyMetaService {

    OntologyMetaVO getOntologyById(Long id);

    OntologyMetaVO getOntologyByApi(String api);

    OntologyMetaVO getOntologyByUniqueIdentifier(String uniqueIdentifier);

    OntologyMetaVO add(OntologyMetaAddParam param);

    Integer delete(String uniqueIdentifier);

    Integer update(OntologyMetaBO ontologyMetaBO);


    List<OntologyMetaVO> getAllOntologies();

    Integer getCountByStatus(int status);

    List<OntologyMetaVO> searchOntologies(String keyword);

    PageInfo<OntologyGroupMetaVO> searchGroupOntologies(String keyword, Integer page, Integer size);

    List<OntologyMetaVO> listOntologiesByGroup(String groupId);

    List<OntologyMetaVO> selectByUniqueIdentifiers(List<String> ontologyUniqueIdentifiers);

    Integer countByGroup(String groupId);
}
