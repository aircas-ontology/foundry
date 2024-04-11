package com.aircas.ptr.foundry.ontology.function;

import com.aircas.ptr.foundry.ontology.GroovyClassLoaderManager;
import groovy.lang.GroovyObject;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionUtils {

    //得到函数参数名称
    public static List getAnnotatedMethodParameterNames(Method method) {
        return null;
    }

    public static Method getGroovyMethod(GroovyObject groovyObject, String methodName) {
        return null;
    }


    public static GroovyObject getFunctionProxyInstance() throws IllegalAccessException, InstantiationException {
        List<Class> functionProxyClasses = Arrays.asList(
                GroovyClassLoaderManager.getParentClassLoader().getLoadedClasses()
        )
                .stream()
                .filter((Class loadedClass) -> loadedClass.getSimpleName().contains("FunctionProxy"))
                .collect(Collectors.toList());
        if (functionProxyClasses.size() != 1) {
            return null;
        }
        Class functionProxyClass = functionProxyClasses.get(0);
        return (GroovyObject) functionProxyClass.newInstance();
    }

}
