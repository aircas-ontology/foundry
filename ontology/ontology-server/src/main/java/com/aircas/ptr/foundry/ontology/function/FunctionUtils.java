package com.aircas.ptr.foundry.ontology.function;

import com.aircas.ptr.foundry.model.po.OntologyDataType;
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
    public static List<Parameter> getMethodParameterAnnotates(Method method) {
        Annotation[][] annotations = method.getParameterAnnotations();
        List<Parameter> parameters = new ArrayList<>();
        for (Annotation[] annotation: annotations) {
            if (annotation.length == 0) {
                parameters.add(null);
            } else {
                parameters.add(((Parameter)annotation[0]));
            }
        }
        return parameters;
    }

    public static List<OntologyDataType> getParameterTypes(Method method) {
        Class[] parameterTypes = method.getParameterTypes();
        List<OntologyDataType> paramTypes = new ArrayList<>();
        for(int i = 0; i < parameterTypes.length; i ++) {
            Class parameterClass = parameterTypes[i];
            //如果是本体，则参数为本体类型，且api从class获得。
            //OntologBaseObject 为动态生成，因而无法获取其class， 写死了字符串
            if (parameterClass.getSuperclass().getSimpleName().equals("OntologBaseObject")) {
                OntologyDataType type = OntologyDataType.Ontology;
                type.setOntologyApi(parameterClass.getSimpleName().toLowerCase());
                paramTypes.add(type);
            } else {
                OntologyDataType type = OntologyDataType.valueOf(parameterClass);
                paramTypes.add(type);
            }
        }
        return paramTypes;
    }


    public static Method getMethod(GroovyObject groovyObject, String methodName) {
        List<Method> matchedMethods = Arrays.asList(groovyObject.getClass().getMethods())
                .stream()
                .filter((Method method) -> method.getName().equals(methodName))
                .collect(Collectors.toList());
        if (matchedMethods.size() == 0) {return null;}
        return matchedMethods.get(0);
    }


    public static GroovyObject getFunctionProxyInstance() {
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
        try {
            return (GroovyObject) functionProxyClass.newInstance();
        }  catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }
}
