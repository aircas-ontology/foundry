package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyVisibilityUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVisibilityVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface OntologyPropertyService extends IService<OntologyProperty> {

    void batchCreateProperties(List<OntologyPropertyCreateParam> propertyCreateParam);

    void deleteProperty(String propertyUniqueIdentifier);

    void createProperty(OntologyPropertyCreateParam propertyCreateParam);

    void updateProperty(OntologyPropertyUpdateParam propertyUpdateParam);

    void batchUpdateProperties(List<OntologyPropertyUpdateParam> params);

    List<OntologyPropertyDetailVO> getPropertyDetailByOntologyId(String ontologyUniqueIdentifier);

    List<OntologyPropertyInfoVO> getPropertyInfoByOntologyId(String ontologyUniqueIdentifier);

    OntologyPropertyDetailVO getPropertyDetailById(String uniqueIdentifier);

    List<OntologyPropertyDetailVO> getPropertiesDetailById(List<String> uniqueIdentifiers);

    List<OntologyPropertyVisibilityVO> getPropertyVisibility(String ontologyUniqueIdentifier);

    void updatePropertyVisibility(OntologyPropertyVisibilityUpdateParam param);

    void autoBindDatasource(String ontologyIdentifier);
}
