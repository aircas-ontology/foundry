package com.aircas.ptr.foundry.ontology.function

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
                params.add(parameterType.cast(parameters.get(name)))
            }
        }
        method.invoke(instance, *params)
    }
}
