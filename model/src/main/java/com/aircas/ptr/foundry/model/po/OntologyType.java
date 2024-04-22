package com.aircas.ptr.foundry.model.po;

import org.apache.groovy.util.Maps;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

import java.util.HashMap;
import java.util.Map;

/**
 * 目前本体属性类型的string
 *
 */

public enum OntologyType {
    Bool("Bool"),

    Int("Int"),
    Float("Float"),
    Double("Double"),
    Decimal("Decimal"),

    String("String"),
    MAP("Map"),

    Date("Date"),
    Time("Time"),
    Timestamp("Timestamp");

    private final String value;

    OntologyType(String value) {
        this.value = value;
    }

    //将数据库的数据类型转化为Java类型
    //

    public static OntologyType valueOf(Class class1) {
        return (OntologyType)classToType.get(class1);
    }

    private static Map classToType = Maps.of(
            Boolean.class, OntologyType.Bool,

            Integer.class, OntologyType.Int,
            Float.class, OntologyType.Float,
            Double.class, OntologyType.Double,

            String.class,  OntologyType.String,
            HashMap.class, OntologyType.MAP
            /**
             * Date, time, timestamp未定
             */
    );

    private static Map pgTypeMap = Maps.of(
            "boolean", OntologyType.Bool,

            "smallint", OntologyType.Int,
            "integer", OntologyType.Int,
            "bigint", OntologyType.Int,

            "real", OntologyType.Float,
            "double precision", OntologyType.Double,
            "numeric", OntologyType.Decimal,

            "text", OntologyType.String,
            "character varying", OntologyType.String,

            "date", OntologyType.Date,
            "time without time zone", OntologyType.Time,
            "timestamp without time zone", OntologyType.Timestamp
    );

    private static Map toOwlDataType = Maps.of(
        OntologyType.Bool, OWL2Datatype.XSD_BOOLEAN,
        OntologyType.Int, OWL2Datatype.XSD_INT,
        OntologyType.Float, OWL2Datatype.XSD_FLOAT,
        OntologyType.Double, OWL2Datatype.XSD_DOUBLE,
        OntologyType.Decimal, OWL2Datatype.XSD_DECIMAL,

        OntologyType.String, OWL2Datatype.XSD_STRING
 );

    public static OntologyType valueFromPgType(String pgType) {
        OntologyType type = (OntologyType) pgTypeMap.get(pgType);
        assert (type == null);
        return type;
    }

    public OWL2Datatype owl2Datatype() {
        OWL2Datatype type = (OWL2Datatype) toOwlDataType.get(this);
        assert (type != null);
        return type;
    }

//
//    private static Map typeToClass = Maps.of(
//            OntologyType.Bool, String.class,
//            OntologyType.Int, Integer.class,
//
//            OntologyType.String, String.class,
//            OntologyType.MAP, HashMap.class
//    );
//
//    public Class getClassFromType(OntologyType type) {
//        return (Class) typeToClass.get(type);
//    }

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

