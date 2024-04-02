package com.aircas.ptr.foundry.ontology;

import groovy.lang.GroovyClassLoader;

public class GroovyClassLoaderManager {

    private static GroovyClassLoader parentClassloader = new GroovyClassLoader();

    public static GroovyClassLoader getParentClassLoader() {
        return parentClassloader;
    }

    public static GroovyClassLoader getIndependentClassLoader() {
        return new GroovyClassLoader(parentClassloader);
    }
}

