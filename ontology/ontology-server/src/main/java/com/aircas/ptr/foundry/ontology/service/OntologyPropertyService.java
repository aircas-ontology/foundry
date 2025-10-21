package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyDataSourceCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyDatasourcePropertyVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface OntologyPropertyService extends IService<OntologyProperty>  {

    void createDatasource(OntologyDataSourceCreateParam dataSourceCreateParam);

    OntologyDatasourcePropertyVO getPropertyDetailByOntologyId(String ontologyUniqueIdentifier);

    List<OntologyPropertyInfoVO> getPropertyInfoByOntologyId(String ontologyUniqueIdentifier);

    Integer add(OntologyPropertyBO ontologyPropertyBO);

    List<OntologyPropertyVO> selectByOntologyUniqueIdentifier(String uniqueIdentifier);

    List<OntologyPropertyVO> selectByOntologyApi(String api);
}
