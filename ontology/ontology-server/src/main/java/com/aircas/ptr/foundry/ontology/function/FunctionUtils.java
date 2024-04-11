package com.aircas.ptr.foundry.ontology.function;

import com.aircas.ptr.foundry.ontology.GroovyClassLoaderManager;
import groovy.lang.GroovyObject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionUtils {

    //得到函数参数名称
    public static List getAnnotatedMethodParameterNames(Method method) {
        Annotation[][] annotations = method.getParameterAnnotations();
        List<String> names = new ArrayList<>();
        for (Annotation[] annotation: annotations) {
            if (annotation.length == 0) {
                names.add(null);
            } else {
                names.add(((Parameter)annotation[0]).name());
            }
        }
        return names;
    }

    public static Method getMethod(GroovyObject groovyObject, String methodName) {
        List<Method> matchedMethods = Arrays.asList(groovyObject.getClass().getMethods())
                .stream()
                .filter((Method method) -> method.getName().equals(methodName))
                .collect(Collectors.toList());
        if (matchedMethods.size() == 0) {return null;}
        return matchedMethods.get(0);
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
