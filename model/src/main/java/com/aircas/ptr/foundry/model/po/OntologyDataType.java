package com.aircas.ptr.foundry.model.po;

import org.apache.groovy.util.Maps;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

import java.util.HashMap;
import java.util.Map;

/**
 * 目前本体属性类型的string
 *
 */

public enum OntologyDataType {
    Bool("Bool"),

    Int("Int"),
    Float("Float"),
    Double("Double"),
    Decimal("Decimal"),

    String("String"),
    MAP("Map"),

    Date("Date"),
    Time("Time"),
    Timestamp("Timestamp"),

    Ontology("Ontology");

    private final String value;

    String ontologyApi;

    OntologyDataType(String value) {
        this.value = value;
    }

    public void setOntologyApi(String ontologyApi) {
        this.ontologyApi = ontologyApi;
    }

    public Boolean isOntologyDataType() {
        return this.value == Ontology.value;
    }

    public String getOntologyApi() {
        return this.ontologyApi;
    }

    public static OntologyDataType valueOf(Class class1) {
        return (OntologyDataType)classToType.get(class1);
    }

    private static Map classToType = Maps.of(
            Boolean.class, OntologyDataType.Bool,

            Integer.class, OntologyDataType.Int,
            Float.class, OntologyDataType.Float,
            Double.class, OntologyDataType.Double,

            String.class,  OntologyDataType.String,
            HashMap.class, OntologyDataType.MAP
            /**
             * Date, time, timestamp未定
             */
    );

    private static Map pgTypeMap = Maps.of(
            "boolean", OntologyDataType.Bool,

            "smallint", OntologyDataType.Int,
            "integer", OntologyDataType.Int,
            "bigint", OntologyDataType.Int,

            "real", OntologyDataType.Float,
            "double precision", OntologyDataType.Double,
            "numeric", OntologyDataType.Decimal,

            "text", OntologyDataType.String,
            "character varying", OntologyDataType.String,

            "date", OntologyDataType.Date,
            "time without time zone", OntologyDataType.Time,
            "timestamp without time zone", OntologyDataType.Timestamp
    );

    private static Map toOwlDataType = Maps.of(
        OntologyDataType.Bool, OWL2Datatype.XSD_BOOLEAN,
        OntologyDataType.Int, OWL2Datatype.XSD_INT,
        OntologyDataType.Float, OWL2Datatype.XSD_FLOAT,
        OntologyDataType.Double, OWL2Datatype.XSD_DOUBLE,
        OntologyDataType.Decimal, OWL2Datatype.XSD_DECIMAL,

        OntologyDataType.String, OWL2Datatype.XSD_STRING
 );

    public static OntologyDataType valueFromPgType(String pgType) {
        OntologyDataType type = (OntologyDataType) pgTypeMap.get(pgType);
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

