package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParamV2;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface OntologyPropertyService extends IService<OntologyProperty> {

    void batchCreateProperties(List<OntologyPropertyCreateParamV2> propertyCreateParam);

    void deleteProperty(String propertyUniqueIdentifier);

    void createProperty(OntologyPropertyCreateParamV2 propertyCreateParam);

    void updateProperty(OntologyPropertyUpdateParam propertyUpdateParam);

    List<OntologyPropertyDetailVO> getPropertyDetailByOntologyId(String ontologyUniqueIdentifier);

    List<OntologyPropertyInfoVO> getPropertyInfoByOntologyId(String ontologyUniqueIdentifier);

}
