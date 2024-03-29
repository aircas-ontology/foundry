package com.aircas.ptr.foundry.ontology.function

import com.aircas.ptr.foundry.ontology.application.service.ObjectService
import  com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.entity.vo.ObjectValueVo
import com.aircas.ptr.foundry.ontology.entity.vo.PropertyValueVO;


class Ontology {


    static getObjectByOntologyUniqueIdentifier(identifier, primaryKey) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        return objectService.queryObjectByPrimaryKey(identifier, primaryKey)
    }

    static getObject(api, primaryKey) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        return objectService.queryObjectByApiAndPrimaryKey(api, primaryKey)
    }

    static getProperty(ObjectValueVo object, propertyAPI) {
        List<PropertyValueVO> properties = object.properties;
        PropertyValueVO propertyValueVO = properties.find { a -> a.getApiName() == propertyAPI};
        if (propertyValueVO == null) {
            return null;
        }
        String value = propertyValueVO.getValue();
        return value;
    }
}
