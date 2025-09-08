package com.aircas.ptr.foundry.ontology.function


import com.aircas.ptr.foundry.ontology.service.ObjectService
import com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService
import com.aircas.ptr.foundry.ontology.model.vo.LinkedValueVo
import com.aircas.ptr.foundry.ontology.model.vo.ObjectOneInfoVO
import com.aircas.ptr.foundry.ontology.model.vo.ObjectWithLinkedInfoVO
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaVO
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVO
import com.aircas.ptr.foundry.ontology.model.vo.PropertyValueVO

class Ontology {

    static getObjectByOntologyUniqueIdentifier(identifier, primaryKey) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        return objectService.queryObjectByPrimaryKey(identifier, primaryKey)
    }

    static getObject(api, primaryKey) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        return objectService.queryObjectByApiAndPrimaryKey(api, primaryKey)
    }

    static getProperty(ObjectOneInfoVO object, String propertyApi) {
        if (propertyApi == null || propertyApi.length() == 0) {
            return null;
        }
        List<PropertyValueVO> properties = object.properties;
        PropertyValueVO propertyValueVO = properties.find { a -> a.getApiName() == propertyApi };
        if (propertyValueVO == null) {
            return null;
        }
        String value = propertyValueVO.getValue();
        return value;
    }

    static getPropertyList(String api) {
        OntologyMetaService ontologyMetaService = OntologyServerApplication.context.getBean(OntologyMetaService.class)
        OntologyMetaVO metaVO = ontologyMetaService.getOntologyByApi(api)
        OntologyPropertyService ontologyPropertyService = OntologyServerApplication.context.getBean(OntologyPropertyService.class)
        List<OntologyPropertyVO> propertyList = ontologyPropertyService.selectByOntologyUniqueIdentifier(metaVO.getUniqueIdentifier())
        return propertyList
    }

    static getTitle(ObjectOneInfoVO object) {
        List<PropertyValueVO> properties = object.properties;
        PropertyValueVO propertyValueVO = properties.find { a -> a.getIsTitleKey() == 1 };
        if (propertyValueVO == null) {
            return null;
        }
        String value = propertyValueVO.getValue();
        return value;
    }

    static getLinkedObjects(api, primaryKey, linkApi) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        ObjectWithLinkedInfoVO objectWithLinkedInfo = objectService.queryObjectWithLinkedInfoByApiAndPrimaryKey(api, primaryKey)
        LinkedValueVo linkedValueVo = objectWithLinkedInfo.links.find { a -> a.getApiName() == linkApi };
        return linkedValueVo.getJoinedResults();
    }
}
