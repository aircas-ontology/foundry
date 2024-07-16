package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
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

     Integer add(OntologyMetaBO ontologyMetaBO);

     Integer delete(String uniqueIdentifier);

     Integer update(OntologyMetaBO ontologyMetaBO);

     List<OntologyMetaVO> getAllOntologies();

     /**
      * 统计本体数量
      * @return
      */
     Integer getCountByStatus(int status);
}
