package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 目前本体属性类型的string
 * 有几种方式是需要此类：
 * OntologyType是中间类型
 * <p>
 * 1. 函数的描述，生成owl时，入参，出参，需要描述， 需要class 转为owl标准格式的字符串
 * 需要 Class -> owl需要type
 * 2. 函数evaluate时，返回参数名字和类型，类型给前端转化数据类型
 * 需要Class -> OntologyType
 * 3. 本体的属性的类型，应该保存在OntologyMeta的数据表中，该类型返回给前端/SDK时，可以转化
 * 需要OntologyType -> Class, OntologyType
 *
 * @author Lenovo
 */
@Getter
@AllArgsConstructor
public enum OntologyDataTypeEnum {

    Bool("Boolean"),
    Int("Integer"),
    Long("Long"),
    Float("Float"),
    Short("Short"),
    Byte("Byte"),
    Double("Double"),
    Decimal("Decimal"),
    String("String"),
    Date("Date"),
    Array("Array"),
    Map("Map"),
    Vector("Vector"),
    Timestamp("Timestamp"),
    MediaReference("MediaReference"),
    TimeSeries("TimeSeries"),
    Attachment("Attachment"),
    Geohash("Geohash"),
    Geoshape("Geoshape"),
    Cipher("Cipher"),
    Ontology("Ontology"),
    ;

    private static final Map<Class<?>, OntologyDataTypeEnum> CLASS_2_TYPE = new HashMap<Class<?>, OntologyDataTypeEnum>() {{
        put(Boolean.class, OntologyDataTypeEnum.Bool);
        put(Integer.class, OntologyDataTypeEnum.Int);
        put(Long.class, OntologyDataTypeEnum.Long);
        put(Float.class, OntologyDataTypeEnum.Float);
        put(Double.class, OntologyDataTypeEnum.Double);
        put(String.class, OntologyDataTypeEnum.String);
        put(HashMap.class, OntologyDataTypeEnum.Map);
        put(Date.class, OntologyDataTypeEnum.Date);
        put(Timestamp.class, OntologyDataTypeEnum.Timestamp);
        put(Time.class, OntologyDataTypeEnum.Timestamp);
    }};

    private static final Map<String, OntologyDataTypeEnum> PG_2_TYPE = new HashMap<String, OntologyDataTypeEnum>() {{
        put(PostgresDataTypeEnum.Boolean.getValue(), OntologyDataTypeEnum.Bool);
        put(PostgresDataTypeEnum.Smallint.getValue(), OntologyDataTypeEnum.Int);
        put(PostgresDataTypeEnum.Integer.getValue(), OntologyDataTypeEnum.Int);
        put(PostgresDataTypeEnum.Bigint.getValue(), OntologyDataTypeEnum.Long);
        put(PostgresDataTypeEnum.Real.getValue(), OntologyDataTypeEnum.Float);
        put(PostgresDataTypeEnum.Double.getValue(), OntologyDataTypeEnum.Double);
        put(PostgresDataTypeEnum.Decimal.getValue(), OntologyDataTypeEnum.Decimal);
        put(PostgresDataTypeEnum.Numeric.getValue(), OntologyDataTypeEnum.Decimal);
        put(PostgresDataTypeEnum.Text.getValue(), OntologyDataTypeEnum.String);
        put(PostgresDataTypeEnum.Varchar.getValue(), OntologyDataTypeEnum.String);
        put(PostgresDataTypeEnum.Date.getValue(), OntologyDataTypeEnum.Date);
        put(PostgresDataTypeEnum.Time.getValue(), OntologyDataTypeEnum.Timestamp);
        put(PostgresDataTypeEnum.Timestamp.getValue(), OntologyDataTypeEnum.Timestamp);
        put(PostgresDataTypeEnum.Bytea.getValue(), OntologyDataTypeEnum.Byte);
    }};

    private static final Map<OntologyDataTypeEnum, String> TYPE_2_PG = new HashMap<OntologyDataTypeEnum, String>() {{
        put(OntologyDataTypeEnum.Bool, "bool");
        put(OntologyDataTypeEnum.Int, "int4");
        put(OntologyDataTypeEnum.Long, "int8");
        put(OntologyDataTypeEnum.Float, "float4");
        put(OntologyDataTypeEnum.Double, "float8");
        put(OntologyDataTypeEnum.Decimal, "numeric(255)");
        put(OntologyDataTypeEnum.String, "text");
        put(OntologyDataTypeEnum.Date, "date");
        put(OntologyDataTypeEnum.Timestamp, "timestamp(6)");
    }};

    private static final Map<OntologyDataTypeEnum, OWL2Datatype> TYPE_2_OWL_DATA = new HashMap<OntologyDataTypeEnum, OWL2Datatype>() {{
        put(OntologyDataTypeEnum.Bool, OWL2Datatype.XSD_BOOLEAN);
        put(OntologyDataTypeEnum.Int, OWL2Datatype.XSD_INT);
        put(OntologyDataTypeEnum.Long, OWL2Datatype.XSD_LONG);
        put(OntologyDataTypeEnum.Float, OWL2Datatype.XSD_FLOAT);
        put(OntologyDataTypeEnum.Double, OWL2Datatype.XSD_DOUBLE);
        put(OntologyDataTypeEnum.Decimal, OWL2Datatype.XSD_DECIMAL);
        put(OntologyDataTypeEnum.String, OWL2Datatype.XSD_STRING);
    }};


    private static final Map<FunctionParamTypeEnum, OntologyDataTypeEnum> FUNCTION_PARAM_TO_ONTOLOGY_DATA = new HashMap<FunctionParamTypeEnum, OntologyDataTypeEnum>() {{
        put(FunctionParamTypeEnum.STRING, OntologyDataTypeEnum.String);
        put(FunctionParamTypeEnum.INTEGER, OntologyDataTypeEnum.Int);
        put(FunctionParamTypeEnum.BOOL, OntologyDataTypeEnum.Bool);
        put(FunctionParamTypeEnum.DOUBLE, OntologyDataTypeEnum.Double);
        put(FunctionParamTypeEnum.FLOAT, OntologyDataTypeEnum.Float);
        put(FunctionParamTypeEnum.LONG, OntologyDataTypeEnum.Long);
        put(FunctionParamTypeEnum.List, OntologyDataTypeEnum.Array);

    }};

    private final String value;
    @Setter
    private String ontologyApi;

    OntologyDataTypeEnum(String value) {
        this.value = value;
    }

    public Boolean isOntologyDataType() {

        return Objects.equals(this.value, Ontology.value);
    }

    /**
     * 根据Class类型获取对应的OntologyDataTypeEnum枚举
     *
     * @param aClass Class类型
     * @return 对应的OntologyDataTypeEnum枚举
     */
    public static OntologyDataTypeEnum valueOfClass(Class<?> aClass) {
        return CLASS_2_TYPE.get(aClass);
    }

    /**
     * 根据PostgreSQL数据类型字符串获取对应的OntologyDataTypeEnum枚举
     *
     * @param pgType PostgreSQL数据类型字符串
     * @return 对应的OntologyDataTypeEnum枚举
     */
    public static OntologyDataTypeEnum valueOfPg(String pgType) {
        OntologyDataTypeEnum type = PG_2_TYPE.get(pgType);
        assert (type != null);
        return type;
    }


    public static OntologyDataTypeEnum valueOfDataType(FunctionParamTypeEnum paramType) {
        return FUNCTION_PARAM_TO_ONTOLOGY_DATA.get(paramType);

    }

    /**
     * 根据OntologyDataTypeEnum枚举转换为PostgreSQL数据类型字符串
     *
     * @return PostgreSQL数据类型字符串
     */
    public String transfer2Pg() {
        String pgType = TYPE_2_PG.get(this);
        assert (pgType != null);
        return pgType;
    }

    /**
     * 将当前OntologyDataTypeEnum枚举转换为OWL2Datatype类型
     *
     * @return 对应的OWL2Datatype类型
     */
    public OWL2Datatype transfer2Owl() {
        OWL2Datatype type = TYPE_2_OWL_DATA.get(this);
        assert (type != null);
        return type;
    }

    public static Object convert(OntologyDataTypeEnum type, Object value) {
        switch (type) {
            case Int:
                return Integer.parseInt(value.toString());
            case Long:
                return java.lang.Long.parseLong(value.toString());
            case Float:
                return java.lang.Float.parseFloat(value.toString());
            case Double:
                return java.lang.Double.parseDouble(value.toString());
            case String:
                return value.toString();
            default:
                return value;
        }
    }


}




