package com.aircas.ptr.foundry.ontology.function

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyBaseObjectBo
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionBo
import com.github.jsonldjava.utils.Obj

import java.lang.reflect.Method

class FunctionProxy {

    def invoke(HashMap map) {
        System.out.println(map)
        HashMap parameters = map.get("parameters")
        List<String> parameterNames = map.get("parameterNames")
        GroovyObject instance = map.get("instance")
        Method method = map.get("method")
        Class[] parameterTypes = method.parameterTypes
        def params = []
        for(int i = 0; i < parameterTypes.length; i ++) {
            Class parameterType = parameterTypes[i]
            String name = parameterNames[i]
            if (name == null) {
                params.add(null)
            } else {
                def parameterValue = parameters.get(name);
                if (parameterType.getSuperclass().getSimpleName().equals("OntologBaseObject")) {
                    Class aClass = parameterType
                    if (parameterValue instanceof Map) {
                        parameterValue = new OntologyBaseObjectBo(parameterValue.get("api"), parameterValue.get("primaryKey"))
                    }
                    GroovyObject groovyObject = aClass.newInstance(parameterValue.getPrimaryKey())
                    assert (parameterType.getSimpleName().toLowerCase().equals(parameterValue.getApi()))
                    params.add(groovyObject)
                } else {
                    params.add(parameterType.cast(parameterValue))
                }
            }
        }
        return method.invoke(instance, *params)
    }
}
