package com.aircas.ptr.foundry.ontology.function

import com.aircas.ptr.foundry.model.po.OntologyMeta
import com.aircas.ptr.foundry.ontology.application.service.ObjectService
import  com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService
import com.aircas.ptr.foundry.ontology.application.service.OntologyPropertyService
import com.aircas.ptr.foundry.ontology.application.service.impl.OntologyMetaServiceImpl
import com.aircas.ptr.foundry.ontology.entity.vo.LinkedValueVo
import com.aircas.ptr.foundry.ontology.entity.vo.ObjectValueVo
import com.aircas.ptr.foundry.ontology.entity.vo.ObjectWithLinkedInfoVO
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO
import com.aircas.ptr.foundry.ontology.entity.vo.PropertyValueVO

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target;



@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@interface Parameter{
    String name () default "null"
    String description() default "null"
}

class Ontology {

    static getObjectByOntologyUniqueIdentifier(identifier, primaryKey) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        return objectService.queryObjectByPrimaryKey(identifier, primaryKey)
    }

    static getObject(api, primaryKey) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        return objectService.queryObjectByApiAndPrimaryKey(api, primaryKey)
    }

    static getProperty(ObjectValueVo object, String propertyApi) {
        if (propertyApi == null || propertyApi.length() == 0) {
            return null;
        }
        List<PropertyValueVO> properties = object.properties;
        PropertyValueVO propertyValueVO = properties.find { a -> a.getApiName() == propertyApi};
        if (propertyValueVO == null) {
            return null;
        }
        String value = propertyValueVO.getValue();
        return value;
    }

    static getPropertyList(String api) {
        OntologyMetaService ontologyMetaService = OntologyServerApplication.context.getBean(OntologyMetaService.class)
        OntologyMetaVO metaVO =  ontologyMetaService.getOntologyByApi(api)
        OntologyPropertyService ontologyPropertyService = OntologyServerApplication.context.getBean(OntologyPropertyService.class)
        List<OntologyPropertyVO> propertyList = ontologyPropertyService.selectByOntologyUniqueIdentifier(metaVO.getUniqueIdentifier())
        return propertyList
    }

    static getTitle(ObjectValueVo object) {
        List<PropertyValueVO> properties = object.properties;
        PropertyValueVO propertyValueVO = properties.find { a -> a.getIsTitleKey() == 1};
        if (propertyValueVO == null) {
            return null;
        }
        String value = propertyValueVO.getValue();
        return value;
    }

    static getLinkedObjects(api, primaryKey, linkApi) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        ObjectWithLinkedInfoVO objectWithLinkedInfo = objectService.queryObjectWithLinkedInfoByApiAndPrimaryKey(api, primaryKey)
        LinkedValueVo linkedValueVo = objectWithLinkedInfo.links.find { a -> a.getApiName() == linkApi};
        return linkedValueVo.getJoinedResults();
    }
}
