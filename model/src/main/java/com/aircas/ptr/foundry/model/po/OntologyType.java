package com.aircas.ptr.foundry.model.po;

import org.apache.groovy.util.Maps;

import java.util.HashMap;
import java.util.Map;

/**
 * 目前本体属性类型的string
 *
 */

public enum OntologyType {
    STRING("String"),
    INT("int"),
    MAP("map");

    private final String value;

    OntologyType(String value) {
        this.value = value;
    }

    //将数据库的数据类型转化为Java类型
    //
    static OntologyType getClassFromDB(String dbType) {
        return OntologyType.STRING;
    }

    public static OntologyType valueOf(Class class1) {
        return (OntologyType)classToType.get(class1);
    }

    private static Map typeToClass = Maps.of(
            OntologyType.STRING, String.class,
            OntologyType.INT, Integer.class,
            OntologyType.MAP, HashMap.class
    );

    private static Map classToType = Maps.of(
            String.class,  OntologyType.STRING,
            Integer.class, OntologyType.INT,
            HashMap.class, OntologyType.MAP
    );

    public Class getClassFromType(OntologyType type) {
       return (Class) typeToClass.get(type);
    }
}


//有几种方式是需要此类：
// OntologyType是中间类型
/**
 *
 * 1. 函数的描述，生成owl时，入参，出参，需要描述， 需要class 转为owl标准格式的字符串
 *       需要 Class -> owl需要type
 * 2. 函数evaluate时，返回参数名字和类型，类型给前端转化数据类型
 *      需要Class -> OntologyType
 * 3. 本体的属性的类型，应该保存在OntologyMeta的数据表中，该类型返回给前端/SDK时，可以转化
 *     需要OntologyType -> Class, OntologyType
 *
 *
 */

