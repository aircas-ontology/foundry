package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaAddParam;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 16:15
 */

public interface OntologyMetaService {

    OntologyMetaVO getOntologyById(Long id);

    OntologyMetaVO getOntologyByApi(String api);

    OntologyMetaInfoVO getMetaByUniqueIdentifier(String uniqueIdentifier);

    OntologyMetaVO add(OntologyMetaAddParam param);

    void deleteOntology(String uniqueIdentifier);

    Integer update(OntologyMetaBO ontologyMetaBO);

    void updateMeta(OntologyUpdateParam updateParam);

    List<OntologyMetaVO> getAllOntologies();

    Integer getCountByStatus(int status);

    List<OntologyMetaInfoVO> searchByKeyword(String keyword);

    List<OntologyMetaVO> listOntologiesByGroup(String groupId);

    List<OntologyMetaVO> selectByUniqueIdentifiers(List<String> ontologyUniqueIdentifiers);

    Integer countByGroup(String groupId);

    String createOntology(OntologyCreateParam ontologyCreateParam);

    List<OntologyGroupMetaVO> getByGroupId(String groupId);
}
