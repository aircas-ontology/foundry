package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCreateParamV2;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 16:15
 */

public interface OntologyMetaService extends IService<OntologyMeta> {

    String createOntologyV2(OntologyCreateParamV2 ontologyCreateParam);


    String createOntology(OntologyCreateParam ontologyCreateParam);

    List<OntologyGroupMetaVO> getByGroupId(String groupId);

    OntologyMetaInfoVO getMetaByUniqueIdentifier(String uniqueIdentifier);

    void deleteOntology(String uniqueIdentifier);

    void updateMeta(OntologyUpdateParam updateParam);

    List<OntologyMetaInfoVO> searchByKeyword(String keyword);

    List<OntologyMetaNodeVO> getOntologyTreeByByGroupId(String groupId);

    //todo need clean
    List<OntologyMetaVO> listOntologiesByGroup(String groupId);

    List<OntologyMetaVO> selectByUniqueIdentifiers(List<String> ontologyUniqueIdentifiers);

    Integer countByGroup(String groupId);


}
