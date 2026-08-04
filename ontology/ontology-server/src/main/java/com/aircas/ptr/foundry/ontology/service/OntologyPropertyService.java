package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
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

    List<String> getStorageGroup(String ontologyUniqueIdentifier);

    void notifyBuildPipeline(String ontologyIdentifier);

    void createCategory(PropertyCategoryCreateParam param);

    PropertyCategoryVO getCategory(String ontologyUniqueIdentifier);

    void deleteCategory(PropertyCategoryDeleteParam param);

    void updateCategory(PropertyCategoryUpdateParam param);

    void createMetadataSchema(PropertyMetadataSchemaCreateParam param);

    void updateMetadataSchema(PropertyMetadataSchemaUpdateParam param);

    void deleteMetadataSchema(PropertyMetadataSchemaDeleteParam param);

    JsonNode getMetadataSchema(String ontologyUniqueIdentifier);

    PropertyMetadataSchemaVO getMetadataSchemaTree(String ontologyUniqueIdentifier);
}
