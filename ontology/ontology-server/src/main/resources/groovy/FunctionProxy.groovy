package com.aircas.ptr.foundry.ontology.function

class FunctionProxy {

    static def invoke(HashMap map) {
        Object instance = map.get("instance")
        HashMap parameters = map.get("parameters")
        String primaryKey = parameters.get("primaryKey")
        def params = [primaryKey, "hello"]
        instance.handle(*params)
    }
}
