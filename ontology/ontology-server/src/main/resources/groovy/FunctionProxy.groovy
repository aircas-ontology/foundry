package com.aircas.ptr.foundry.ontology.function

class FunctionProxy {

    GroovyObject instance;

    def invoke(HashMap map) {
        HashMap parameters = map.get("parameter")
        String primaryKey = parameters.get("primaryKey")

        def params = [primaryKey, "hello"]
        instance.handle(*params)
    }
}
