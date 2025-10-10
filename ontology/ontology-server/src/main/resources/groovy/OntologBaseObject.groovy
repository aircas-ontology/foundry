//package com.aircas.ptr.foundry.ontology.function
//
//import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty
//import com.aircas.ptr.foundry.ontology.model.vo.ObjectOneInfoVO
//
//class OntologBaseObject extends Proxy implements GroovyInterceptable {
//
//    OntologBaseObjectProxy proxy
//
//    OntologBaseObject(String objectTypeApi, String primaryKey) {
//        proxy = new OntologBaseObjectProxy()
//        proxy.objectTypeApi = objectTypeApi
//        proxy.primaryKey = primaryKey
//    }
//
//    def getProperty(String propertyName) {
//        if (proxy.metaClass.hasProperty(propertyName)) {
//            return proxy."$propertyName"
//        } else if (this.proxy.isObjectPropertyName(propertyName)) {
//            return proxy.getPropertyValue(propertyName);
//        } else {
//            throw new MissingPropertyException(propertyName, this.class);
//        }
//    }
//
//    def methodMissing(String name, args) {
//        System.out.println("methodMissing");
//    }
//
//}
//
//class OntologBaseObjectProxy {
//
//    String objectTypeApi
//    String primaryKey   //主键
//
//    private ObjectOneInfoVO objectValueVo;
//
//    def getPropertyValue(String key) {
//        this.fetchIfNeeded();
//        return Ontology.getProperty(objectValueVo, key);
//    }
//
//    def fetchIfNeeded() {
//        if(this.objectValueVo != null) {
//            return
//        }
//        this.objectValueVo = Ontology.getObject(objectTypeApi, primaryKey);
//    }
//
//    def isObjectPropertyName(String propertyName) {
//        List<OntologyProperty> list = Ontology.getPropertyList(objectTypeApi)
//        String property = list.find {property -> property.getApiName() == propertyName}
//        return property != null
//    }
//
//    def isLinkName() {
//        return false;
//    }
//
//    def isFunctionName() {
//        return false;
//    }
//}
