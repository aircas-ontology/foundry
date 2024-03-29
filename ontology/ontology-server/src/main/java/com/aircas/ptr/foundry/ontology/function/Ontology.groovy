package com.aircas.ptr.foundry.ontology.function

import com.aircas.ptr.foundry.ontology.application.service.ObjectService
import  com.aircas.ptr.foundry.ontology.OntologyServerApplication;


class Ontology {


    static getObject(objectTypeAPI, primaryKey) {
        ObjectService objectService = OntologyServerApplication.context.getBean(ObjectService.class)
        return objectService.queryObjectByPrimaryKey("545649a4-8bba-4d0c-b265-362e95fd4ffb", "7")
    }


}
